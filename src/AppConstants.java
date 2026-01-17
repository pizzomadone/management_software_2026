/*
 * Copyright © 2026 WareStat (www.warestat.com). All rights reserved.
 *
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or reverse engineering
 * is strictly prohibited.
 *
 * See LICENSE.txt for full terms.
 */

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
    public static final String SOFTWARE_NAME = "WareStat";

    /**
     * The version of the software.
     */
    public static final String VERSION = "1.2";

    /**
     * The year for copyright notice.
     */
    public static final String COPYRIGHT_YEAR = "2026";

    /**
     * Full application title with version.
     */
    public static final String FULL_TITLE = SOFTWARE_NAME + " v" + VERSION;

    /**
     * Copyright notice.
     */
    public static final String COPYRIGHT = "© " + COPYRIGHT_YEAR + " " + SOFTWARE_NAME;

    // License and Legal Information

    /**
     * Copyright holder (entity that owns the rights to this software).
     */
    public static final String COPYRIGHT_HOLDER = "WareStat";

    /**
     * Official website.
     */
    public static final String WEBSITE = "www.warestat.com";

    /**
     * Contact email for legal and licensing inquiries.
     */
    public static final String CONTACT_EMAIL = "info@warestat.com";

    /**
     * License type.
     */
    public static final String LICENSE_TYPE = "Proprietary Freeware";

    /**
     * Software version(s) to which the current license applies.
     * Example: "1.x" means all versions 1.0, 1.1, 1.2, etc.
     */
    public static final String LICENSE_VERSION_APPLIES_TO = "1.x";

    /**
     * Jurisdiction country for legal matters.
     */
    public static final String JURISDICTION_COUNTRY = "Italy";

    /**
     * Competent court for legal disputes.
     */
    public static final String JURISDICTION_COURT = "Tribunale di Caltanissetta";

    /**
     * Full copyright notice for display in UI and documents.
     */
    public static final String COPYRIGHT_FULL = COPYRIGHT + ". All rights reserved.";

    /**
     * Short license summary for display.
     */
    public static final String LICENSE_SUMMARY =
        "Free for personal and commercial use. " +
        "Modification, reverse engineering, and redistribution are prohibited.";

    // File and Directory Names

    /**
     * Sanitizes a string for use in filesystem paths.
     * Removes spaces and special characters that might cause issues.
     *
     * @param name The name to sanitize
     * @return A filesystem-safe version of the name
     */
    private static String sanitizeForFilesystem(String name) {
        // Remove spaces and keep only alphanumeric characters, hyphens, and underscores
        return name.replaceAll("[^a-zA-Z0-9-_]", "");
    }

    /**
     * The name of the application directory for user data.
     * This is automatically derived from SOFTWARE_NAME with spaces and special characters removed.
     * Used on macOS: ~/Library/Application Support/{APP_DIR_NAME}/
     * Used on Windows: %LOCALAPPDATA%\{APP_DIR_NAME}\
     * Used on Linux: ~/.{APP_DIR_NAME}/
     *
     * Examples:
     * - SOFTWARE_NAME = "WareStat" -> APP_DIR_NAME = "WareStat"
     * - SOFTWARE_NAME = "My Management Software" -> APP_DIR_NAME = "MyManagementSoftware"
     * - SOFTWARE_NAME = "Gestionale 2025!" -> APP_DIR_NAME = "Gestionale2025"
     */
    public static final String APP_DIR_NAME = sanitizeForFilesystem(SOFTWARE_NAME);

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
