import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.swing.*;

/**
 * Current data and backups use signed .wsbq; SQLite .db backups from the
 * actual WareStat 1.2 release are converted to .wsbq, never opened as live data.
 */
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
        Path file=AppConstants.getBackupConfigPath();
        if(Files.exists(file)) {
            try(Reader r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){config.load(r);}
            catch(IOException|IllegalArgumentException e){throw new IllegalStateException("Unable to read backup settings",e);}
        }
    }
    public static synchronized BackupManager getInstance() {
        if(instance==null)instance=new BackupManager();
        return instance;
    }
    private synchronized void saveConfig() {
        Path path=AppConstants.getBackupConfigPath();
        Path tmp=null;
        try{
            Files.createDirectories(path.getParent());
            tmp=Files.createTempFile(path.getParent(),"backup-settings-",".tmp");
            try(Writer w=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)){config.store(w,null);}
            moveReplace(tmp,path);
        }catch(IOException e){throw new IllegalStateException("Unable to save backup settings",e);}
        finally{removeTemporary(tmp);}
    }
    private static boolean sameFile(Path a,Path b) throws IOException {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize())||
            (Files.exists(a)&&Files.exists(b)&&Files.isSameFile(a,b));
    }
    private static void rejectActive(Path path) throws IOException {
        if(sameFile(path,AppConstants.getDatabasePath()))
            throw new IllegalArgumentException("The selected file is the active database");
    }
    private static void removeTemporary(Path path) {
        if(path==null)return;
        try{
            if(Files.isDirectory(path))deleteDirectory(path);
            else Files.deleteIfExists(path);
        }catch(IOException ignored){}
    }
    private static void moveReplace(Path source,Path destination) throws IOException {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        try{Files.move(source,destination,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException ignored){
            Files.move(source,destination,StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private static void clearSidecars(Path file) throws IOException {
        Path wal=Path.of(file+"-wal"),journal=Path.of(file+"-journal");
        // Never discard committed WAL frames or a rollback journal.
        if ((Files.exists(wal)&&Files.size(wal)>0)||Files.exists(journal))
            throw new IOException("SQLite has uncheckpointed changes; refusing to replace "+file);
        Files.deleteIfExists(wal);
        Files.deleteIfExists(Path.of(file+"-shm"));
    }

    /**
     * Build a consistent SQLite snapshot. EXCLUSIVE prevents a second client
     * opening the live file: VACUUM INTO must therefore use its owner connection.
     * For a sealed external .wsbq, unseal only a private temporary copy.
     */
    private Path snapshot(Path source,Path directory,String legacyCurrency,boolean excludePhotos)
            throws IOException,SQLException {
        if(!Files.isRegularFile(source)||!Files.isReadable(source))
            throw new IOException("Database file not found or not readable");
        Files.createDirectories(directory);
        Path staging=Files.createTempFile(directory,"warestat-snapshot-",".wsbq");
        Files.delete(staging);
        Path readableCopy=null;
        try{
            boolean live=sameFile(source,AppConstants.getDatabasePath());
            Connection owner=live?DatabaseManager.getInstance().liveConnectionIfOpen():null;
            if(live&&owner==null)
                throw new IOException("The live database is closing; backup postponed");
            if(owner!=null){
                // Keep the existing EXCLUSIVE owner alive; never close it.
                synchronized(owner){ vacuumInto(owner,staging,legacyCurrency); }
            }else{
                Path readable=source;
                if(!WareStatFileHeader.isSQLite(source)){
                    // Never modify an imported file, even when recovering a
                    // partially written WareStat signature.
                    readableCopy=WareStatFileHeader.copyReadable(source,directory);
                    readable=readableCopy;
                }
                org.sqlite.SQLiteConfig settings=new org.sqlite.SQLiteConfig();
                settings.setReadOnly(true);
                settings.setBusyTimeout(10000);
                try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+readable.toAbsolutePath(),
                        settings.toProperties())){
                    if(WareStatFileHeader.isSealed(source))
                        validateCurrentBackupVersion(c);
                    vacuumInto(c,staging,legacyCurrency);
                }
            }
            try(Connection c=DatabaseManager.openConnection("jdbc:sqlite:"+staging)){
                if(legacyCurrency!=null)DatabaseCurrency.initialize(c,legacyCurrency);
                DatabaseManager.prepareDatabase(c);
                if(excludePhotos){
                    try(Statement st=c.createStatement()){
                        st.executeUpdate("DELETE FROM product_images");
                        st.execute("VACUUM");
                    }
                }
                // Legacy archives still refer to external photos here; their
                // BLOBs are imported and checked by prepareInput afterwards.
                DatabaseIntegrity.validate(c,false);
            }
            return staging;
        }catch(IOException|SQLException|RuntimeException e){
            removeTemporary(staging);
            throw e;
        }finally{
            removeTemporary(readableCopy);
        }
    }
    /** A signed current-format backup must already contain the complete
     * current schema: never silently migrate an obsolete SQLite database that
     * has merely been renamed/resealed with a .wsbq extension. */
    private static void validateCurrentBackupVersion(Connection c) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(
                "SELECT version FROM schema_migrations ORDER BY version DESC LIMIT 1");
            ResultSet r=p.executeQuery()){
            if(!r.next()||!"022".equals(r.getString(1)))
                throw new SQLException("Incompatible WareStat .wsbq schema version");
        }
        DatabaseIntegrity.validate(c,false);
        ProductImageStore.validateEmbeddedImages(c);
    }

    private static void vacuumInto(Connection c,Path staging,String legacyCurrency)
            throws SQLException {
        if(DatabaseCurrency.read(c)==null&&legacyCurrency==null)
            throw new SQLException("Original currency is required to import a legacy database");
        try(PreparedStatement p=c.prepareStatement("VACUUM INTO ?")){
            p.setString(1,staging.toAbsolutePath().toString());
            p.execute();
        }
    }
    private Path snapshot(Path source,Path directory) throws IOException,SQLException {
        return snapshot(source,directory,null,false);
    }

    /** A WareStat 1.2 backup is a SQLite .db file; only the 1.2
     * schema is accepted. Current WareStat backups must carry the signed
     * WSBQ header. The original source is never changed. */
    private Path prepareInput(Path source, Path folder, String legacyCurrency)
            throws IOException, SQLException {
        if(source==null||!Files.isRegularFile(source))
            throw new IOException("Backup file not found");
        boolean legacy=source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".db");
        if(!legacy&&!isArchive(source))
            throw new IOException("Only WareStat 1.2 .db and current .wsbq backups are accepted");
        if(legacy) WareStatLegacy12.validate(source);
        else if(!WareStatFileHeader.isSealed(source))
            throw new IOException("Invalid WareStat .wsbq signature");
        Path result=null;
        try {
            result=snapshot(source,folder,legacy?legacyCurrency:null,false);
            try(Connection c=DatabaseManager.openConnection("jdbc:sqlite:"+result)) {
                ProductImageStore.validateEmbeddedImages(c);
                DatabaseIntegrity.validate(c,false);
                try(Statement st=c.createStatement();ResultSet r=st.executeQuery("PRAGMA integrity_check")) {
                    if(!r.next()||!"ok".equalsIgnoreCase(r.getString(1)))
                        throw new SQLException("Imported database failed integrity_check");
                }
            }
            return result;
        } catch (IOException|SQLException|RuntimeException e) {
            removeTemporary(result);
            throw e;
        }
    }

    /** Only current-format database exports and archives use .wsbq. */
    public static boolean isArchive(Path file) {
        return file!=null&&file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wsbq");
    }

    public synchronized Path performBackup(){return performBackup(true);}
    public synchronized Path performDatabaseOnlyBackup(){return performBackup(false);}
    public synchronized Path performBackup(boolean includePhotos) {
        Path staged=null;
        try{
            Path folder=Path.of(getBackupDirectory()).toAbsolutePath();
            Files.createDirectories(folder);
            staged=snapshot(AppConstants.getDatabasePath(),folder,null,!includePhotos);
            try(Connection c=DatabaseManager.openConnection("jdbc:sqlite:"+staged)){ProductImageStore.validateEmbeddedImages(c);}
            Path target=folder.resolve("WareStat-Backup-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".wsbq");
            WareStatFileHeader.seal(staged);
            moveReplace(staged,target);
            staged=null;
            registerBackup(target);
            config.setProperty("backup.last_success",Long.toString(System.currentTimeMillis()));
            saveConfig();
            cleanOldBackups();
            DatabaseManager.reclaimUnusedPages();
            return target;
        }catch(IOException|SQLException e){throw new IllegalStateException("Error during backup: "+e.getMessage(),e);}
        finally{removeTemporary(staged);}
    }

    public synchronized Path exportDatabase(Path destination){return exportDatabase(destination,true);}
    public synchronized Path exportDatabase(Path destination,boolean includePhotos) {
        Path staged=null;
        try{
            Path target=destination.toAbsolutePath().normalize();
            rejectActive(target);
            if(!isArchive(target))throw new IOException("The export filename must end in .wsbq");
            Files.createDirectories(target.getParent());
            staged=snapshot(AppConstants.getDatabasePath(),target.getParent(),null,!includePhotos);
            validateInternalSnapshot(staged);
            WareStatFileHeader.seal(staged);
            moveReplace(staged,target);
            staged=null;
            return target;
        }catch(IOException|SQLException e){throw new IllegalStateException("Unable to export database: "+e.getMessage(),e);}
        finally{removeTemporary(staged);}
    }

    /**
     * Archive the current company as a usable SQLite .wsbq and start a new one.
     * Retain a safety snapshot until the replacement has been validated.
     */
    public synchronized Path archiveAndStartNew(Path destination) {
        Path archiveTmp=null,empty=null,safety=null;
        Path target=destination==null?null:destination.toAbsolutePath().normalize();
        Path current=AppConstants.getDatabasePath().toAbsolutePath().normalize();
        boolean replaced=false;
        try{
            if(target==null||target.getParent()==null||!isArchive(target))
                throw new IOException("The new archive filename must end in .wsbq");
            rejectActive(target);
            Files.createDirectories(target.getParent());
            archiveTmp=snapshot(current,target.getParent());
            validateInternalSnapshot(archiveTmp);
            WareStatFileHeader.seal(archiveTmp);
            moveReplace(archiveTmp,target);
            archiveTmp=null;
            safety=snapshot(current,current.getParent());

            String currency=DatabaseCurrency.boundCode();
            if(currency==null||currency.isBlank())currency=SettingsPanel.getCurrencyCode();
            empty=Files.createTempFile(current.getParent(),"warestat-empty-",".wsbq");
            DatabaseManager.createEmptyDatabase(empty,currency);
            DatabaseManager.getInstance().closeConnection();
            clearSidecars(current);
            moveReplace(empty,current);
            empty=null;
            replaced=true;
            DatabaseManager.getInstance().initDatabase();
            CompanyData.getInstance().reloadFromDatabase();
            ProductImageStore.getInstance().removeOrphanFiles();
            DatabaseIntegrity.validate(DatabaseManager.getInstance().getConnection(),false);
            registerBackup(target,false);
            config.setProperty("archive.last_directory",target.getParent().toString());
            saveConfig();
            return target;
        }catch(Exception e){
            if(replaced&&safety!=null){
                try{restoreSafety(current,safety);}catch(Exception rollback){e.addSuppressed(rollback);}
            }else if(!replaced){
                try{DatabaseManager.getInstance().initDatabase();}catch(Exception ignored){}
            }
            throw new IllegalStateException("Unable to create new archive: "+e.getMessage(),e);
        }finally{
            removeTemporary(archiveTmp);removeTemporary(empty);removeTemporary(safety);
        }
    }

    public synchronized BackupRecord importBackup(Path file){return importBackup(file,null);}
    public synchronized BackupRecord importBackup(Path file,String legacyCurrency) {
        Path staged=null;
        try{
            rejectActive(file);
            Path folder=Path.of(getBackupDirectory()).toAbsolutePath();
            Files.createDirectories(folder);
            staged=prepareInput(file,folder,legacyCurrency);
            Path target=folder.resolve("imported_"+UUID.randomUUID()+".wsbq");
            WareStatFileHeader.seal(staged);
            moveReplace(staged,target);
            staged=null;
            return registerBackup(target);
        }catch(IOException|SQLException e){throw new IllegalArgumentException("Unable to import backup: "+e.getMessage(),e);}
        finally{removeTemporary(staged);}
    }

    public synchronized void importDatabase(Path source){importDatabase(source,null);}
    public synchronized void importDatabase(Path source,String legacyCurrency) {
        Path current=AppConstants.getDatabasePath().toAbsolutePath().normalize();
        Path staging=null,safety=null;
        boolean replaced=false,stoppedLive=false;
        try{
            rejectActive(source);
            staging=prepareInput(source,current.getParent(),legacyCurrency);
            if(Files.exists(current)){
                safety=snapshot(current,current.getParent());
                Path retained=current.resolveSibling("data_before_restore_"+UUID.randomUUID()+".wsbq");
                WareStatFileHeader.seal(safety);
                moveReplace(safety,retained);
                safety=retained;
            }
            DatabaseManager.getInstance().closeConnection();
            stoppedLive=true;
            clearSidecars(current);
            moveReplace(staging,current);
            staging=null;
            replaced=true;
            DatabaseManager.getInstance().initDatabase();
            CompanyData.getInstance().reloadFromDatabase();
            ProductImageStore.getInstance().removeOrphanFiles();
        }catch(Exception e){
            if(replaced&&safety!=null){
                try{restoreSafety(current,safety);}catch(Exception recovery){e.addSuppressed(recovery);}
            }else if(!replaced&&stoppedLive){
                // A rejected input never closed or changed the live connection:
                // do not needlessly reopen SQLite and invalidate callers.
                try{DatabaseManager.getInstance().initDatabase();}catch(Exception ignored){}
            }
            throw new IllegalStateException("Unable to import database: "+e.getMessage()+
                (safety==null?"":". Previous database retained at "+safety),e);
        }finally{removeTemporary(staging);}
    }
    private void restoreSafety(Path current,Path safety) throws IOException,SQLException {
        Path rollback=snapshot(safety,current.getParent());
        try{
            DatabaseManager.getInstance().closeConnection();
            clearSidecars(current);
            moveReplace(rollback,current);
            DatabaseManager.getInstance().initDatabase();
            CompanyData.getInstance().reloadFromDatabase();
            ProductImageStore.getInstance().removeOrphanFiles();
        }finally{removeTemporary(rollback);}
    }

    public void restoreBackup(String file){restoreBackup(file,null);}
    public synchronized CostIntegrity.Audit restoreBackup(String file,String legacyCurrency){
        importDatabase(Path.of(file),legacyCurrency);
        try{return CostIntegrity.audit(DatabaseManager.getInstance().getConnection());}
        catch(SQLException e){throw new IllegalStateException("Database restored but cost completeness could not be checked: "+e.getMessage(),e);}
    }

    /** Validates our own unsealed staging file, never a file chosen for import. */
    private static void validateInternalSnapshot(Path staged) throws SQLException {
        try(Connection c=DatabaseManager.openConnection("jdbc:sqlite:"+staged)){
            DatabaseIntegrity.validate(c,false);
            ProductImageStore.validateEmbeddedImages(c);
        }
    }

    private void validateBackup(Path path) {
        Path prepared=null;
        try{
            prepared=prepareInput(path,AppConstants.getAppDataDirectory(),null);
            try(Connection c=DatabaseManager.openConnection("jdbc:sqlite:"+prepared)){ProductImageStore.validateEmbeddedImages(c);}
        }catch(IOException|SQLException e){throw new IllegalArgumentException("Invalid WareStat backup: "+e.getMessage(),e);}
        finally{removeTemporary(prepared);}
    }
    public void validateDatabase(Path path){validateBackup(path);}

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
