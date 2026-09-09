import java.sql.*;
import java.nio.file.*;
import java.util.Currency;
import javax.swing.JOptionPane;

/** WareStat stores one currency per database; changing a symbol never converts money. */
public final class DatabaseCurrency {
    private static volatile String boundCode;
    private DatabaseCurrency() {}
    public static String codeFromSetting(String setting) {
        if(setting==null || setting.isBlank())throw new IllegalArgumentException("Currency code is required");
        String code=setting.trim().split("[ -]",2)[0];
        Currency.getInstance(code);
        return code;
    }
    public static String boundCode() { return boundCode; }
    public static String read(Connection c) throws SQLException {
        try (PreparedStatement p=c.prepareStatement("SELECT value FROM application_metadata WHERE key='currency_code'");
             ResultSet r=p.executeQuery()) { return r.next()?r.getString(1):null; }
        catch(SQLException e) {
            if(e.getMessage().contains("no such table")) return null;
            throw e;
        }
    }
    public static void bind(Connection c) throws SQLException {
        String code=read(c);
        if(code==null) throw new SQLException("Database currency is missing");
        Currency.getInstance(code); boundCode=code;
    }
    public static void initialize(Connection c, String code) throws SQLException {
        Currency.getInstance(code);
        try(Statement s=c.createStatement()) { s.execute("CREATE TABLE IF NOT EXISTS application_metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)"); }
        try(PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO application_metadata(key,value) VALUES('currency_code',?)")) {
            p.setString(1,code);p.executeUpdate();
        }
    }
    public static void change(String code) throws SQLException {
        Currency.getInstance(code);
        Connection c=DatabaseManager.getInstance().getConnection();
        if(code.equals(read(c))) return;
        DatabaseManager.beginTransaction(c);
        try {
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT " +
                    "(SELECT COUNT(*) FROM products)+(SELECT COUNT(*) FROM orders)+(SELECT COUNT(*) FROM invoices)+" +
                    "(SELECT COUNT(*) FROM supplier_orders)+(SELECT COUNT(*) FROM supplier_price_lists)")) {
                if(r.next() && r.getLong(1)>0) throw new SQLException("Currency cannot be changed in a database that already contains products or documents. No conversion has been performed.");
            }
            try(PreparedStatement p=c.prepareStatement("UPDATE application_metadata SET value=? WHERE key='currency_code'")) {
                p.setString(1,code);p.executeUpdate();
            }
            c.commit();boundCode=code;
        } catch(SQLException e) {c.rollback();throw e;}
        finally {c.setAutoCommit(true);}
    }
    public static String confirmLegacyCurrency(java.awt.Component parent, Path file) throws SQLException {
        org.sqlite.SQLiteConfig settings=new org.sqlite.SQLiteConfig();settings.setReadOnly(true);
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath(),settings.toProperties())) {
            String code=read(c);if(code!=null)return code;
        }
        String chosen=JOptionPane.showInputDialog(parent,
            "This older database does not record its currency. Enter its ORIGINAL ISO currency code (for example EUR or USD). Amounts will not be converted.",
            SettingsPanel.getCurrencyCode());
        if(chosen==null) return null;
        return codeFromSetting(chosen.toUpperCase(java.util.Locale.ROOT));
    }
}
