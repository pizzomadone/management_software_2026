import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import java.util.Properties;

public class BackupManager {
    private static BackupManager instance;
    private Properties config;
    private static final String CONFIG_FILE = "backup.properties";
    private static final String DEFAULT_BACKUP_DIR = "backups";
    
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
            File configFile = new File(CONFIG_FILE);
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    config.load(fis);
                }
            } else {
                // Default settings
                config.setProperty("backup.directory", DEFAULT_BACKUP_DIR);
                config.setProperty("backup.autobackup", "true");
                config.setProperty("backup.retention", "7");
                saveConfig();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            config.store(fos, "Backup Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void performBackup() {
        try {
            String backupDir = config.getProperty("backup.directory", DEFAULT_BACKUP_DIR);
            Files.createDirectories(Paths.get(backupDir));
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String backupFileName = "gestionale_" + sdf.format(new Date()) + ".db";
            String backupPath = Paths.get(backupDir, backupFileName).toString();
            
            // FIXED: Check if source database exists
            Path sourceDb = Paths.get("gestionale.db");
            if (!Files.exists(sourceDb)) {
                throw new IOException("Database file 'gestionale.db' not found");
            }
            
            Files.copy(sourceDb, Paths.get(backupPath), StandardCopyOption.REPLACE_EXISTING);
            cleanOldBackups();
            
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error during backup: " + e.getMessage());
        }
    }
    
    private void cleanOldBackups() {
        try {
            String backupDir = config.getProperty("backup.directory", DEFAULT_BACKUP_DIR);
            int retentionDays = Integer.parseInt(config.getProperty("backup.retention", "7"));
            
            File dir = new File(backupDir);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.endsWith(".db"));
                if (files != null) {
                    long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
                    for (File file : files) {
                        if (file.lastModified() < cutoffTime) {
                            // FIXED: Check deletion success
                            if (!file.delete()) {
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
    
    public void restoreBackup(String backupFile) {
        try {
            if (!Files.exists(Paths.get(backupFile))) {
                throw new FileNotFoundException("Backup file not found: " + backupFile);
            }
            
            // FIXED: Close connection safely
            try {
                DatabaseManager.getInstance().closeConnection();
            } catch (Exception e) {
                System.err.println("Warning: Could not close database connection: " + e.getMessage());
            }
            
            String currentBackup = "gestionale_pre_restore_" + 
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".db";
                
            // FIXED: Check if current database exists
            Path currentDb = Paths.get("gestionale.db");
            if (Files.exists(currentDb)) {
                Files.copy(currentDb, Paths.get(currentBackup), StandardCopyOption.REPLACE_EXISTING);
            }
            
            Files.copy(Paths.get(backupFile), Paths.get("gestionale.db"), StandardCopyOption.REPLACE_EXISTING);
            
            // FIXED: Handle reconnection failure
            try {
                DatabaseManager.getInstance().initDatabase();
            } catch (Exception e) {
                // Restore previous backup if reconnection fails
                if (Files.exists(Paths.get(currentBackup))) {
                    Files.copy(Paths.get(currentBackup), Paths.get("gestionale.db"), StandardCopyOption.REPLACE_EXISTING);
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
        return config.getProperty("backup.directory", DEFAULT_BACKUP_DIR);
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
    
    public File[] listBackups() {
        File dir = new File(getBackupDirectory());
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".db"));
            return files != null ? files : new File[0];
        }
        return new File[0];
    }
}