import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application-wide constants for the management software.
 * This class contains all constants that define the application's identity and configuration.
 */
public class AppConstants {

    /**
     * The name of the software application.
     * Change this value to rebrand the entire application.
     */
    public static final String SOFTWARE_NAME = "WorkGenio";

    /**
     * The version of the software.
     */
    public static final String VERSION = "1.0";

    /**
     * The year for copyright notice.
     */
    public static final String COPYRIGHT_YEAR = "2025";

    /**
     * Full application title with version.
     */
    public static final String FULL_TITLE = SOFTWARE_NAME + " v" + VERSION;

    /**
     * Copyright notice.
     */
    public static final String COPYRIGHT = "© " + COPYRIGHT_YEAR + " " + SOFTWARE_NAME;

    // File and Directory Names

    /**
     * The name of the application directory for user data.
     * Used on macOS: ~/Library/Application Support/{APP_DIR_NAME}/
     * Used on Windows: %LOCALAPPDATA%\{APP_DIR_NAME}\
     * Used on Linux: ~/.{APP_DIR_NAME}/
     */
    public static final String APP_DIR_NAME = SOFTWARE_NAME;

    /**
     * The name of the database file.
     */
    public static final String DATABASE_FILE_NAME = "data.db";

    /**
     * The name of the backup configuration file.
     */
    public static final String BACKUP_CONFIG_FILE_NAME = "backup.properties";

    /**
     * The name of the application settings file.
     */
    public static final String SETTINGS_FILE_NAME = "app_settings.properties";

    /**
     * The default backup directory name (relative to app data directory).
     */
    public static final String BACKUP_DIR_NAME = "backups";

    /**
     * Gets the application data directory path for the current platform.
     * Creates the directory if it doesn't exist.
     *
     * @return The path to the application data directory
     */
    public static Path getAppDataDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        Path appDir;

        if (os.contains("mac")) {
            // macOS: Use Application Support directory
            appDir = Paths.get(userHome, "Library", "Application Support", APP_DIR_NAME);
        } else if (os.contains("win")) {
            // Windows: Use AppData/Local
            appDir = Paths.get(userHome, "AppData", "Local", APP_DIR_NAME);
        } else {
            // Linux/Unix: Use hidden directory
            appDir = Paths.get(userHome, "." + APP_DIR_NAME.toLowerCase());
        }

        // Create the directory if it doesn't exist
        try {
            if (!Files.exists(appDir)) {
                Files.createDirectories(appDir);
                System.out.println("Created application data directory: " + appDir);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not create application data directory: " + e.getMessage());
            // Fallback to user home directory
            appDir = Paths.get(userHome);
        }

        return appDir;
    }

    /**
     * Gets the full path to the database file.
     *
     * @return The path to the database file
     */
    public static Path getDatabasePath() {
        return getAppDataDirectory().resolve(DATABASE_FILE_NAME);
    }

    /**
     * Gets the full path to the backup configuration file.
     *
     * @return The path to the backup configuration file
     */
    public static Path getBackupConfigPath() {
        return getAppDataDirectory().resolve(BACKUP_CONFIG_FILE_NAME);
    }

    /**
     * Gets the full path to the application settings file.
     *
     * @return The path to the settings file
     */
    public static Path getSettingsPath() {
        return getAppDataDirectory().resolve(SETTINGS_FILE_NAME);
    }

    /**
     * Gets the full path to the backup directory.
     * Creates the directory if it doesn't exist.
     *
     * @return The path to the backup directory
     */
    public static Path getBackupDirectory() {
        Path backupDir = getAppDataDirectory().resolve(BACKUP_DIR_NAME);
        try {
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not create backup directory: " + e.getMessage());
        }
        return backupDir;
    }

    // Private constructor to prevent instantiation
    private AppConstants() {
        throw new AssertionError("AppConstants class cannot be instantiated");
    }
}
