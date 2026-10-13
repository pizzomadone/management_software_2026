import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import javax.swing.*;

public class BackupManager {
    public record BackupRecord(String id, String name, String absolutePath, long createdAt, long size) {
        public boolean exists() { return Files.isRegularFile(Path.of(absolutePath)); }
    }

    private record ArchiveContent(Path root, Path database, Path images) {}

    public static final String BACKUP_SIGNATURE = "WARESTAT_BACKUP_V1";
    public static final int BACKUP_FORMAT_VERSION = 1;

    private static BackupManager instance;
    private final Properties config = new Properties();
    private javax.swing.Timer automaticTimer;
    private boolean automaticRunning;
    private long retryAfter;

    private BackupManager() {
        Path file = AppConstants.getBackupConfigPath();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { config.load(r); }
            catch (IOException | IllegalArgumentException e) {
                throw new IllegalStateException("Unable to read backup settings", e);
            }
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
            moveReplace(temporary, path);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save backup settings", e);
        } finally { removeTemporary(temporary); }
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
        try {
            if (Files.isDirectory(file)) deleteDirectory(file);
            else Files.deleteIfExists(file);
        } catch (IOException ignored) {}
    }

    private Path snapshot(Path source, Path directory) throws IOException, SQLException {
        return snapshot(source, directory, null, false);
    }

    private Path snapshot(Path source, Path directory, String legacyCurrency, boolean removeImageReferences)
            throws IOException, SQLException {
        if (!Files.isRegularFile(source) || !Files.isReadable(source))
            throw new IOException("Database file not found or not readable");
        Files.createDirectories(directory);
        Path staging = Files.createTempFile(directory, "warestat-snapshot-", ".db");
        Files.delete(staging);
        try {
            org.sqlite.SQLiteConfig settings = new org.sqlite.SQLiteConfig();
            settings.setReadOnly(true);
            settings.setBusyTimeout(10000);
            try (Connection c = DriverManager.getConnection(
                    "jdbc:sqlite:" + source.toAbsolutePath(), settings.toProperties());
                 PreparedStatement p = c.prepareStatement("VACUUM INTO ?")) {
                if (DatabaseCurrency.read(c) == null && legacyCurrency == null)
                    throw new SQLException("Original currency is required to import a legacy database");
                p.setString(1, staging.toAbsolutePath().toString());
                p.execute();
            }

            try (Connection c = DatabaseManager.openConnection("jdbc:sqlite:" + staging)) {
                if (legacyCurrency != null) DatabaseCurrency.initialize(c, legacyCurrency);
                DatabaseManager.prepareDatabase(c);
                if (removeImageReferences) {
                    try (Statement s = c.createStatement()) {
                        s.executeUpdate("DELETE FROM product_images");
                        s.execute("VACUUM");
                    }
                }
                DatabaseIntegrity.validate(c, false);
            }
            return staging;
        } catch (SQLException | RuntimeException e) {
            removeTemporary(staging);
            throw e;
        }
    }

    private static void moveReplace(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public synchronized Path performBackup() { return performBackup(true); }

    public synchronized Path performDatabaseOnlyBackup() { return performBackup(false); }

    /**
     * Saves the current data as a complete, user-named archive and then replaces
     * the live database with a brand-new empty one. Application settings remain.
     */
    public synchronized Path archiveAndStartNew(Path destination) {
        Path work = null;
        Path temporaryArchive = null;
        Path emptyDatabase = null;
        Path safetyDatabase = null;
        Path safetyImages = null;
        Path target = destination == null ? null : destination.toAbsolutePath().normalize();
        Path current = AppConstants.getDatabasePath().toAbsolutePath().normalize();
        boolean databaseReplaced = false;

        try {
            if (target == null || target.getParent() == null || !isNewArchive(target))
                throw new IOException("The new archive filename must end in .wsbq");
            rejectActive(target);
            Files.createDirectories(target.getParent());

            work = Files.createTempDirectory(AppConstants.getAppDataDirectory(), "new-archive-");
            safetyDatabase = snapshot(current, work);
            safetyImages = work.resolve("current-images");
            ProductImageStore.getInstance().copyAllFilesTo(safetyImages);

            temporaryArchive = Files.createTempFile(target.getParent(), "warestat-archive-", ".tmp");
            createArchive(safetyDatabase, temporaryArchive, true);
            moveReplace(temporaryArchive, target);
            temporaryArchive = null;

            // Never erase the live data unless the just-created archive can be read back.
            validateBackup(target);

            String currency = DatabaseCurrency.boundCode();
            if (currency == null || currency.isBlank()) currency = SettingsPanel.getCurrencyCode();
            emptyDatabase = work.resolve("empty-data.db");
            DatabaseManager.createEmptyDatabase(emptyDatabase, currency);

            DatabaseManager.getInstance().closeConnection();
            Files.deleteIfExists(Path.of(current + "-wal"));
            Files.deleteIfExists(Path.of(current + "-shm"));
            moveReplace(emptyDatabase, current);
            emptyDatabase = null;
            databaseReplaced = true;

            Path emptyImages = work.resolve("empty-images");
            Files.createDirectories(emptyImages);
            ProductImageStore.getInstance().replaceAllFilesFrom(emptyImages);

            DatabaseManager.getInstance().initDatabase();
            CompanyData.getInstance().reloadFromDatabase();
            ProductImageStore.getInstance().removeOrphanFiles();
            DatabaseIntegrity.validate(DatabaseManager.getInstance().getConnection(), false);

            // A manually created archive must never be removed by automatic retention.
            registerBackup(target, false);
            config.setProperty("archive.last_directory", target.getParent().toString());
            saveConfig();
            return target;
        } catch (Exception e) {
            if (databaseReplaced && safetyDatabase != null && Files.isRegularFile(safetyDatabase)) {
                try {
                    DatabaseManager.getInstance().closeConnection();
                    Path rollback = snapshot(safetyDatabase, current.getParent());
                    Files.deleteIfExists(Path.of(current + "-wal"));
                    Files.deleteIfExists(Path.of(current + "-shm"));
                    moveReplace(rollback, current);
                    if (safetyImages != null && Files.isDirectory(safetyImages))
                        ProductImageStore.getInstance().replaceAllFilesFrom(safetyImages);
                    DatabaseManager.getInstance().initDatabase();
                    CompanyData.getInstance().reloadFromDatabase();
                } catch (Exception recovery) {
                    e.addSuppressed(recovery);
                }
            }
            throw new IllegalStateException("Unable to create new archive: " + e.getMessage(), e);
        } finally {
            removeTemporary(emptyDatabase);
            removeTemporary(temporaryArchive);
            removeTemporary(work);
        }
    }

    public synchronized Path performBackup(boolean includePhotos) {
        Path staging = null;
        Path temporaryArchive = null;
        try {
            Path folder = Path.of(getBackupDirectory()).toAbsolutePath();
            Files.createDirectories(folder);
            staging = snapshot(AppConstants.getDatabasePath(), folder, null, !includePhotos);

            Path target = folder.resolve("WareStat-Backup-" + System.currentTimeMillis() + "-" +
                UUID.randomUUID() + ".wsbq");
            temporaryArchive = Files.createTempFile(folder, "warestat-backup-", ".wsbq");
            createArchive(staging, temporaryArchive, includePhotos);
            validateBackup(temporaryArchive);
            moveReplace(temporaryArchive, target);
            temporaryArchive = null;

            registerBackup(target);
            config.setProperty("backup.last_success", Long.toString(System.currentTimeMillis()));
            saveConfig();
            cleanOldBackups();
            return target;
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Error during backup: " + e.getMessage(), e);
        } finally {
            removeTemporary(staging);
            removeTemporary(temporaryArchive);
        }
    }

    private void createArchive(Path databaseSnapshot, Path destination, boolean includePhotos) throws IOException, SQLException {
        List<String> referenced = readImageReferences(databaseSnapshot);
        for (String filename : referenced) {
            validateJpegFilename(filename);
            if (!Files.isRegularFile(ProductImageStore.getInstance().imagePath(filename)))
                throw new IOException("Missing product image " + filename);
        }

        StringBuilder imageArray = new StringBuilder();
        for (int i = 0; i < referenced.size(); i++) {
            if (i > 0) imageArray.append(',');
            imageArray.append('"').append(jsonEscape(referenced.get(i))).append('"');
        }

        String manifest = "{" +
            "\"signature\":\"" + BACKUP_SIGNATURE + "\"," +
            "\"format\":\"WareStat-Backup\"," +
            "\"format_version\":" + BACKUP_FORMAT_VERSION + "," +
            "\"created_at\":" + System.currentTimeMillis() + "," +
            "\"database\":\"" + jsonEscape(AppConstants.DATABASE_FILE_NAME) + "\"," +
            "\"image_format\":\"jpeg\"," +
            "\"images_included\":" + includePhotos + "," +
            "\"images\":[" + imageArray + "]" +
            "}";

        Path zipStaging = null;
        try {
            zipStaging = Files.createTempFile(destination.toAbsolutePath().getParent(), "warestat-zip-", ".tmp");
            try (OutputStream raw = Files.newOutputStream(zipStaging, StandardOpenOption.TRUNCATE_EXISTING);
                 ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw))) {
                putText(zip, "manifest.json", manifest);
                putFile(zip, "database/" + AppConstants.DATABASE_FILE_NAME, databaseSnapshot);
                if (includePhotos) {
                    for (String filename : referenced)
                        putFile(zip, "images/" + filename, ProductImageStore.getInstance().imagePath(filename));
                }
            }
            WareStatArchiveFormat.wrap(zipStaging, destination);
        } finally {
            removeTemporary(zipStaging);
        }
    }

    public synchronized BackupRecord importBackup(Path file) { return importBackup(file, null); }

    public synchronized BackupRecord importBackup(Path file, String legacyCurrency) {
        Path staging = null;
        Path work = null;
        try {
            rejectActive(file);
            Path folder = Path.of(getBackupDirectory()).toAbsolutePath();
            Files.createDirectories(folder);
            if (isArchive(file)) {
                work = Files.createTempDirectory(AppConstants.getAppDataDirectory(), "backup-import-");
                validateArchive(file, work);
                Path target = folder.resolve("imported_" + UUID.randomUUID()
                    + (isNewArchive(file) ? ".wsbq" : ".wsbu"));
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return registerBackup(target);
            }

            staging = snapshot(file, folder, legacyCurrency, true);
            Path target = folder.resolve("imported_" + UUID.randomUUID() + ".db");
            moveReplace(staging, target);
            staging = null;
            return registerBackup(target);
        } catch (IOException | SQLException e) {
            throw new IllegalArgumentException("Unable to import backup: " + e.getMessage(), e);
        } finally {
            removeTemporary(staging);
            removeTemporary(work);
        }
    }

    public synchronized Path exportDatabase(Path destination) {
        return exportDatabase(destination, true);
    }

    public synchronized Path exportDatabase(Path destination, boolean includePhotos) {
        Path staging = null;
        Path temporaryArchive = null;
        try {
            Path target = destination.toAbsolutePath().normalize();
            rejectActive(target);
            if (!isNewArchive(target))
                throw new IOException("The export filename must end in .wsbq");
            staging = snapshot(AppConstants.getDatabasePath(), target.getParent(), null, !includePhotos);
            temporaryArchive = Files.createTempFile(target.getParent(), "warestat-export-", ".wsbq");
            createArchive(staging, temporaryArchive, includePhotos);
            validateBackup(temporaryArchive);
            moveReplace(temporaryArchive, target);
            temporaryArchive = null;
            return target;
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Unable to export database: " + e.getMessage(), e);
        } finally {
            removeTemporary(staging);
            removeTemporary(temporaryArchive);
        }
    }

    public synchronized void importDatabase(Path sourceFile) { importDatabase(sourceFile, null); }

    public synchronized void importDatabase(Path sourceFile, String legacyCurrency) {
        Path current = AppConstants.getDatabasePath().toAbsolutePath();
        Path staging = null;
        Path safetyCopy = null;
        boolean replaced = false;
        try {
            rejectActive(sourceFile);
            staging = snapshot(sourceFile, current.getParent(), legacyCurrency, true);
            if (Files.exists(current)) {
                safetyCopy = snapshot(current, current.getParent());
                Path retained = current.resolveSibling("data_before_restore_" + UUID.randomUUID() + ".db");
                moveReplace(safetyCopy, retained);
                safetyCopy = retained;
            }

            DatabaseManager.getInstance().closeConnection();
            Files.deleteIfExists(Path.of(current + "-wal"));
            Files.deleteIfExists(Path.of(current + "-shm"));
            moveReplace(staging, current);
            staging = null;
            replaced = true;
            DatabaseManager.getInstance().initDatabase();
            CompanyData.getInstance().reloadFromDatabase();
            ProductImageStore.getInstance().removeOrphanFiles();
        } catch (Exception e) {
            if (replaced && safetyCopy != null) {
                try {
                    DatabaseManager.getInstance().closeConnection();
                    Path rollback = snapshot(safetyCopy, current.getParent());
                    Files.deleteIfExists(Path.of(current + "-wal"));
                    Files.deleteIfExists(Path.of(current + "-shm"));
                    moveReplace(rollback, current);
                    DatabaseManager.getInstance().initDatabase();
                    CompanyData.getInstance().reloadFromDatabase();
                } catch (Exception recovery) { e.addSuppressed(recovery); }
            }
            throw new IllegalStateException("Unable to import database: " + e.getMessage() +
                (safetyCopy == null ? "" : ". Previous database retained at " + safetyCopy), e);
        } finally { removeTemporary(staging); }
    }

    public void restoreBackup(String file) { restoreBackup(file, null); }

    public synchronized CostIntegrity.Audit restoreBackup(String file, String legacyCurrency) {
        Path path = Path.of(file);
        if (isArchive(path)) restoreArchive(path);
        else importDatabase(path, legacyCurrency);
        try {
            return CostIntegrity.audit(DatabaseManager.getInstance().getConnection());
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Database restored but cost completeness could not be checked: " + e.getMessage(), e);
        }
    }

    private void restoreArchive(Path archive) {
        Path work = null;
        Path prepared = null;
        Path safetyDb = null;
        Path safetyImages = null;
        Path current = AppConstants.getDatabasePath().toAbsolutePath();
        boolean databaseReplaced = false;
        try {
            work = Files.createTempDirectory(AppConstants.getAppDataDirectory(), "backup-restore-");
            ArchiveContent extracted = extractArchive(archive, work);
            prepared = snapshot(extracted.database(), current.getParent(), null, false);
            validateImageCoherence(prepared, extracted.images());

            if (Files.exists(current)) safetyDb = snapshot(current, work);
            safetyImages = work.resolve("current-images");
            ProductImageStore.getInstance().copyAllFilesTo(safetyImages);

            DatabaseManager.getInstance().closeConnection();
            Files.deleteIfExists(Path.of(current + "-wal"));
            Files.deleteIfExists(Path.of(current + "-shm"));
            moveReplace(prepared, current);
            prepared = null;
            databaseReplaced = true;

            ProductImageStore.getInstance().replaceOriginalsFrom(extracted.images());
            DatabaseManager.getInstance().initDatabase();
            CompanyData.getInstance().reloadFromDatabase();
            ProductImageStore.getInstance().removeOrphanFiles();
            DatabaseIntegrity.validate(DatabaseManager.getInstance().getConnection(), false);
        } catch (Exception e) {
            try {
                if (databaseReplaced && safetyDb != null && Files.isRegularFile(safetyDb)) {
                    DatabaseManager.getInstance().closeConnection();
                    Path rollback = snapshot(safetyDb, current.getParent());
                    Files.deleteIfExists(Path.of(current + "-wal"));
                    Files.deleteIfExists(Path.of(current + "-shm"));
                    moveReplace(rollback, current);
                }
                if (safetyImages != null && Files.isDirectory(safetyImages))
                    ProductImageStore.getInstance().replaceAllFilesFrom(safetyImages);
                DatabaseManager.getInstance().initDatabase();
                CompanyData.getInstance().reloadFromDatabase();
            } catch (Exception recovery) {
                e.addSuppressed(recovery);
            }
            throw new IllegalStateException("Unable to restore backup: " + e.getMessage(), e);
        } finally {
            removeTemporary(prepared);
            removeTemporary(work);
        }
    }

    private void validateArchive(Path archive, Path work) throws IOException, SQLException {
        ArchiveContent extracted = extractArchive(archive, work);
        Path prepared = null;
        try {
            prepared = snapshot(extracted.database(), work, null, false);
            validateImageCoherence(prepared, extracted.images());
        } finally { removeTemporary(prepared); }
    }

    private ArchiveContent extractArchive(Path archive, Path work) throws IOException {
        if (!Files.isRegularFile(archive) || !Files.isReadable(archive))
            throw new IOException("Backup file not found or not readable");
        Path extracted = work.resolve("extracted");
        Files.createDirectories(extracted);
        Path payload = archive;
        if (isNewArchive(archive)) {
            payload = work.resolve("verified-payload.zip");
            WareStatArchiveFormat.unwrap(archive, payload);
        }
        extractSafely(payload, extracted);

        Path manifestPath = extracted.resolve("manifest.json");
        if (!Files.isRegularFile(manifestPath)) throw new IOException("Backup manifest is missing");
        String manifest = Files.readString(manifestPath, StandardCharsets.UTF_8);
        requireManifest(manifest);

        String databaseName = manifestString(manifest, "database");
        if (databaseName == null || !databaseName.equals(Path.of(databaseName).getFileName().toString()) ||
                databaseName.contains(".."))
            throw new IOException("Invalid database entry in backup");

        Path database = extracted.resolve("database").resolve(databaseName).normalize();
        Path images = extracted.resolve("images").normalize();
        if (!Files.isRegularFile(database)) throw new IOException("Backup database is missing");
        if (!Files.exists(images)) Files.createDirectories(images);
        return new ArchiveContent(extracted, database, images);
    }

    private static void requireManifest(String manifest) throws IOException {
        String signature = manifestString(manifest, "signature");
        String format = manifestString(manifest, "format");
        Integer version = manifestInteger(manifest, "format_version");
        String imageFormat = manifestString(manifest, "image_format");
        if (!BACKUP_SIGNATURE.equals(signature) || !"WareStat-Backup".equals(format) ||
                version == null || version != BACKUP_FORMAT_VERSION)
            throw new IOException("Unsupported WareStat backup format");
        if (imageFormat != null && !"jpeg".equalsIgnoreCase(imageFormat))
            throw new IOException("Unsupported product image format in backup");
    }

    private void validateImageCoherence(Path dbFile, Path imagesDir) throws SQLException, IOException {
        org.sqlite.SQLiteConfig settings = new org.sqlite.SQLiteConfig();
        settings.setReadOnly(true);
        try (Connection c = DriverManager.getConnection(
                "jdbc:sqlite:" + dbFile.toAbsolutePath(), settings.toProperties())) {
            try (Statement s = c.createStatement();
                 ResultSet r = s.executeQuery("SELECT filename FROM product_images")) {
                while (r.next()) {
                    String name = r.getString(1);
                    validateJpegFilename(name);
                    if (!Files.isRegularFile(imagesDir.resolve(name)))
                        throw new IOException("Backup is missing product image " + name);
                }
            }
            try (Statement s = c.createStatement();
                 ResultSet r = s.executeQuery("""
                    SELECT product_id,COUNT(*) AS total,
                           SUM(CASE WHEN is_primary=1 THEN 1 ELSE 0 END) AS primary_count,
                           COUNT(DISTINCT sort_order) AS order_count
                    FROM product_images GROUP BY product_id
                 """)) {
                while (r.next()) {
                    int total = r.getInt("total");
                    int primary = r.getInt("primary_count");
                    if (total > ProductImageStore.MAX_IMAGES_PER_PRODUCT)
                        throw new IOException("A product has more than three images");
                    if (total > 0 && primary != 1)
                        throw new IOException("A product must have exactly one primary image");
                    if (r.getInt("order_count") != total)
                        throw new IOException("A product has duplicate image sort order values");
                }
            }
        }
    }

    private static List<String> readImageReferences(Path dbFile) throws SQLException {
        List<String> names = new ArrayList<>();
        org.sqlite.SQLiteConfig settings = new org.sqlite.SQLiteConfig();
        settings.setReadOnly(true);
        try (Connection c = DriverManager.getConnection(
                "jdbc:sqlite:" + dbFile.toAbsolutePath(), settings.toProperties());
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(
                "SELECT filename FROM product_images ORDER BY product_id,sort_order,id")) {
            while (r.next()) names.add(r.getString(1));
        }
        return names;
    }

    private static void validateJpegFilename(String filename) throws IOException {
        if (filename == null || filename.isBlank() ||
                !filename.equals(Path.of(filename).getFileName().toString()) ||
                filename.contains("..") || !filename.toLowerCase(Locale.ROOT).endsWith(".jpg"))
            throw new IOException("Invalid product image filename in backup");
    }

    private static void extractSafely(Path archive, Path destination) throws IOException {
        long total = 0;
        int entries = 0;
        Path root = destination.toRealPath();
        try (InputStream raw = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                entries++;
                if (entries > 5000) throw new IOException("Backup contains too many entries");
                Path output = destination.resolve(entry.getName()).normalize();
                if (!output.startsWith(root)) throw new IOException("Unsafe backup entry");
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    try (OutputStream out = Files.newOutputStream(output,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        byte[] buffer = new byte[64 * 1024];
                        for (int read; (read = zip.read(buffer)) > 0; ) {
                            total += read;
                            if (total > 512L * 1024L * 1024L)
                                throw new IOException("Backup is too large");
                            out.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static String manifestString(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
            .matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
    }

    private static Integer manifestInteger(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void putText(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void putFile(ZipOutputStream zip, String name, Path file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private static boolean isNewArchive(Path file) {
        return file != null && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wsbq");
    }

    public static boolean isArchive(Path file) {
        return isNewArchive(file) ||
            (file != null && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wsbu"));
    }

    private void validateBackup(Path file) {
        if (isArchive(file)) {
            Path work = null;
            try {
                work = Files.createTempDirectory(AppConstants.getAppDataDirectory(), "backup-validate-");
                validateArchive(file, work);
            } catch (IOException | SQLException e) {
                throw new IllegalArgumentException("Invalid WareStat backup: " + e.getMessage(), e);
            } finally { removeTemporary(work); }
            return;
        }

        if (!Files.isRegularFile(file) || !Files.isReadable(file))
            throw new IllegalArgumentException("Backup file not found or not readable");
        org.sqlite.SQLiteConfig settings = new org.sqlite.SQLiteConfig();
        settings.setReadOnly(true);
        try (Connection c = DriverManager.getConnection(
                "jdbc:sqlite:" + file.toAbsolutePath(), settings.toProperties())) {
            DatabaseIntegrity.validate(c, true);
        } catch (SQLException e) {
            throw new IllegalArgumentException("Invalid WareStat backup: " + e.getMessage(), e);
        }
    }

    public void validateDatabase(Path file) { validateBackup(file); }

    private synchronized BackupRecord registerBackup(Path file) {
        return registerBackup(file, true);
    }

    private synchronized BackupRecord registerBackup(Path file, boolean managed) {
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
            config.setProperty(prefix + "managed", Boolean.toString(managed));
            saveConfig();
            return new BackupRecord(id, normalized.getFileName().toString(),
                normalized.toString(), created, size);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to register backup", e);
        }
    }

    public synchronized List<BackupRecord> listBackupRecords() {
        List<BackupRecord> records = new ArrayList<>();
        for (String key : config.stringPropertyNames()) {
            if (!key.startsWith("backup.record.") || !key.endsWith(".path")) continue;
            String id = key.substring("backup.record.".length(), key.length() - 5);
            String prefix = "backup.record." + id + ".";
            String path = config.getProperty(key);
            records.add(new BackupRecord(id,
                config.getProperty(prefix + "name", Path.of(path).getFileName().toString()),
                path, parseLong(config.getProperty(prefix + "created"), 0),
                parseLong(config.getProperty(prefix + "size"), 0)));
        }
        records.sort(Comparator.comparingLong(BackupRecord::createdAt).reversed());
        return records;
    }

    public synchronized void removeBackupRecord(String id) {
        for (String suffix : List.of("path", "name", "created", "size", "managed"))
            config.remove("backup.record." + id + "." + suffix);
        saveConfig();
    }

    public synchronized void deleteBackup(String id) throws IOException {
        if (!"true".equals(config.getProperty("backup.record." + id + ".managed")))
            throw new IOException("This external backup can only be removed from history");
        BackupRecord record = listBackupRecords().stream()
            .filter(r -> r.id().equals(id)).findFirst()
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
            if (r.createdAt() < cutoff &&
                    "true".equals(config.getProperty("backup.record." + r.id() + ".managed"))) {
                try { deleteBackup(r.id()); }
                catch (IOException e) { throw new IllegalStateException("Unable to remove expired backup", e); }
            }
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    private long parseLong(String value, long fallback) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException e) { return fallback; }
    }

    public synchronized String getBackupDirectory() {
        return config.getProperty("backup.directory", AppConstants.getBackupDirectory().toString());
    }

    public synchronized String getArchiveDirectory() {
        String fallback = getBackupDirectory();
        String stored = config.getProperty("archive.last_directory", fallback);
        if (stored == null || stored.isBlank()) return fallback;
        try {
            Path path = Path.of(stored).toAbsolutePath().normalize();
            return Files.isDirectory(path) ? path.toString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public synchronized void setBackupDirectory(String directory) {
        if (directory == null || directory.isBlank())
            throw new IllegalArgumentException("Backup directory is required");
        config.setProperty("backup.directory", Path.of(directory).toAbsolutePath().toString());
        saveConfig();
    }

    public synchronized boolean isAutoBackupEnabled() {
        return Boolean.parseBoolean(config.getProperty("backup.autobackup", "true"));
    }

    public synchronized void setAutoBackupEnabled(boolean enabled) {
        config.setProperty("backup.autobackup", String.valueOf(enabled));
        saveConfig();
    }

    public synchronized int getRetentionDays() {
        return (int)Math.max(1, Math.min(36500,
            parseLong(config.getProperty("backup.retention"), 7)));
    }

    public synchronized void setRetentionDays(int days) {
        if (days < 1 || days > 36500) throw new IllegalArgumentException("Invalid retention period");
        config.setProperty("backup.retention", String.valueOf(days));
        saveConfig();
    }

    public synchronized int getBackupIntervalHours() {
        return (int)Math.max(1, Math.min(8760,
            parseLong(config.getProperty("backup.interval"), 24)));
    }

    public synchronized void setBackupIntervalHours(int hours) {
        if (hours < 1 || hours > 8760) throw new IllegalArgumentException("Invalid backup interval");
        config.setProperty("backup.interval", String.valueOf(hours));
        saveConfig();
    }

    public synchronized File[] listBackups() {
        return listBackupRecords().stream().filter(BackupRecord::exists)
            .map(r -> new File(r.absolutePath())).toArray(File[]::new);
    }

    synchronized boolean isAutomaticBackupDue(long now) {
        long last = parseLong(config.getProperty("backup.last_success"), 0);
        return isAutoBackupEnabled() && !automaticRunning && now >= retryAfter &&
            now - last >= getBackupIntervalHours() * 3600000L;
    }

    public void startAutomaticBackups() {
        if (automaticTimer != null) return;
        automaticTimer = new javax.swing.Timer(60000, event -> {
            long now = System.currentTimeMillis();
            if (!isAutomaticBackupDue(now)) return;
            automaticRunning = true;
            new SwingWorker<Path, Void>() {
                protected Path doInBackground() { return performBackup(true); }
                protected void done() {
                    automaticRunning = false;
                    try { get(); }
                    catch (Exception e) {
                        retryAfter = System.currentTimeMillis() + 3600000L;
                        Dialogs.showMessageDialog(null,
                            I18n.tr("Automatic backup failed") + ": " + e.getMessage(),
                            I18n.tr("Backup"), JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });
        automaticTimer.setInitialDelay(1000);
        automaticTimer.start();
    }
}
