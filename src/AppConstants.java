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

    // Private constructor to prevent instantiation
    private AppConstants() {
        throw new AssertionError("AppConstants class cannot be instantiated");
    }
}
