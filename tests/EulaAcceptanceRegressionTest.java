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
