import java.io.IOException;
import java.nio.file.*;
import java.sql.*;

/** One-time, lossless conversion of the previous live data.db + JPEG folder. */
public final class WareStatLocalUpgrade {
    private WareStatLocalUpgrade() {}

    public static void migrateIfNeeded() throws IOException, SQLException {
        Path target = AppConstants.getDatabasePath().toAbsolutePath().normalize();
        if (Files.exists(target)) return;
        Path legacy = AppConstants.getAppDataDirectory().resolve("data.db");
        if (!Files.isRegularFile(legacy)) return;

        Files.createDirectories(target.getParent());
        Path staged = Files.createTempFile(target.getParent(), "warestat-upgrade-", ".wsbq");
        Files.delete(staged);
        try {
            org.sqlite.SQLiteConfig readOnly = new org.sqlite.SQLiteConfig();
            readOnly.setReadOnly(true);
            readOnly.setBusyTimeout(10000);
            try (Connection old = DriverManager.getConnection(
                    "jdbc:sqlite:" + legacy.toAbsolutePath(), readOnly.toProperties());
                 PreparedStatement vacuum = old.prepareStatement("VACUUM INTO ?")) {
                vacuum.setString(1, staged.toString());
                vacuum.execute();
            }
            try (Connection c = DatabaseManager.openConnection("jdbc:sqlite:" + staged)) {
                DatabaseManager.prepareDatabase(c);
                DatabaseManager.beginTransaction(c);
                try {
                    ProductImageStore.embedLegacyImages(c, AppConstants.getProductImagesDirectory());
                    ProductImageStore.validateEmbeddedImages(c);
                    c.commit();
                } catch (SQLException | IOException e) {
                    c.rollback();
                    throw e;
                } finally { c.setAutoCommit(true); }
                DatabaseIntegrity.validate(c, false);
                try (Statement s = c.createStatement();
                     ResultSet r = s.executeQuery("PRAGMA integrity_check")) {
                    if (!r.next() || !"ok".equalsIgnoreCase(r.getString(1)))
                        throw new SQLException("Migrated SQLite database failed integrity_check");
                }
                try (Statement s = c.createStatement()) {
                    s.execute("PRAGMA auto_vacuum=INCREMENTAL");
                    s.execute("VACUUM");
                }
            }
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(staged, target);
            }
            // Original database and photos are deliberately retained as a safety copy.
            System.out.println("Migrated WareStat data to " + target +
                "; original data.db and photos retained for recovery.");
        } finally {
            Files.deleteIfExists(staged);
        }
    }
}
