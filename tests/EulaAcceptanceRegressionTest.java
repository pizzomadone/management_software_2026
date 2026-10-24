import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.prefs.Preferences;

/** Self-contained regression tests, no app data or production preferences are altered. */
public class EulaAcceptanceRegressionTest {
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        System.out.println("PASS: " + name);
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("warestat-eula-test-");
        Path evidence = root.resolve("evidence");
        Preferences prefs = Preferences.userRoot().node("/com/warestat/eula-test-" + UUID.randomUUID());
        String actualLicense = LicenseText.getSoftwareLicense();
        check(actualLicense.contains("6. EXCLUSION OF ALL LIABILITY"),
            "license retains broad general exclusion");
        check(actualLicense.contains("7. FALLBACK AGGREGATE LIABILITY CAP")
                && actualLicense.contains("EUR 1 (ONE EURO)")
                && !actualLicense.contains("EUR 5 (FIVE EUROS)"),
            "license specifies EUR 1 total fallback cap");
        check(actualLicense.contains("representative sample data")
                && actualLicense.contains("testing actual restoration"),
            "license requires sample testing and independent restoration checks");
        check(actualLicense.contains("reasonable, documented defense costs")
                && actualLicense.contains("No settlement imposing obligations"),
            "third-party indemnity is limited and requires settlement consent");
        String reviewedSections = LicenseText.getSpecificallyApprovedSections(actualLicense);
        for (String section : new String[] {
                "5. NO WARRANTIES", "6. EXCLUSION OF ALL LIABILITY",
                "7. FALLBACK AGGREGATE LIABILITY CAP",
                "15. UNLAWFUL USE AND THIRD-PARTY CLAIMS",
                "16. TERMINATION", "18. GOVERNING LAW AND COURTS"}) {
            check(reviewedSections.contains(section), "specific review includes " + section);
        }
        check(!reviewedSections.contains("8. USER VERIFICATION AND RESPONSIBILITY")
                && !reviewedSections.contains("19. ENTIRE AGREEMENT"),
            "specific review omits unlisted sections");
        check(actualLicense.contains("NOTHING IN THIS AGREEMENT EXCLUDES OR LIMITS LIABILITY"),
            "mandatory law protections preserved");
        check("5,6,7,15,16,18".equals(EulaAcceptanceStore.SPECIFIC_CLAUSES),
            "specific approval section references updated");
        String text = "WareStat license terms for this regression test";
        try {
            check(!EulaAcceptanceStore.isAccepted(prefs, evidence, "1.3", text), "first launch");
            for (boolean[] selection : new boolean[][] {{false,false},{true,false},{false,true}}) {
                try {
                    EulaAcceptanceStore.saveAcceptance(prefs, evidence, "1.3", text, selection[0], selection[1]);
                    throw new AssertionError("partial selection was accepted");
                } catch (IOException expected) { }
            }
            check(!Files.exists(evidence), "one or zero checkboxes: no saved evidence");
            EulaAcceptanceStore.saveAcceptance(prefs, evidence, "1.3", text, true, true);
            check(EulaAcceptanceStore.isAccepted(prefs, evidence, "1.3", text), "both boxes: acceptance persisted");
            check(Instant.parse(prefs.get("acceptedAtUtc", "")).getEpochSecond() > 0, "UTC date saved");
            check(!EulaAcceptanceStore.isAccepted(prefs, evidence, "1.4", text), "software update reacceptance");
            EulaAcceptanceStore.saveAcceptance(prefs, evidence, "1.4", text, true, true);
            check(EulaAcceptanceStore.isAccepted(prefs, evidence, "1.4", text), "updated version accepted");
            check(!EulaAcceptanceStore.isAccepted(prefs, evidence, "1.4", text + " changed"), "EULA text change reacceptance");
            String digest = prefs.get("licenseSha256", "");
            Files.delete(evidence.resolve("acceptance-v1.4-" + digest + ".properties"));
            check(!EulaAcceptanceStore.isAccepted(prefs, evidence, "1.4", text), "missing receipt reacceptance");
            EulaAcceptanceStore.saveAcceptance(prefs, evidence, "1.4", text, true, true);
            Files.writeString(evidence.resolve("license-" + digest + ".txt"), "corrupted");
            check(!EulaAcceptanceStore.isAccepted(prefs, evidence, "1.4", text), "corrupt license reacceptance");
            EulaAcceptanceStore.saveAcceptance(prefs, evidence, "1.4", text, true, true);
            check(EulaAcceptanceStore.isAccepted(prefs, evidence, "1.4", text), "resave after corruption");
            prefs.put("specificApproval", "false");
            check(!EulaAcceptanceStore.isAccepted(prefs, evidence, "1.4", text), "corrupt preferences reacceptance");
            Path blocked = root.resolve("not-a-directory");
            Files.writeString(blocked, "file");
            try {
                EulaAcceptanceStore.saveAcceptance(prefs, blocked, "1.5", text, true, true);
                throw new AssertionError("unwritable evidence path accepted");
            } catch (IOException expected) { }
            check(!EulaAcceptanceStore.isAccepted(prefs, evidence, "1.5", text), "failed write blocks new version");
            try (var stream = Files.list(evidence)) {
                check(stream.anyMatch(p -> p.getFileName().toString().startsWith("acceptance-v1.3-")),
                      "old evidence preserved");
            }
        } finally {
            prefs.removeNode();
            prefs.flush();
            try (var stream = Files.walk(root)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                });
            }
        }
    }
}
