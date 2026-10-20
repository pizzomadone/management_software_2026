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
            check(MainWindow.needsStartupDatabaseRecovery(AppConstants.getDefaultDatabasePath()),
                "Missing default database must open the recovery shell");

            Files.createFile(missing);
            check(!MainWindow.needsStartupDatabaseRecovery(missing),
                "Existing custom database must retain normal startup");

            Files.createDirectory(directory);
            check(MainWindow.needsStartupDatabaseRecovery(directory),
                "Directory must not be accepted as a database");

            Files.delete(missing);
            check(MainWindow.needsStartupDatabaseRecovery(missing),
                "Removed database must offer recovery again");
            // Restoring must create a new company, leave the original backup
            // byte-for-byte unchanged, and reject overwriting existing files.
            Path originalBackup = temp.resolve("backup.wsbq");
            Path restored = temp.resolve("restored.wsbq");
            try {
                DatabaseManager.createEmptyDatabase(originalBackup, "EUR");
                WareStatFileHeader.seal(originalBackup);
                byte[] bytes = Files.readAllBytes(originalBackup);
                BackupManager.getInstance().restoreBackupAsNewDatabase(
                    originalBackup, restored, null);
                check(Files.isRegularFile(restored)
                        && WareStatFileHeader.isSealed(restored),
                    "Restored company must be a signed WSBQ");
                check(java.util.Arrays.equals(bytes, Files.readAllBytes(originalBackup)),
                    "Recovery must not modify the source backup");
                boolean overwriteRefused = false;
                try {
                    BackupManager.getInstance().restoreBackupAsNewDatabase(
                        originalBackup, restored, null);
                } catch (java.nio.file.FileAlreadyExistsException expected) {
                    overwriteRefused = true;
                }
                check(overwriteRefused, "Recovery must refuse to overwrite a company");
            } finally {
                Files.deleteIfExists(originalBackup);
                Files.deleteIfExists(restored);
            }
            System.out.println("PASS: Missing database startup recovery selection and safe backup restore");
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
