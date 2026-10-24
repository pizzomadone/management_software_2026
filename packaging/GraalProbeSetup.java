import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CI-only initial user fixture. Compiled into build/graal-ci-setup-classes,
 * which is deliberately excluded from every distributed Native Image.
 */
public final class GraalProbeSetup {
    private GraalProbeSetup() { }

    public static void main(String[] args) throws Exception {
        // This utility runs only on disposable CI runners before native tracing.
        // Never overwrite even an existing CI company database.
        Path database = AppConstants.getDefaultDatabasePath();
        if (Files.exists(database)) {
            throw new IllegalStateException("Refusing to overwrite a company: " + database);
        }
        SettingsPanel.setGlobalSetting("language", "it");
        DatabaseManager.createEmptyDatabase(database, "EUR");
        WareStatFileHeader.seal(database);

        // Provide the required local acceptance evidence to noninteractive CI.
        // Production WareStat still requires the two real EULA checkboxes.
        EulaAcceptanceStore.recordAcceptance(
            LicenseText.getSoftwareLicense(), true, true);
        if (!EulaAcceptanceStore.isCurrentLicenseAccepted()) {
            throw new IllegalStateException("CI licence acceptance could not be verified");
        }
        System.out.println("Disposable CI company and EULA receipt ready");
    }
}
