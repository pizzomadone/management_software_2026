import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.Currency;
import java.util.Locale;

/**
 * Explicit, in-place upgrade of genuine WareStat 1.2 SQLite .db companies.
 *
 * A user must confirm the risk and make any desired backup themselves.
 * No VACUUM INTO, staging company, or other full-size database copy is made.
 */
public final class WareStatLocalUpgrade {
    private WareStatLocalUpgrade() {}

    public static Path installedLegacyFile() {
        return AppConstants.getAppDataDirectory().resolve("data.db")
            .toAbsolutePath().normalize();
    }

    public static boolean needsStartupConfirmation() {
        return AppConstants.getDatabasePath().equals(AppConstants.getDefaultDatabasePath())
            && !Files.exists(AppConstants.getDefaultDatabasePath())
            && Files.isRegularFile(installedLegacyFile());
    }

    /**
     * Kept for compatibility with older callers, but NEVER automatically
     * migrate a user's files. The consent dialog is owned by MainWindow.
     */
    public static void migrateIfNeeded() throws IOException {
        if (needsStartupConfirmation())
            throw new IOException("WareStat 1.2 database requires explicit migration confirmation");
    }

    /** File destination is next to the original so final rename is atomic. */
    public static Path upgradedPath(Path legacy) throws IOException {
        Path source = legacy.toAbsolutePath().normalize();
        String name = source.getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".db"))
            throw new IOException("Only WareStat 1.2 .db files can be migrated");
        return source.resolveSibling(name.substring(0,name.length()-3)+".wsbq");
    }

    /**
     * Only call after an explicit YES. The original .db is changed IN PLACE.
     * SQLite transactions protect individual schema migrations; without a
     * user-provided backup, a process/disk failure cannot be guaranteed safe.
     * The .wsbq filename is used only after the final validation succeeds.
     */
    public static Path migrateConfirmed(Path legacy, String currency)
            throws IOException, SQLException {
        Path source=legacy.toAbsolutePath().normalize();
        Path target=upgradedPath(source);
        if (Files.exists(target))
            throw new IOException("The destination already exists: " + target
                + ". Move it elsewhere before migrating; it will not be overwritten.");
        try { Currency.getInstance(currency); }
        catch (RuntimeException error) {
            throw new IOException("The original database currency is invalid",error);
        }

        // Recognition is read-only; no edits happen if this is not the actual
        // WareStat 1.2 layout or if SQLite reports corruption.
        WareStatLegacy12.validate(source);
        try (Connection conn=DatabaseManager.openConnection("jdbc:sqlite:"+source)) {
            DatabaseCurrency.initialize(conn,currency);
            DatabaseManager.prepareDatabase(conn);
            // The distributed 1.2 schema contains no product JPEG images.
            // Still validate embedded images after schema migration.
            ProductImageStore.validateEmbeddedImages(conn);
            DatabaseIntegrity.validate(conn,false);
            try (Statement s=conn.createStatement();
                 ResultSet r=s.executeQuery("PRAGMA integrity_check")) {
                if (!r.next() || !"ok".equalsIgnoreCase(r.getString(1)))
                    throw new SQLException("The migrated database failed SQLite integrity_check");
            }
            try (Statement s=conn.createStatement()) {
                s.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                s.execute("PRAGMA journal_mode=DELETE");
            }
        }

        // No second database file is ever created. Move only after SQLite
        // closed successfully and the structure and data passed validation.
        try {
            Files.move(source,target,StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source,target);
        }
        // The signature change happens only on a closed, journal-free file.
        WareStatFileHeader.seal(target);
        return target;
    }
}
