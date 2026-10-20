import java.nio.file.Files;
import java.nio.file.Path;

/** Headless checks for the missing-company startup decision. */
public final class StartupDatabaseRecoveryRegressionTest {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("warestat-startup-recovery-");
        Path missing = temp.resolve("moved.wsbq");
        Path directory = temp.resolve("folder.wsbq");
        try {
            check(MainWindow.needsStartupDatabaseRecovery(missing),
                "Missing custom database must offer recovery");
            check(!MainWindow.needsStartupDatabaseRecovery(AppConstants.getDefaultDatabasePath()),
                "Missing default database must keep first-install behavior");

            Files.createFile(missing);
            check(!MainWindow.needsStartupDatabaseRecovery(missing),
                "Existing custom database must retain normal startup");

            Files.createDirectory(directory);
            check(MainWindow.needsStartupDatabaseRecovery(directory),
                "Directory must not be accepted as a database");

            Files.delete(missing);
            check(MainWindow.needsStartupDatabaseRecovery(missing),
                "Removed database must offer recovery again");
            System.out.println("PASS: Missing database startup recovery selection");
        } finally {
            Files.deleteIfExists(missing);
            Files.deleteIfExists(directory);
            Files.deleteIfExists(temp);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
