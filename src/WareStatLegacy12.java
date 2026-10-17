import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Currency;
import java.util.Locale;
import java.util.Properties;

/**
 * Validates the actual SQLite schema distributed by WareStat 1.2
 * (claude/add-ant-release-license-87WXC, data.db and .db backups).
 * It never migrates or edits the source. The 1.2 release has no file signature;
 * its schema can be recognized structurally but not authenticated cryptographically.
 */
public final class WareStatLegacy12 {
    private WareStatLegacy12() {}

    private static boolean hasColumns(Connection c, String table, String... columns)
            throws SQLException {
        java.util.Set<String> found = new java.util.HashSet<>();
        try (Statement s = c.createStatement();
             ResultSet r = s.executeQuery("PRAGMA table_info(\"" + table + "\")")) {
            while (r.next()) found.add(r.getString("name").toLowerCase(Locale.ROOT));
        }
        return found.containsAll(java.util.Arrays.asList(columns));
    }

    private static boolean matchesSchema(Connection c) throws SQLException {
        // A cheap recognition check: it does not scan all data pages.
        return hasColumns(c,"products","id","code","name","price","quantity","supplier_id","vat_rate") ||
            !hasColumns(c,"customers","id","first_name","last_name") ||
            !hasColumns(c,"suppliers","id","company_name","vat_number") ||
            !hasColumns(c,"orders","id","customer_id","order_date","status","total") ||
            !hasColumns(c,"order_details","id","order_id","product_id","quantity","unit_price") ||
            !hasColumns(c,"invoices","id","number","taxable_amount","vat","total","status") ||
            !hasColumns(c,"invoice_details","id","invoice_id","product_id","quantity","unit_price") ||
            !hasColumns(c,"supplier_orders","id","supplier_id","number","total") ||
            !hasColumns(c,"supplier_order_details","id","order_id","product_id","quantity","unit_price") ||
            !hasColumns(c,"supplier_price_lists","id","supplier_id","product_id","price") ||
            !hasColumns(c,"warehouse_movements","id","product_id","date","type","quantity") ||
            !hasColumns(c,"stock_reservations","id","product_id","document_type","document_id") ||
            !hasColumns(c,"company_data","id","company_name") ||
            !hasColumns(c,"products","sale_price_ex_vat","sale_price_inc_vat");
    }

    public static void validate(Connection c) throws SQLException {
        if (!matchesSchema(c))
            throw new SQLException("This is not a WareStat 1.2 SQLite database");
        DatabaseIntegrity.validate(c,true);
    }

    /** Recognize a 1.2 file without running an expensive full integrity scan. */
    public static boolean isVersion12(Path file) throws SQLException {
        if (!Files.isRegularFile(file) ||
                !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".db"))
            return false;
        org.sqlite.SQLiteConfig settings=new org.sqlite.SQLiteConfig();
        settings.setReadOnly(true);
        settings.setBusyTimeout(10000);
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath(),
                settings.toProperties())) {
            return matchesSchema(c);
        }
    }

    public static void validate(Path file) throws SQLException {
        if (!Files.isRegularFile(file) || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".db"))
            throw new SQLException("Expected a WareStat 1.2 .db file");
        org.sqlite.SQLiteConfig settings=new org.sqlite.SQLiteConfig();
        settings.setReadOnly(true);
        settings.setBusyTimeout(10000);
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath(),settings.toProperties())) {
            validate(c);
        }
    }

    /** Local 1.2 installs stored the operating currency in app_settings.properties. */
    public static String installedCurrency() throws java.io.IOException {
        Path path=AppConstants.getSettingsPath();
        Properties p=new Properties();
        if(Files.isRegularFile(path)) {
            try(java.io.Reader in=Files.newBufferedReader(path,java.nio.charset.StandardCharsets.UTF_8)){
                p.load(in);
            }
        }
        String setting=p.getProperty("currency","EUR - Euro (€)");
        String code=setting.trim().split(" - ",2)[0];
        try {Currency.getInstance(code);}
        catch(IllegalArgumentException e){throw new java.io.IOException("Invalid legacy currency in settings: "+setting,e);}
        return code;
    }
}
