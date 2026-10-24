/*
 * Copyright © 2026 WareStat (www.warestat.com). All rights reserved.
 * See LICENSE.txt for full terms.
 */

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Persists EULA acceptance per OS user in Java's native Preferences backend.
 * The exact accepted license and a matching receipt are kept outside company databases.
 * Acceptance is required again after any application-version or license-text change.
 */
public final class EulaAcceptanceStore {
    private static final String PREFERENCES_NODE = "/com/warestat/eula";
    public static final String SPECIFIC_CLAUSES = "5,6,7,10,11,14,15,16,18";

    private EulaAcceptanceStore() { }

    /** A legacy eula_accepted.txt file is intentionally NOT evidence of current acceptance. */
    public static boolean isCurrentLicenseAccepted() {
        try {
            return isAccepted(Preferences.userRoot().node(PREFERENCES_NODE),
                              evidenceDirectory(), AppConstants.VERSION,
                              LicenseText.getSoftwareLicense());
        } catch (SecurityException | IllegalStateException error) {
            System.err.println("EULA acceptance unavailable: " + error.getMessage());
            return false;
        }
    }

    static boolean isAccepted(Preferences prefs, Path directory,
                              String version, String license) {
        try {
            String hash = sha256(license);
            if (!version.equals(prefs.get("softwareVersion", ""))
                    || !AppConstants.EULA_ID.equals(prefs.get("licenseId", ""))
                    || !hash.equals(prefs.get("licenseSha256", ""))
                    || !prefs.getBoolean("generalAcceptance", false)
                    || !prefs.getBoolean("specificApproval", false)
                    || !SPECIFIC_CLAUSES.equals(prefs.get("specificClauses", ""))) {
                return false;
            }

            String acceptedAt = prefs.get("acceptedAtUtc", "");
            String receiptId = prefs.get("receiptId", "");
            if (receiptId.isBlank()) return false;
            Instant.parse(acceptedAt);

            Path licenseCopy = directory.resolve("license-" + hash + ".txt");
            Path receiptFile = directory.resolve(receiptFileName(version, hash));
            if (!license.equals(Files.readString(licenseCopy, StandardCharsets.UTF_8))) return false;

            Properties receipt = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(receiptFile, StandardCharsets.UTF_8)) {
                receipt.load(reader);
            }
            return "1".equals(receipt.getProperty("schemaVersion"))
                && AppConstants.SOFTWARE_NAME.equals(receipt.getProperty("software"))
                && version.equals(receipt.getProperty("softwareVersion"))
                && AppConstants.EULA_ID.equals(receipt.getProperty("licenseId"))
                && hash.equals(receipt.getProperty("licenseSha256"))
                && acceptedAt.equals(receipt.getProperty("acceptedAtUtc"))
                && receiptId.equals(receipt.getProperty("receiptId"))
                && "true".equals(receipt.getProperty("generalAcceptance"))
                && "true".equals(receipt.getProperty("specificApproval"))
                && SPECIFIC_CLAUSES.equals(receipt.getProperty("specificClauses"));
        } catch (IOException | SecurityException | IllegalStateException | DateTimeParseException error) {
            // Corrupt, missing, or inaccessible evidence means re-acceptance, never bypass.
            System.err.println("EULA acceptance unavailable: " + error.getMessage());
            return false;
        }
    }

    /** Only call with the unmodified text displayed to the user and both boxes selected. */
    public static void recordAcceptance(String displayedLicenseText,
                                        boolean generalAcceptance,
                                        boolean specificApproval) throws IOException {
        if (displayedLicenseText == null
                || !displayedLicenseText.equals(LicenseText.getSoftwareLicense())) {
            throw new IOException("The displayed license differs from the current license.");
        }
        try {
            saveAcceptance(Preferences.userRoot().node(PREFERENCES_NODE),
                           evidenceDirectory(), AppConstants.VERSION,
                           displayedLicenseText, generalAcceptance, specificApproval);
        } catch (SecurityException | IllegalStateException error) {
            throw new IOException("Cannot access EULA system preferences.", error);
        }
    }

    static void saveAcceptance(Preferences prefs, Path directory, String version,
                               String displayedLicenseText, boolean generalAcceptance,
                               boolean specificApproval) throws IOException {
        if (!generalAcceptance || !specificApproval) {
            throw new IOException("Both EULA confirmations are required.");
        }
        if (displayedLicenseText == null) {
            throw new IOException("The displayed license is missing.");
        }

        String hash = sha256(displayedLicenseText);
        String acceptedAt = Instant.now().toString();
        String receiptId = UUID.randomUUID().toString();
        Files.createDirectories(directory);  // Must fail if the canonical app directory is not writable.

        Properties receipt = new Properties();
        receipt.setProperty("schemaVersion", "1");
        receipt.setProperty("software", AppConstants.SOFTWARE_NAME);
        receipt.setProperty("softwareVersion", version);
        receipt.setProperty("licenseId", AppConstants.EULA_ID);
        receipt.setProperty("licenseSha256", hash);
        receipt.setProperty("acceptedAtUtc", acceptedAt);
        receipt.setProperty("receiptId", receiptId);
        receipt.setProperty("generalAcceptance", "true");
        receipt.setProperty("specificApproval", "true");
        receipt.setProperty("specificClauses", SPECIFIC_CLAUSES);

        ByteArrayOutputStream receiptBytes = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(receiptBytes, StandardCharsets.UTF_8)) {
            receipt.store(writer, "WareStat local EULA acceptance receipt");
        }

        // Write both complete evidence files before marking acceptance in Preferences.
        writeAtomically(directory.resolve("license-" + hash + ".txt"),
                        displayedLicenseText.getBytes(StandardCharsets.UTF_8));
        writeAtomically(directory.resolve(receiptFileName(version, hash)), receiptBytes.toByteArray());

        try {
            prefs.put("softwareVersion", version);
            prefs.put("licenseId", AppConstants.EULA_ID);
            prefs.put("licenseSha256", hash);
            prefs.put("acceptedAtUtc", acceptedAt);
            prefs.put("receiptId", receiptId);
            prefs.putBoolean("generalAcceptance", true);
            prefs.putBoolean("specificApproval", true);
            prefs.put("specificClauses", SPECIFIC_CLAUSES);
            prefs.flush();
        } catch (BackingStoreException | SecurityException | IllegalStateException error) {
            throw new IOException("Cannot save EULA acceptance in system preferences.", error);
        }

        if (!isAccepted(prefs, directory, version, displayedLicenseText)) {
            throw new IOException("Could not verify the saved EULA acceptance.");
        }
    }

    private static Path evidenceDirectory() {
        // Deliberately bypass getAppDataDirectory()'s legacy home-directory fallback.
        return AppConstants.getAppDataDirectoryLocation().resolve("eula_acceptance");
    }

    private static String receiptFileName(String version, String hash) {
        String safeVersion = version.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "acceptance-v" + safeVersion + "-" + hash + ".properties";
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), ".warestat-eula-", ".tmp");
        try {
            Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                           StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
