import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.Currency;
import java.util.Locale;

/**
 * Explicit, lossless WareStat 1.2 -> 1.3 upgrade.
 * Read the original data.db without writing to it, create a new .wsbq,
 * and publish that file only after migration and integrity checks succeed.
 * No additional "safety copy" of either company database is generated.
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

    /** Migration is never implicit: the confirmation belongs to MainWindow. */
    public static void migrateIfNeeded() throws IOException {
        if (needsStartupConfirmation())
            throw new IOException("WareStat 1.2 database requires explicit migration confirmation");
    }

    /** A new WSBQ is created next to the existing .db, which is retained. */
    public static Path upgradedPath(Path legacy) throws IOException {
        Path source = legacy.toAbsolutePath().normalize();
        String name = source.getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".db"))
            throw new IOException("Only WareStat 1.2 .db files can be migrated");
        return source.resolveSibling(name.substring(0,name.length()-3)+".wsbq");
    }

    /**
     * Only call after explicit YES. The .db remains byte-for-byte unchanged:
     * SQLite reads the legacy company through a read-only connection and
     * VACUUM INTO constructs the new database in a temporary destination.
     * We migrate and validate only this new database, then atomically move
     * it into the final .wsbq name. Temporary files are removed on failure.
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

        // Check the real 1.2 schema and source integrity before allocating a
        // second full-size file. The input remains read-only throughout.
        WareStatLegacy12.validate(source);

        Path staged=Files.createTempFile(target.getParent(),"warestat-upgrade-", ".wsbq");
        Files.delete(staged); // VACUUM INTO requires a nonexistent target.
        try {
            org.sqlite.SQLiteConfig readOnly=new org.sqlite.SQLiteConfig();
            readOnly.setReadOnly(true);
            readOnly.setBusyTimeout(10000);
            try (Connection old=DriverManager.getConnection(
                    "jdbc:sqlite:"+source,readOnly.toProperties());
                 PreparedStatement vacuum=old.prepareStatement("VACUUM INTO ?")) {
                vacuum.setString(1,staged.toString());
                vacuum.execute();
            }

            try (Connection conn=DatabaseManager.openConnection("jdbc:sqlite:"+staged)) {
                DatabaseCurrency.initialize(conn,currency);
                DatabaseManager.prepareDatabase(conn);

                // Prior versions may have referenced JPEG files outside SQLite.
                // Embed them in the new WSBQ; never modify the original photos.
                Path localImages=source.getParent().resolve("product_images");
                Path images=Files.isDirectory(localImages) ? localImages
                    : AppConstants.getProductImagesDirectory();
                DatabaseManager.beginTransaction(conn);
                try {
                    ProductImageStore.embedLegacyImages(conn,images);
                    ProductImageStore.validateEmbeddedImages(conn);
                    conn.commit();
                } catch (SQLException | IOException problem) {
                    conn.rollback();
                    throw problem;
                } finally {
                    conn.setAutoCommit(true);
                }

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

            WareStatFileHeader.seal(staged);
            try {
                Files.move(staged,target,StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged,target);
            }
            return target;
        } finally {
            Files.deleteIfExists(staged);
            // Only temporary output files are removed; input .db is NEVER touched.
            Files.deleteIfExists(Path.of(staged + "-wal"));
            Files.deleteIfExists(Path.of(staged + "-shm"));
        }
    }
}
