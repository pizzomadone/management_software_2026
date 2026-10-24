import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.Arrays;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/**
 * Focused, headless tests that execute before the unrelated UI recovery suite.
 * Test current WSBQ only; 1.2 DB migration remains a separate path.
 */
public final class WsbqRecoveryRegressionTest {
    private WsbqRecoveryRegressionTest() {}
    private static void check(boolean ok,String why) {
        if(!ok)throw new AssertionError(why);
    }
    private static void checkRejected(CheckedOperation action,String why) throws Exception {
        boolean rejected=false;
        try { action.execute(); }
        catch(java.io.IOException | SQLException | IllegalArgumentException expected) {
            rejected=true;
        }
        check(rejected,why);
    }
    @FunctionalInterface private interface CheckedOperation { void execute() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("warestat-current-wsbq-recovery-");
        Path signed=dir.resolve("signed.wsbq");
        Path raw=dir.resolve("unsealed-after-crash.wsbq");
        Path partial=dir.resolve("partial-header.wsbq");
        Path foreign=dir.resolve("foreign-sqlite.wsbq");
        Path obsolete=dir.resolve("obsolete-current.wsbq");
        Path recovered=dir.resolve("restored-current.wsbq");

        DatabaseManager.createEmptyDatabase(signed,"EUR");
        byte[] photo;
        BufferedImage jpeg=new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB);
        jpeg.setRGB(3,4,0x00883377);
        try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            check(ImageIO.write(jpeg,"jpg",bytes),"Unable to generate embedded JPEG");
            photo=bytes.toByteArray();
        }
        try(Connection c=DatabaseManager.openConnection("jdbc:sqlite:"+signed);
            PreparedStatement product=c.prepareStatement(
                "INSERT INTO products(id,code,name,description,sale_price_ex_vat," +
                "sale_price_inc_vat,quantity,acquisition_cost,vat_rate) " +
                "VALUES(1,'CRASH','Test product','',11,11,5,6,0)");
            PreparedStatement image=c.prepareStatement(
                "INSERT INTO product_images(product_id,filename,is_primary,sort_order,image_data) " +
                "VALUES(1,'crash.jpg',1,0,?)")) {
            product.executeUpdate();
            image.setBytes(1,photo);image.executeUpdate();
        }
        WareStatFileHeader.seal(signed);
        Files.copy(signed,raw);
        WareStatFileHeader.restoreForSQLite(raw);
        check(WareStatFileHeader.isSQLite(raw),"Unclean-shutdown header fixture is not SQLite");
        byte[] original=Files.readAllBytes(raw);

        WareStatCurrentDatabase.validateSelectable(raw);
        check(Arrays.equals(original,Files.readAllBytes(raw)),
            "File Open verification modified the source WSBQ");
        BackupManager backup=BackupManager.getInstance();
        backup.validateDatabase(raw);
        Path imported=Path.of(backup.importBackup(raw).absolutePath());
        check(WareStatFileHeader.isSealed(imported),
            "Backup Import did not recover a current SQLite-header WSBQ");
        backup.restoreBackupAsNewDatabase(raw,recovered,null);
        check(WareStatFileHeader.isSealed(recovered),
            "Backup Restore did not recover a current SQLite-header WSBQ");
        check(Arrays.equals(original,Files.readAllBytes(raw)),
            "Backup Import/Restore modified an external source WSBQ");
        System.out.println("PASS: current SQLite-header WSBQ accepted by Backup Import and Restore");

        Files.copy(signed,partial);
        try(RandomAccessFile f=new RandomAccessFile(partial.toFile(),"rw")) {
            byte[] sqlite="SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
            f.seek(0);f.write(sqlite,0,7);
        }
        check(WareStatFileHeader.isInterruptedSignature(partial),
            "Interrupted WareStat signature fixture was not recognized");
        byte[] partialOriginal=Files.readAllBytes(partial);
        WareStatCurrentDatabase.validateSelectable(partial);
        Path importedPartial=Path.of(backup.importBackup(partial).absolutePath());
        check(WareStatFileHeader.isSealed(importedPartial)
            && Arrays.equals(partialOriginal,Files.readAllBytes(partial)),
            "A partial header could not be recovered safely");
        System.out.println("PASS: interrupted signature recovered without changing source");

        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+foreign);
            Statement st=c.createStatement()) {
            st.execute("CREATE TABLE other_software(id INTEGER PRIMARY KEY)");
        }
        checkRejected(()->WareStatCurrentDatabase.validateSelectable(foreign),
            "Unrelated SQLite was accepted by File Open validation");
        checkRejected(()->backup.importBackup(foreign),
            "Unrelated SQLite was accepted by Backup Import");
        System.out.println("PASS: unrelated SQLite rejected consistently");

        Files.copy(raw,obsolete);
        try(Connection c=DatabaseManager.openConnection("jdbc:sqlite:"+obsolete);
            Statement st=c.createStatement()) {
            st.execute("DELETE FROM schema_migrations WHERE version='022'");
        }
        checkRejected(()->WareStatCurrentDatabase.validateSelectable(obsolete),
            "Older WSBQ database schema accepted by File Open");
        checkRejected(()->backup.importBackup(obsolete),
            "Older WSBQ database schema accepted by Backup Import");
        System.out.println("PASS: obsolete current-format schema rejected consistently");

        Path wal=Path.of(raw+"-wal");
        Files.write(wal,new byte[]{1,2,3,4});
        try {
            checkRejected(()->WareStatCurrentDatabase.validateSelectable(raw),
                "Unsafe WAL source accepted by File Open");
            checkRejected(()->backup.importBackup(raw),
                "Unsafe WAL source accepted by Backup Import");
            checkRejected(()->backup.restoreBackupAsNewDatabase(raw,dir.resolve("unsafe.wsbq"),null),
                "Unsafe WAL source accepted by Backup Restore");
            check(Arrays.equals(original,Files.readAllBytes(raw)),
                "Rejecting a WAL source changed its contents");
            check(!Files.exists(dir.resolve("unsafe.wsbq")),
                "An unsafe source was published as a restored database");
        } finally {Files.deleteIfExists(wal);}
        System.out.println("PASS: WAL sidecars rejected without losing committed data");

        // Exercise the production File > Open backend on the raw source and
        // check that the embedded JPEG survives without a migration.
        DatabaseManager manager=DatabaseManager.getInstance();
        Path previous=AppConstants.getDatabasePath().toAbsolutePath().normalize();
        manager.initDatabase();
        try {
            manager.openDatabase(raw);
            try(Statement st=manager.getConnection().createStatement();
                ResultSet r=st.executeQuery(
                    "SELECT p.quantity,i.image_data FROM products p " +
                    "JOIN product_images i ON p.id=i.product_id WHERE p.code='CRASH'")) {
                check(r.next()&&r.getInt(1)==5&&Arrays.equals(photo,r.getBytes(2)),
                    "Manual File Open did not retain stock and photo data");
            }
            check(WareStatFileHeader.isSQLite(raw),
                "The open current database was unexpectedly sealed");
            System.out.println("PASS: File Open reads recovered current WSBQ in place");
        } finally {
            if(!previous.equals(AppConstants.getDatabasePath()))
                manager.openDatabase(previous);
            manager.shutdownAndSeal();
        }
        check(WareStatFileHeader.isSealed(raw),
            "Opened WSBQ did not seal cleanly on switch");
        System.out.println("PASS: File Open seals current database on exit");
    }
}
