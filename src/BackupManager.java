import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import javax.swing.*;

public class BackupManager {
    public record BackupRecord(String id, String name, String absolutePath, long createdAt, long size) {
        public boolean exists() { return Files.isRegularFile(Path.of(absolutePath)); }
    }
    private static BackupManager instance;
    private final Properties config = new Properties();
    private javax.swing.Timer automaticTimer;
    private boolean automaticRunning;
    private long retryAfter;

    private BackupManager() {
        Path file = AppConstants.getBackupConfigPath();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { config.load(r); }
            catch (IOException | IllegalArgumentException e) { throw new IllegalStateException("Unable to read backup settings", e); }
        }
    }
    public static synchronized BackupManager getInstance() {
        if (instance == null) instance = new BackupManager();
        return instance;
    }
    private synchronized void saveConfig() {
        Path path = AppConstants.getBackupConfigPath();
        Path temporary = null;
        try {
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), "backup-settings-", ".tmp");
            try (Writer w = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { config.store(w, null); }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) { throw new IllegalStateException("Unable to save backup settings", e); }
        finally { removeTemporary(temporary); }
    }
    private static boolean sameFile(Path left, Path right) throws IOException {
        return left.toAbsolutePath().normalize().equals(right.toAbsolutePath().normalize()) ||
            (Files.exists(left) && Files.exists(right) && Files.isSameFile(left, right));
    }
    private static void rejectActive(Path file) throws IOException {
        if (sameFile(file, AppConstants.getDatabasePath()))
            throw new IllegalArgumentException("The selected file is the active database");
    }
    private static void removeTemporary(Path file) {
        if (file == null) return;
        try { Files.deleteIfExists(file); } catch (IOException ignored) { }
    }
    /** VACUUM INTO reads a transactionally consistent snapshot, including committed WAL pages. */
    private Path snapshot(Path source, Path directory) throws IOException, SQLException {
        if (!Files.isRegularFile(source) || !Files.isReadable(source))
            throw new IOException("Database file not found or not readable");
        Files.createDirectories(directory);
        Path staging = Files.createTempFile(directory, "warestat-snapshot-", ".db");
        Files.delete(staging);
        try {
            org.sqlite.SQLiteConfig settings = new org.sqlite.SQLiteConfig();
            settings.setReadOnly(true);
            settings.setBusyTimeout(10000);
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + source.toAbsolutePath(), settings.toProperties());
                 PreparedStatement p = c.prepareStatement("VACUUM INTO ?")) {
                p.setString(1, staging.toAbsolutePath().toString()); p.execute();
            }
            try (Connection c = DatabaseManager.openConnection("jdbc:sqlite:" + staging)) {
                DatabaseManager.prepareDatabase(c);
            }
            return staging;
        } catch (SQLException | RuntimeException e) {
            removeTemporary(staging);
            throw e;
        }
    }
    private static void replaceAtomically(Path staging, Path destination) throws IOException {
        Files.move(staging, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    public synchronized Path performBackup() {
        Path staging = null;
        try {
            Path folder = Path.of(getBackupDirectory()).toAbsolutePath();
            staging = snapshot(AppConstants.getDatabasePath(), folder);
            Path target = folder.resolve("data_" + System.currentTimeMillis() + "_" + UUID.randomUUID() + ".db");
            replaceAtomically(staging, target);
            registerBackup(target);
            config.setProperty("backup.last_success", Long.toString(System.currentTimeMillis()));
            saveConfig();
            cleanOldBackups();
            return target;
        } catch (IOException | SQLException e) { throw new IllegalStateException("Error during backup: " + e.getMessage(), e); }
        finally { removeTemporary(staging); }
    }
    public synchronized BackupRecord importBackup(Path file) {
        Path staging = null;
        try {
            rejectActive(file);
            Path folder = Path.of(getBackupDirectory()).toAbsolutePath();
            staging = snapshot(file, folder);
            Path target = folder.resolve("imported_" + UUID.randomUUID() + ".db");
            replaceAtomically(staging, target);
            return registerBackup(target);
        } catch (IOException | SQLException e) { throw new IllegalArgumentException("Unable to import backup: " + e.getMessage(), e); }
        finally { removeTemporary(staging); }
    }
    public synchronized Path exportDatabase(Path destination) {
        Path staging = null;
        try {
            Path target = destination.toAbsolutePath().normalize();
            rejectActive(target);
            staging = snapshot(AppConstants.getDatabasePath(), target.getParent());
            replaceAtomically(staging, target);
            return target;
        } catch (IOException | SQLException e) { throw new IllegalStateException("Unable to export database: " + e.getMessage(), e); }
        finally { removeTemporary(staging); }
    }
    public synchronized void importDatabase(Path sourceFile) {
        Path current = AppConstants.getDatabasePath().toAbsolutePath();
        Path staging = null;
        Path safetyCopy = null;
        boolean replaced = false;
        try {
            rejectActive(sourceFile);
            staging = snapshot(sourceFile, current.getParent());
            if (Files.exists(current)) {
                safetyCopy = snapshot(current, current.getParent());
                Path retained = current.resolveSibling("data_before_restore_" + UUID.randomUUID() + ".db");
                replaceAtomically(safetyCopy, retained);
                safetyCopy = retained;
            }
            DatabaseManager.getInstance().closeConnection();
            Files.deleteIfExists(Path.of(current + "-wal"));
            Files.deleteIfExists(Path.of(current + "-shm"));
            replaceAtomically(staging, current);
            replaced = true;
            DatabaseManager.getInstance().initDatabase();
            CompanyData.getInstance().reloadFromDatabase();
        } catch (Exception e) {
            if (replaced && safetyCopy != null) {
                try {
                    DatabaseManager.getInstance().closeConnection();
                    Path rollback = snapshot(safetyCopy, current.getParent());
                    Files.deleteIfExists(Path.of(current + "-wal"));
                    Files.deleteIfExists(Path.of(current + "-shm"));
                    replaceAtomically(rollback, current);
                    DatabaseManager.getInstance().initDatabase();
                    CompanyData.getInstance().reloadFromDatabase();
                } catch (Exception recovery) { e.addSuppressed(recovery); }
            }
            throw new IllegalStateException("Unable to import database: " + e.getMessage() +
                (safetyCopy == null ? "" : ". Previous database retained at " + safetyCopy), e);
        } finally { removeTemporary(staging); }
    }
    public void restoreBackup(String file) { importDatabase(Path.of(file)); }
    private void validateBackup(Path file) {
        if (!Files.isRegularFile(file) || !Files.isReadable(file))
            throw new IllegalArgumentException("Backup file not found or not readable");
        org.sqlite.SQLiteConfig settings = new org.sqlite.SQLiteConfig();
        settings.setReadOnly(true);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath(), settings.toProperties())) {
            DatabaseIntegrity.validate(c, true);
        } catch (SQLException e) { throw new IllegalArgumentException("Invalid WareStat backup: " + e.getMessage(), e); }
    }
    public void validateDatabase(Path file) { validateBackup(file); }
    private synchronized BackupRecord registerBackup(Path file) {
        try {
            rejectActive(file);
            Path normalized = file.toRealPath();
            String id = UUID.randomUUID().toString();
            long created = System.currentTimeMillis(), size = Files.size(normalized);
            String prefix = "backup.record." + id + ".";
            config.setProperty(prefix + "path", normalized.toString());
            config.setProperty(prefix + "name", normalized.getFileName().toString());
            config.setProperty(prefix + "created", Long.toString(created));
            config.setProperty(prefix + "size", Long.toString(size));
            config.setProperty(prefix + "managed", "true");
            saveConfig();
            return new BackupRecord(id, normalized.getFileName().toString(), normalized.toString(), created, size);
        } catch (IOException e) { throw new IllegalArgumentException("Unable to register backup", e); }
    }
    public synchronized List<BackupRecord> listBackupRecords() {
        List<BackupRecord> records = new ArrayList<>();
        for (String key : config.stringPropertyNames()) {
            if (!key.startsWith("backup.record.") || !key.endsWith(".path")) continue;
            String id = key.substring("backup.record.".length(), key.length() - 5);
            String prefix = "backup.record." + id + ".";
            String path = config.getProperty(key);
            records.add(new BackupRecord(id, config.getProperty(prefix + "name", Path.of(path).getFileName().toString()), path,
                parseLong(config.getProperty(prefix + "created"), 0), parseLong(config.getProperty(prefix + "size"), 0)));
        }
        records.sort(Comparator.comparingLong(BackupRecord::createdAt).reversed());
        return records;
    }
    public synchronized void removeBackupRecord(String id) {
        for (String suffix : List.of("path", "name", "created", "size", "managed")) config.remove("backup.record." + id + "." + suffix);
        saveConfig();
    }
    public synchronized void deleteBackup(String id) throws IOException {
        if (!"true".equals(config.getProperty("backup.record." + id + ".managed")))
            throw new IOException("This external backup can only be removed from history");
        BackupRecord record = listBackupRecords().stream().filter(r -> r.id().equals(id)).findFirst()
            .orElseThrow(() -> new IOException("Backup no longer exists"));
        Path file = Path.of(record.absolutePath());
        rejectActive(file);
        if (Files.isSymbolicLink(file)) throw new IOException("Refusing to delete a symbolic link");
        Files.deleteIfExists(file);
        removeBackupRecord(id);
    }
    private synchronized void cleanOldBackups() {
        long cutoff = System.currentTimeMillis() - getRetentionDays() * 86400000L;
        for (BackupRecord r : listBackupRecords()) {
            if (r.createdAt() < cutoff && "true".equals(config.getProperty("backup.record." + r.id() + ".managed"))) {
                try { deleteBackup(r.id()); }
                catch (IOException e) { throw new IllegalStateException("Unable to remove expired backup", e); }
            }
        }
    }
    private long parseLong(String value, long fallback) {
        try { return Long.parseLong(value); } catch (NumberFormatException e) { return fallback; }
    }
    public synchronized String getBackupDirectory() { return config.getProperty("backup.directory", AppConstants.getBackupDirectory().toString()); }
    public synchronized void setBackupDirectory(String directory) {
        if (directory == null || directory.isBlank()) throw new IllegalArgumentException("Backup directory is required");
        config.setProperty("backup.directory", Path.of(directory).toAbsolutePath().toString()); saveConfig();
    }
    public synchronized boolean isAutoBackupEnabled() { return Boolean.parseBoolean(config.getProperty("backup.autobackup", "true")); }
    public synchronized void setAutoBackupEnabled(boolean enabled) { config.setProperty("backup.autobackup", String.valueOf(enabled)); saveConfig(); }
    public synchronized int getRetentionDays() { return (int)Math.max(1, Math.min(36500, parseLong(config.getProperty("backup.retention"), 7))); }
    public synchronized void setRetentionDays(int days) {
        if (days < 1 || days > 36500) throw new IllegalArgumentException("Invalid retention period");
        config.setProperty("backup.retention", String.valueOf(days)); saveConfig();
    }
    public synchronized int getBackupIntervalHours() { return (int)Math.max(1, Math.min(8760, parseLong(config.getProperty("backup.interval"), 24))); }
    public synchronized void setBackupIntervalHours(int hours) {
        if (hours < 1 || hours > 8760) throw new IllegalArgumentException("Invalid backup interval");
        config.setProperty("backup.interval", String.valueOf(hours)); saveConfig();
    }
    public synchronized File[] listBackups() {
        return listBackupRecords().stream().filter(BackupRecord::exists).map(r -> new File(r.absolutePath())).toArray(File[]::new);
    }
    public void startAutomaticBackups() {
        if (automaticTimer != null) return;
        automaticTimer = new javax.swing.Timer(60000, event -> {
            long now = System.currentTimeMillis();
            long last = parseLong(config.getProperty("backup.last_success"), 0);
            if (!isAutoBackupEnabled() || automaticRunning || now < retryAfter || now - last < getBackupIntervalHours() * 3600000L) return;
            automaticRunning = true;
            new SwingWorker<Path, Void>() {
                protected Path doInBackground() { return performBackup(); }
                protected void done() {
                    automaticRunning = false;
                    try { get(); }
                    catch (Exception e) {
                        retryAfter = System.currentTimeMillis() + 3600000L;
                        JOptionPane.showMessageDialog(null, I18n.tr("Automatic backup failed") + ": " + e.getMessage(),
                            I18n.tr("Backup"), JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });
        automaticTimer.setInitialDelay(1000);
        automaticTimer.start();
    }
}
