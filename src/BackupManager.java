import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import java.util.Properties;

public class BackupManager {
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
                try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
                    config.load(fis);
                }
            } else {
                // Default settings
                config.setProperty("backup.directory", AppConstants.getBackupDirectory().toString());
                config.setProperty("backup.autobackup", "true");
                config.setProperty("backup.retention", "7");
                saveConfig();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(AppConstants.getBackupConfigPath().toFile())) {
            config.store(fos, "Backup Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void performBackup() {
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

            Files.copy(sourceDb, Paths.get(backupPath), StandardCopyOption.REPLACE_EXISTING);
            cleanOldBackups();

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error during backup: " + e.getMessage());
        }
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
    
    public File[] listBackups() {
        File dir = new File(getBackupDirectory());
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".db"));
            return files != null ? files : new File[0];
        }
        return new File[0];
    }
}