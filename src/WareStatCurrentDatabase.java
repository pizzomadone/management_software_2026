import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Shared acceptance rules for opening and importing a WareStat 1.3 .wsbq.
 * A clean .wsbq has the WareStat header; after a crash its 16 bytes can
 * instead contain SQLite's header (or an interrupted signature write).
 *
 * Only recovered headers require a full read-only schema/integrity check
 * before acceptance. No source bytes are rewritten by this verification.
 */
public final class WareStatCurrentDatabase {
    private WareStatCurrentDatabase() {}

    public static void validateSelectable(Path path) throws IOException, SQLException {
        if (path == null || !path.getFileName().toString()
                .toLowerCase(Locale.ROOT).endsWith(".wsbq")
                || !Files.isRegularFile(path))
            throw new IOException("Select a WareStat 1.3 .wsbq database");

        // A source with a live/hot WAL or rollback journal cannot be copied
        // as an isolated file without risking missing committed transactions.
        // Refuse it in BOTH Open and Backup Import/Restore.
        if (Files.exists(Path.of(path + "-wal"))
                || Files.exists(Path.of(path + "-journal")))
            throw new IOException("The selected WSBQ has a WAL or rollback journal. "
                + "Recover it at its original location in WareStat before copying or importing.");

        if (WareStatFileHeader.isSealed(path)) return;

        boolean sqlite=WareStatFileHeader.isSQLite(path);
        if (!sqlite && !WareStatFileHeader.isInterruptedSignature(path))
            throw new IOException("Invalid WareStat .wsbq signature");

        Path readableCopy=null;
        try {
            Path readable=path;
            if (!sqlite) {
                // Only a torn 16-byte signature needs a temporary readable
                // copy; the original header is never altered for validation.
                readableCopy=WareStatFileHeader.copyReadable(
                    path,AppConstants.getAppDataDirectory());
                readable=readableCopy;
            }

            org.sqlite.SQLiteConfig settings=new org.sqlite.SQLiteConfig();
            settings.setReadOnly(true);
            settings.setBusyTimeout(10000);
            try(Connection c=DriverManager.getConnection(
                    "jdbc:sqlite:"+readable.toAbsolutePath(),settings.toProperties())) {
                validateCurrentContents(c);
            }
        } finally {
            if (readableCopy!=null)Files.deleteIfExists(readableCopy);
        }
    }

    /** Same current-format checks used by Backup, Open recovery, and imports. */
    public static void validateCurrentContents(Connection c) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(
                "SELECT version FROM schema_migrations ORDER BY version DESC LIMIT 1");
            ResultSet r=p.executeQuery()) {
            if (!r.next() || !"022".equals(r.getString(1)))
                throw new SQLException("Incompatible WareStat .wsbq schema version");
        }
        DatabaseIntegrity.validate(c,false);
        ProductImageStore.validateEmbeddedImages(c);
    }
}
