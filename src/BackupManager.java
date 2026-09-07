import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import java.util.Properties;
import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class BackupManager {
    public record BackupRecord(String id, String name, String absolutePath,
            long createdAt, long size) {
        public boolean exists() {
            return Files.isRegularFile(Path.of(absolutePath));
        }
    }

    private static BackupManager instance;
    private Properties config;

    private BackupManager() {
        loadConfig();
    }

    public static BackupManager getInstance() {
        if (instance == null) {
            instance = new BackupManager();
        }
        return instance;
    }

    private void loadConfig() {
        config = new Properties();
        try {
            Path configPath = AppConstants.getBackupConfigPath();
            if (Files.exists(configPath)) {
                try (InputStreamReader reader = new InputStreamReader(
                        new FileInputStream(configPath.toFile()),
                        java.nio.charset.StandardCharsets.UTF_8)) {
                    config.load(reader);
                }
            } else {
                // Default settings
                config.setProperty("backup.directory", AppConstants.getBackupDirectory().toString());
                config.setProperty("backup.autobackup", "true");
                config.setProperty("backup.retention", "7");
                config.setProperty("backup.interval", "24");
                saveConfig();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(AppConstants.getBackupConfigPath().toFile()),
                java.nio.charset.StandardCharsets.UTF_8)) {
            config.store(writer, "Backup Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public Path performBackup() {
        try {
            String backupDir = config.getProperty("backup.directory", AppConstants.getBackupDirectory().toString());
            Files.createDirectories(Paths.get(backupDir));

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String backupFileName = AppConstants.DATABASE_FILE_NAME.replace(".db", "_" + sdf.format(new Date()) + ".db");
            String backupPath = Paths.get(backupDir, backupFileName).toString();

            // Check if source database exists
            Path sourceDb = AppConstants.getDatabasePath();
            if (!Files.exists(sourceDb)) {
                throw new IOException("Database file '" + sourceDb + "' not found");
            }

            Path target = Paths.get(backupPath);
            Files.deleteIfExists(target);
            try (Connection snapshotConn = DriverManager.getConnection("jdbc:sqlite:" + sourceDb)) {
                try (Statement settings = snapshotConn.createStatement()) {
                    settings.execute("PRAGMA busy_timeout = 10000");
                }
                try (PreparedStatement pstmt = snapshotConn.prepareStatement("VACUUM INTO ?")) {
                    pstmt.setString(1, target.toAbsolutePath().toString());
                    pstmt.execute();
                }
            }
            try (Connection backupConn = DriverManager.getConnection("jdbc:sqlite:" + target);
                 Statement stmt = backupConn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    Files.deleteIfExists(target);
                    throw new IOException("Backup database failed integrity check");
                }
            }
            cleanOldBackups();
            registerBackup(target);
            return target;

        } catch (IOException | SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error during backup: " + e.getMessage());
        }
    }

    public synchronized BackupRecord importBackup(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        validateBackup(normalized);
        return registerBackup(normalized);
    }

    /** Creates a portable snapshot of the current WareStat database without
     * adding it to the backup history. */
    public Path exportDatabase(Path destination) {
        Path target = destination.toAbsolutePath().normalize();
        Path source = AppConstants.getDatabasePath().toAbsolutePath().normalize();
        if (target.equals(source)) {
            throw new IllegalArgumentException("The current database cannot be overwritten during export");
        }
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.deleteIfExists(target);
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA busy_timeout = 10000");
                }
                try (PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
                    statement.setString(1, target.toString());
                    statement.execute();
                }
            }
            validateBackup(target);
            return target;
        } catch (IOException | SQLException exception) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw new RuntimeException("Unable to export database: " + exception.getMessage(), exception);
        }
    }

    /** Replaces the active database with a validated WareStat database file. */
    public void importDatabase(Path sourceFile) {
        Path source = sourceFile.toAbsolutePath().normalize();
        Path current = AppConstants.getDatabasePath().toAbsolutePath().normalize();
        validateBackup(source);
        if (source.equals(current)) {
            throw new IllegalArgumentException("The selected database is already in use");
        }

        Path temporary = current.resolveSibling(current.getFileName() + ".importing");
        Path safetyCopy = current.resolveSibling(current.getFileName() + ".import-safety");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            validateBackup(temporary);
            if (Files.exists(current)) exportDatabase(safetyCopy);
            DatabaseManager.getInstance().closeConnection();
            Files.deleteIfExists(Path.of(current + "-wal"));
            Files.deleteIfExists(Path.of(current + "-shm"));
            try {
                Files.move(temporary, current, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, current, StandardCopyOption.REPLACE_EXISTING);
            }
            DatabaseManager.getInstance().initDatabase();
            CompanyData.getInstance().reloadFromDatabase();
            Files.deleteIfExists(safetyCopy);
        } catch (Exception exception) {
            try {
                DatabaseManager.getInstance().closeConnection();
                if (Files.exists(safetyCopy)) {
                    Files.copy(safetyCopy, current, StandardCopyOption.REPLACE_EXISTING);
                    DatabaseManager.getInstance().initDatabase();
                    CompanyData.getInstance().reloadFromDatabase();
                }
            } catch (Exception rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw new RuntimeException("Unable to import database: " + exception.getMessage(), exception);
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    private void validateBackup(Path file) {
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IllegalArgumentException("Backup file not found or not readable");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
                if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                    throw new IllegalArgumentException("The selected file is not a valid SQLite backup");
                }
            }
            try (ResultSet result = statement.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
                    "AND name IN ('products', 'orders')")) {
                if (!result.next() || result.getInt(1) < 2) {
                    throw new IllegalArgumentException("The selected file is not a WareStat backup");
                }
            }
        } catch (SQLException exception) {
            throw new IllegalArgumentException("Unable to validate backup: " + exception.getMessage(), exception);
        }
    }

    public void validateDatabase(Path file) {
        validateBackup(file.toAbsolutePath().normalize());
    }

    private synchronized BackupRecord registerBackup(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        for (BackupRecord record : listBackupRecords()) {
            if (Path.of(record.absolutePath()).toAbsolutePath().normalize().equals(normalized)) {
                return record;
            }
        }
        String id = UUID.randomUUID().toString();
        long createdAt;
        long size;
        try {
            createdAt = Files.getLastModifiedTime(normalized).toMillis();
            size = Files.size(normalized);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read backup file: " + exception.getMessage(), exception);
        }
        String prefix = "backup.record." + id + ".";
        config.setProperty(prefix + "path", normalized.toString());
        config.setProperty(prefix + "name", normalized.getFileName().toString());
        config.setProperty(prefix + "created", Long.toString(createdAt));
        config.setProperty(prefix + "size", Long.toString(size));
        saveConfig();
        return new BackupRecord(id, normalized.getFileName().toString(), normalized.toString(), createdAt, size);
    }

    public synchronized List<BackupRecord> listBackupRecords() {
        List<BackupRecord> records = new ArrayList<>();
        String suffix = ".path";
        for (String key : config.stringPropertyNames()) {
            if (!key.startsWith("backup.record.") || !key.endsWith(suffix)) continue;
            String id = key.substring("backup.record.".length(), key.length() - suffix.length());
            String prefix = "backup.record." + id + ".";
            String path = config.getProperty(prefix + "path");
            String name = config.getProperty(prefix + "name", Path.of(path).getFileName().toString());
            long created = parseLong(config.getProperty(prefix + "created"), 0);
            long size = parseLong(config.getProperty(prefix + "size"), 0);
            records.add(new BackupRecord(id, name, path, created, size));
        }
        records.sort(Comparator.comparingLong(BackupRecord::createdAt).reversed());
        return records;
    }

    public synchronized void removeBackupRecord(String id) {
        String prefix = "backup.record." + id + ".";
        config.remove(prefix + "path");
        config.remove(prefix + "name");
        config.remove(prefix + "created");
        config.remove(prefix + "size");
        saveConfig();
    }

    private long parseLong(String value, long fallback) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { return fallback; }
    }
    
    private void cleanOldBackups() {
        try {
            String backupDir = config.getProperty("backup.directory", AppConstants.getBackupDirectory().toString());
            int retentionDays = Integer.parseInt(config.getProperty("backup.retention", "7"));

            File dir = new File(backupDir);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.endsWith(".db"));
                if (files != null) {
                    long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
                    for (File file : files) {
                        if (file.lastModified() < cutoffTime) {
                            if (file.delete()) {
                                removeBackupRecordByPath(file.toPath());
                            } else {
                                System.err.println("Failed to delete old backup: " + file.getName());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeBackupRecordByPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        for (BackupRecord record : listBackupRecords()) {
            if (Path.of(record.absolutePath()).toAbsolutePath().normalize().equals(normalized)) {
                removeBackupRecord(record.id());
                return;
            }
        }
    }
    
    public void restoreBackup(String backupFile) {
        try {
            if (!Files.exists(Paths.get(backupFile))) {
                throw new FileNotFoundException("Backup file not found: " + backupFile);
            }

            // Close connection safely
            try {
                DatabaseManager.getInstance().closeConnection();
            } catch (Exception e) {
                System.err.println("Warning: Could not close database connection: " + e.getMessage());
            }

            String currentBackup = AppConstants.DATABASE_FILE_NAME.replace(".db", "_pre_restore_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".db");

            // Check if current database exists
            Path currentDb = AppConstants.getDatabasePath();
            Path preRestoreBackup = AppConstants.getAppDataDirectory().resolve(currentBackup);

            if (Files.exists(currentDb)) {
                Files.copy(currentDb, preRestoreBackup, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.copy(Paths.get(backupFile), currentDb, StandardCopyOption.REPLACE_EXISTING);

            // Handle reconnection failure
            try {
                DatabaseManager.getInstance().initDatabase();
                CompanyData.getInstance().reloadFromDatabase();
            } catch (Exception e) {
                // Restore previous backup if reconnection fails
                if (Files.exists(preRestoreBackup)) {
                    Files.copy(preRestoreBackup, currentDb, StandardCopyOption.REPLACE_EXISTING);
                    DatabaseManager.getInstance().initDatabase();
                }
                throw new RuntimeException("Failed to restore database: " + e.getMessage());
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error during restore: " + e.getMessage());
        }
    }
    
    public String getBackupDirectory() {
        return config.getProperty("backup.directory", AppConstants.getBackupDirectory().toString());
    }
    
    public void setBackupDirectory(String directory) {
        config.setProperty("backup.directory", directory);
        saveConfig();
    }
    
    public boolean isAutoBackupEnabled() {
        return Boolean.parseBoolean(config.getProperty("backup.autobackup", "true"));
    }
    
    public void setAutoBackupEnabled(boolean enabled) {
        config.setProperty("backup.autobackup", String.valueOf(enabled));
        saveConfig();
    }
    
    public int getRetentionDays() {
        return Integer.parseInt(config.getProperty("backup.retention", "7"));
    }
    
    public void setRetentionDays(int days) {
        config.setProperty("backup.retention", String.valueOf(days));
        saveConfig();
    }

    public int getBackupIntervalHours() {
        return Integer.parseInt(config.getProperty("backup.interval", "24"));
    }

    public void setBackupIntervalHours(int hours) {
        config.setProperty("backup.interval", String.valueOf(hours));
        saveConfig();
    }
    
    public File[] listBackups() {
        File dir = new File(getBackupDirectory());
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".db"));
            return files != null ? files : new File[0];
        }
        return new File[0];
    }
}
