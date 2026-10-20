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
            // Recovery-only settings must not initialize SQLite or expose
            // company fields, and currency changes must remain unavailable.
            Path defaultFile = AppConstants.getDefaultDatabasePath();
            boolean defaultExisted = Files.exists(defaultFile);
            SettingsPanel preferences = new SettingsPanel(true);
            javax.swing.JTabbedPane tabs = null;
            for (java.awt.Component component : preferences.getComponents()) {
                if (component instanceof javax.swing.JTabbedPane found) tabs = found;
            }
            check(tabs != null && tabs.getTabCount() == 1
                    && "Settings".equals(tabs.getTitleAt(0)),
                "Recovery settings must hide the database-dependent Company Data tab");
            java.lang.reflect.Field currencyField =
                SettingsPanel.class.getDeclaredField("currencyCombo");
            currencyField.setAccessible(true);
            javax.swing.JComboBox<?> currency =
                (javax.swing.JComboBox<?>) currencyField.get(preferences);
            check(currency != null && !currency.isEnabled(),
                "Currency change must be disabled without a company database");
            check(defaultExisted == Files.exists(defaultFile),
                "Opening recovery settings must not create a database");
            System.out.println("PASS: Recovery settings are database-independent");

            // The ordinary dashboard layout must remain visible in recovery
            // without ever attempting a SQLite connection or inventing KPI data.
            DashboardPanel dashboard = new DashboardPanel(null, true);
            check(dashboard.getComponentCount() > 0,
                "Dashboard must be visible in recovery");
            java.util.ArrayList<javax.swing.JButton> dashboardButtons =
                new java.util.ArrayList<>();
            collectButtons(dashboard, dashboardButtons);
            check(!dashboardButtons.isEmpty()
                    && dashboardButtons.stream().noneMatch(javax.swing.JButton::isEnabled),
                "Dashboard database actions and refresh must be disabled");
            dashboard.loadData();
            check(defaultExisted == Files.exists(defaultFile),
                "Recovery dashboard must not create a default database");
            System.out.println("PASS: Recovery dashboard renders without database access");

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

    private static void collectButtons(java.awt.Container container,
            java.util.List<javax.swing.JButton> result) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof javax.swing.JButton button) result.add(button);
            if (child instanceof java.awt.Container nested) collectButtons(nested, result);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
