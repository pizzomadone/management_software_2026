import java.sql.*;
import java.nio.file.*;
import java.util.Currency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.swing.JOptionPane;

/** Controls the database operating currency while preserving finalized invoice history. */
public final class DatabaseCurrency {
    public enum ChangeMode { RELABEL, CONVERT }

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
            if(e.getMessage()!=null && e.getMessage().contains("no such table")) return null;
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
        try(Statement s=c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS application_metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        }
        try(PreparedStatement p=c.prepareStatement(
                "INSERT OR IGNORE INTO application_metadata(key,value) VALUES('currency_code',?)")) {
            p.setString(1,code);p.executeUpdate();
        }
    }

    /**
     * Explicit currency change. RELABEL preserves every numeric value and only changes the
     * operating currency. CONVERT updates every non-final monetary value using the supplied
     * rate, where 1 unit of the old currency equals {@code rate} units of the new currency.
     * Finalized invoices (Issued/Paid/Canceled) are immutable historical amounts and currencies.
     */
    public static void change(String code, ChangeMode mode, BigDecimal rate) throws SQLException {
        Currency.getInstance(code);
        if(mode==null) throw new IllegalArgumentException("Currency change mode is required");
        if(mode==ChangeMode.CONVERT && (rate==null || rate.signum()<=0))
            throw new IllegalArgumentException("Exchange rate must be greater than zero");

        Connection c=DatabaseManager.getInstance().getConnection();
        String oldCode=read(c);
        if(oldCode==null) throw new SQLException("Database currency is missing");
        if(code.equals(oldCode)) return;

        DatabaseManager.beginTransaction(c);
        try {
            if(mode==ChangeMode.CONVERT) {
                verifyConvertibleDocuments(c);
                convertProducts(c,rate);
                convertSupplierPriceLists(c,rate);
                convertCustomerOrders(c,rate);
                convertSupplierOrders(c,rate);
                convertDraftInvoices(c,rate,code);
            } else {
                // Drafts are not historical documents: they follow the new operating currency.
                try(PreparedStatement p=c.prepareStatement(
                        "UPDATE invoices SET currency_code=? WHERE status='Draft'")) {
                    p.setString(1,code);p.executeUpdate();
                }
            }

            try(PreparedStatement p=c.prepareStatement(
                    "UPDATE application_metadata SET value=? WHERE key='currency_code'")) {
                p.setString(1,code);
                if(p.executeUpdate()!=1) throw new SQLException("Database currency metadata is missing");
            }

            c.commit();
            boundCode=code;
        } catch(SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /** Retained only for callers changing an empty database. */
    public static void change(String code) throws SQLException {
        Connection c=DatabaseManager.getInstance().getConnection();
        if(code.equals(read(c))) return;
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery(
                "SELECT (SELECT COUNT(*) FROM products)+(SELECT COUNT(*) FROM orders)+"+
                "(SELECT COUNT(*) FROM invoices)+(SELECT COUNT(*) FROM supplier_orders)+"+
                "(SELECT COUNT(*) FROM supplier_price_lists)")) {
            if(r.next() && r.getLong(1)>0)
                throw new SQLException("Choose whether to relabel or convert existing monetary values.");
        }
        change(code,ChangeMode.RELABEL,null);
    }

    private static boolean sameMoney(double left,double right) {
        return BigDecimal.valueOf(left).setScale(2,RoundingMode.HALF_UP)
            .compareTo(BigDecimal.valueOf(right).setScale(2,RoundingMode.HALF_UP))==0;
    }

    /**
     * A currency change must never double as a silent reconciliation. If a document is
     * already inconsistent with its own lines, stop before changing any monetary value.
     */
    private static void verifyConvertibleDocuments(Connection c) throws SQLException {
        try(PreparedStatement orders=c.prepareStatement("SELECT id,total FROM orders ORDER BY id");
            PreparedStatement lines=c.prepareStatement("SELECT quantity,unit_price FROM order_details WHERE order_id=? ORDER BY id");
            ResultSet r=orders.executeQuery()) {
            while(r.next()) {
                int id=r.getInt("id"),count=0;double total=0;
                lines.setInt(1,id);
                try(ResultSet d=lines.executeQuery()) {
                    while(d.next()) {
                        count++;
                        total=Numbers.add(total,Numbers.lineTotal(
                            Numbers.positiveQuantity(d.getObject("quantity")),
                            Numbers.nonNegative(d.getObject("unit_price"))));
                    }
                }
                if(count>0 && !sameMoney(total,Numbers.nonNegative(r.getObject("total"))))
                    throw new SQLException("Customer order "+id+" total does not match its lines; reconcile it before currency conversion.");
            }
        }

        try(PreparedStatement orders=c.prepareStatement("SELECT id,total FROM supplier_orders ORDER BY id");
            PreparedStatement lines=c.prepareStatement("SELECT quantity,unit_price,total FROM supplier_order_details WHERE order_id=? ORDER BY id");
            ResultSet r=orders.executeQuery()) {
            while(r.next()) {
                int id=r.getInt("id"),count=0;double total=0;
                lines.setInt(1,id);
                try(ResultSet d=lines.executeQuery()) {
                    while(d.next()) {
                        count++;
                        double line=Numbers.lineTotal(Numbers.positiveQuantity(d.getObject("quantity")),
                            Numbers.nonNegative(d.getObject("unit_price")));
                        if(!sameMoney(line,Numbers.nonNegative(d.getObject("total"))))
                            throw new SQLException("Supplier order "+id+" contains an inconsistent line; reconcile it before currency conversion.");
                        total=Numbers.add(total,line);
                    }
                }
                if(count>0 && !sameMoney(total,Numbers.nonNegative(r.getObject("total"))))
                    throw new SQLException("Supplier order "+id+" total does not match its lines; reconcile it before currency conversion.");
            }
        }

        try(PreparedStatement invoices=c.prepareStatement(
                "SELECT id,taxable_amount,vat,total FROM invoices WHERE status='Draft' ORDER BY id");
            PreparedStatement lines=c.prepareStatement(
                "SELECT quantity,unit_price,vat_rate,total FROM invoice_details WHERE invoice_id=? ORDER BY id");
            ResultSet r=invoices.executeQuery()) {
            while(r.next()) {
                int id=r.getInt("id"),count=0;double taxable=0,vat=0;
                lines.setInt(1,id);
                try(ResultSet d=lines.executeQuery()) {
                    while(d.next()) {
                        count++;
                        double line=Numbers.lineTotal(Numbers.positiveQuantity(d.getObject("quantity")),
                            Numbers.nonNegative(d.getObject("unit_price")));
                        if(!sameMoney(line,Numbers.nonNegative(d.getObject("total"))))
                            throw new SQLException("Draft invoice "+id+" contains an inconsistent line; reconcile it before currency conversion.");
                        taxable=Numbers.add(taxable,line);
                        vat=Numbers.add(vat,Numbers.tax(line,Numbers.vat(d.getObject("vat_rate"))));
                    }
                }
                if(count>0 && (!sameMoney(taxable,Numbers.nonNegative(r.getObject("taxable_amount"))) ||
                        !sameMoney(vat,Numbers.nonNegative(r.getObject("vat"))) ||
                        !sameMoney(Numbers.add(taxable,vat),Numbers.nonNegative(r.getObject("total")))))
                    throw new SQLException("Draft invoice "+id+" totals do not match its lines; reconcile it before currency conversion.");
            }
        }
    }

    private static double convertUnit(Object value, BigDecimal rate) {
        double source=Numbers.nonNegative(value);
        // Twelve decimals keep the line-total error below one cent even at the
        // maximum supported integer quantity, while remaining safely representable in SQLite REAL.
        BigDecimal converted=BigDecimal.valueOf(source).multiply(rate)
            .setScale(12,RoundingMode.HALF_UP).stripTrailingZeros();
        return Numbers.nonNegative(converted.toPlainString());
    }

    private static double convertMoney(Object value, BigDecimal rate) {
        double source=Numbers.nonNegative(value);
        return Numbers.money(BigDecimal.valueOf(source).multiply(rate));
    }

    private static void convertProducts(Connection c,BigDecimal rate) throws SQLException {
        try(PreparedStatement read=c.prepareStatement("SELECT id,price,acquisition_cost FROM products");
            PreparedStatement write=c.prepareStatement(
                "UPDATE products SET price=?,acquisition_cost=? WHERE id=?");
            ResultSet r=read.executeQuery()) {
            while(r.next()) {
                write.setDouble(1,convertUnit(r.getObject("price"),rate));
                write.setDouble(2,convertUnit(r.getObject("acquisition_cost"),rate));
                write.setInt(3,r.getInt("id"));write.addBatch();
            }
            write.executeBatch();
        }
    }

    private static void convertSupplierPriceLists(Connection c,BigDecimal rate) throws SQLException {
        try(PreparedStatement read=c.prepareStatement("SELECT id,price FROM supplier_price_lists");
            PreparedStatement write=c.prepareStatement("UPDATE supplier_price_lists SET price=? WHERE id=?");
            ResultSet r=read.executeQuery()) {
            while(r.next()) {
                write.setDouble(1,convertUnit(r.getObject("price"),rate));
                write.setInt(2,r.getInt("id"));write.addBatch();
            }
            write.executeBatch();
        }
    }

    private static void convertCustomerOrders(Connection c,BigDecimal rate) throws SQLException {
        try(PreparedStatement read=c.prepareStatement(
                "SELECT id,unit_price FROM order_details ORDER BY id");
            PreparedStatement write=c.prepareStatement(
                "UPDATE order_details SET unit_price=? WHERE id=?");
            ResultSet r=read.executeQuery()) {
            while(r.next()) {
                write.setDouble(1,convertUnit(r.getObject("unit_price"),rate));
                write.setInt(2,r.getInt("id"));write.addBatch();
            }
            write.executeBatch();
        }

        try(PreparedStatement orders=c.prepareStatement(
                "SELECT id,total,payment_status,paid_amount FROM orders ORDER BY id");
            PreparedStatement lines=c.prepareStatement(
                "SELECT quantity,unit_price FROM order_details WHERE order_id=? ORDER BY id");
            PreparedStatement update=c.prepareStatement(
                "UPDATE orders SET total=?,paid_amount=? WHERE id=?");
            ResultSet r=orders.executeQuery()) {
            while(r.next()) {
                int id=r.getInt("id");
                double total=0;int lineCount=0;
                lines.setInt(1,id);
                try(ResultSet d=lines.executeQuery()) {
                    while(d.next()) {
                        lineCount++;
                        total=Numbers.add(total,
                            Numbers.lineTotal(Numbers.positiveQuantity(d.getObject("quantity")),
                                Numbers.nonNegative(d.getObject("unit_price"))));
                    }
                }
                if(lineCount==0) total=convertMoney(r.getObject("total"),rate);
                String payment=r.getString("payment_status");
                double paid;
                if("PAID".equals(payment)) paid=total;
                else if("NOT_PAID".equals(payment)) paid=0;
                else paid=convertMoney(r.getObject("paid_amount"),rate);
                update.setDouble(1,total);update.setDouble(2,paid);update.setInt(3,id);update.addBatch();
            }
            update.executeBatch();
        }
    }

    private static void convertSupplierOrders(Connection c,BigDecimal rate) throws SQLException {
        try(PreparedStatement read=c.prepareStatement(
                "SELECT id,quantity,unit_price FROM supplier_order_details ORDER BY id");
            PreparedStatement write=c.prepareStatement(
                "UPDATE supplier_order_details SET unit_price=?,total=? WHERE id=?");
            ResultSet r=read.executeQuery()) {
            while(r.next()) {
                int quantity=Numbers.positiveQuantity(r.getObject("quantity"));
                double price=convertUnit(r.getObject("unit_price"),rate);
                write.setDouble(1,price);write.setDouble(2,Numbers.lineTotal(quantity,price));
                write.setInt(3,r.getInt("id"));write.addBatch();
            }
            write.executeBatch();
        }

        try(PreparedStatement orders=c.prepareStatement("SELECT id,total FROM supplier_orders ORDER BY id");
            PreparedStatement sum=c.prepareStatement(
                "SELECT total FROM supplier_order_details WHERE order_id=? ORDER BY id");
            PreparedStatement update=c.prepareStatement("UPDATE supplier_orders SET total=? WHERE id=?");
            ResultSet r=orders.executeQuery()) {
            while(r.next()) {
                int id=r.getInt("id");double total=0;int lineCount=0;
                sum.setInt(1,id);
                try(ResultSet d=sum.executeQuery()) {
                    while(d.next()) {lineCount++;total=Numbers.add(total,Numbers.nonNegative(d.getObject("total")));}
                }
                if(lineCount==0) total=convertMoney(r.getObject("total"),rate);
                update.setDouble(1,total);update.setInt(2,id);update.addBatch();
            }
            update.executeBatch();
        }
    }

    private static void convertDraftInvoices(Connection c,BigDecimal rate,String newCode) throws SQLException {
        try(PreparedStatement read=c.prepareStatement(
                "SELECT d.id,d.quantity,d.unit_price FROM invoice_details d "+
                "JOIN invoices i ON i.id=d.invoice_id WHERE i.status='Draft' ORDER BY d.id");
            PreparedStatement write=c.prepareStatement(
                "UPDATE invoice_details SET unit_price=?,total=? WHERE id=?");
            ResultSet r=read.executeQuery()) {
            while(r.next()) {
                int quantity=Numbers.positiveQuantity(r.getObject("quantity"));
                double price=convertUnit(r.getObject("unit_price"),rate);
                write.setDouble(1,price);write.setDouble(2,Numbers.lineTotal(quantity,price));
                write.setInt(3,r.getInt("id"));write.addBatch();
            }
            write.executeBatch();
        }

        try(PreparedStatement drafts=c.prepareStatement(
                "SELECT id,taxable_amount,vat,total FROM invoices WHERE status='Draft' ORDER BY id");
            PreparedStatement lines=c.prepareStatement(
                "SELECT quantity,unit_price,vat_rate FROM invoice_details WHERE invoice_id=? ORDER BY id");
            PreparedStatement update=c.prepareStatement(
                "UPDATE invoices SET taxable_amount=?,vat=?,total=?,currency_code=? WHERE id=?");
            ResultSet r=drafts.executeQuery()) {
            while(r.next()) {
                int id=r.getInt("id");double taxable=0,vat=0;int lineCount=0;
                lines.setInt(1,id);
                try(ResultSet d=lines.executeQuery()) {
                    while(d.next()) {
                        lineCount++;
                        double line=Numbers.lineTotal(
                            Numbers.positiveQuantity(d.getObject("quantity")),
                            Numbers.nonNegative(d.getObject("unit_price")));
                        taxable=Numbers.add(taxable,line);
                        vat=Numbers.add(vat,Numbers.tax(line,Numbers.vat(d.getObject("vat_rate"))));
                    }
                }
                double total;
                if(lineCount==0) {
                    taxable=convertMoney(r.getObject("taxable_amount"),rate);
                    vat=convertMoney(r.getObject("vat"),rate);
                    total=convertMoney(r.getObject("total"),rate);
                } else total=Numbers.add(taxable,vat);
                update.setDouble(1,taxable);update.setDouble(2,vat);
                update.setDouble(3,total);update.setString(4,newCode);
                update.setInt(5,id);update.addBatch();
            }
            update.executeBatch();
        }
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
