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
     * Finalized invoices (Issued/Canceled) are immutable historical amounts and currencies.
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
                convertSupplierRelationsAndLots(c,rate);
                convertCustomerOrders(c,rate);
                convertSupplierOrders(c,rate);
                convertDraftInvoices(c,rate,code);
                reconcilePayments(c);
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
                "(SELECT COUNT(*) FROM product_suppliers)+(SELECT COUNT(*) FROM inventory_lots)")) {
            if(r.next() && r.getLong(1)>0)
                throw new SQLException("Choose whether to relabel or convert existing monetary values.");
        }
        change(code,ChangeMode.RELABEL,null);
    }

    private static void reconcilePayments(Connection c) throws SQLException {
        try (Statement s=c.createStatement()) {
            s.executeUpdate("""
                UPDATE orders SET payment_status=CASE
                    WHEN paid_amount<=0 THEN 'NOT_PAID'
                    WHEN paid_amount+0.004>=COALESCE(
                        (SELECT total FROM invoices WHERE order_id=orders.id),total_inc_vat)
                        THEN 'PAID' ELSE 'PARTIALLY_PAID' END
                """);
            s.executeUpdate("""
                UPDATE invoices SET payment_status=CASE
                    WHEN paid_amount<=0 THEN 'NOT_PAID'
                    WHEN paid_amount+0.004>=total THEN 'PAID' ELSE 'PARTIALLY_PAID' END
                WHERE status='Draft' AND order_id IS NULL
                """);
        }
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
        try(PreparedStatement orders=c.prepareStatement("SELECT id,total_ex_vat,total_inc_vat FROM orders ORDER BY id");
            PreparedStatement lines=c.prepareStatement(
                "SELECT quantity,sale_unit_price_ex_vat,sale_unit_price_inc_vat,vat_rate FROM order_details WHERE order_id=? ORDER BY id");
            ResultSet r=orders.executeQuery()) {
            while(r.next()) {
                int id=r.getInt("id"),count=0;double totalExVat=0,totalIncVat=0;
                lines.setInt(1,id);
                try(ResultSet d=lines.executeQuery()) {
                    while(d.next()) {
                        count++;
                        int quantity=Numbers.positiveQuantity(d.getObject("quantity"));
                        double net=Numbers.nonNegative(d.getObject("sale_unit_price_ex_vat"));
                        double gross=Numbers.nonNegative(d.getObject("sale_unit_price_inc_vat"));
                        totalExVat=Numbers.add(totalExVat,Numbers.lineTotal(quantity,net));
                        totalIncVat=Numbers.add(totalIncVat,SalePrice.lineIncVat(quantity,gross));
                    }
                }
                if(count>0 && (!sameMoney(totalExVat,Numbers.nonNegative(r.getObject("total_ex_vat"))) ||
                        !sameMoney(totalIncVat,Numbers.nonNegative(r.getObject("total_inc_vat")))))
                    throw new SQLException("Customer order "+id+" totals do not match its lines; reconcile it before currency conversion.");
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
                "SELECT quantity,sale_unit_price_inc_vat,vat_rate,total FROM invoice_details WHERE invoice_id=? ORDER BY id");
            ResultSet r=invoices.executeQuery()) {
            while(r.next()) {
                int id=r.getInt("id"),count=0;double taxable=0,vat=0,gross=0;
                lines.setInt(1,id);
                try(ResultSet d=lines.executeQuery()) {
                    while(d.next()) {
                        count++;
                        int quantity=Numbers.positiveQuantity(d.getObject("quantity"));
                        double incVat=Numbers.nonNegative(d.getObject("sale_unit_price_inc_vat"));
                        double rate=Numbers.vat(d.getObject("vat_rate"));
                        double net=SalePrice.lineExVat(quantity,incVat,rate);
                        double lineGross=SalePrice.lineIncVat(quantity,incVat);
                        if(!sameMoney(net,Numbers.nonNegative(d.getObject("total"))))
                            throw new SQLException("Draft invoice "+id+" contains an inconsistent line; reconcile it before currency conversion.");
                        taxable=Numbers.add(taxable,net);
                        gross=Numbers.add(gross,lineGross);
                        vat=Numbers.add(vat,Numbers.money(BigDecimal.valueOf(lineGross).subtract(BigDecimal.valueOf(net))));
                    }
                }
                if(count>0 && (!sameMoney(taxable,Numbers.nonNegative(r.getObject("taxable_amount"))) ||
                        !sameMoney(vat,Numbers.nonNegative(r.getObject("vat"))) ||
                        !sameMoney(gross,Numbers.nonNegative(r.getObject("total")))))
                    throw new SQLException("Draft invoice "+id+" totals do not match its lines; reconcile it before currency conversion.");
            }
        }
    }

    private static double convertUnit(Object value, BigDecimal rate) {
        double source=Numbers.nonNegative(value);
        // Calculate from decimal values first, then use the exact value that SQLite REAL
        // can actually preserve. Avoid a fixed decimal scale: it can either lose useful
        // precision or reject otherwise valid large prices.
        BigDecimal converted=BigDecimal.valueOf(source).multiply(rate);
        double stored=converted.doubleValue();
        return Numbers.nonNegative(stored);
    }

    private static double convertMoney(Object value, BigDecimal rate) {
        double source=Numbers.nonNegative(value);
        return Numbers.money(BigDecimal.valueOf(source).multiply(rate));
    }

    private static void convertProducts(Connection c,BigDecimal rate) throws SQLException {
        try(PreparedStatement read=c.prepareStatement(
                "SELECT id,sale_price_inc_vat,vat_rate,acquisition_cost FROM products");
            PreparedStatement write=c.prepareStatement(
                "UPDATE products SET sale_price_ex_vat=?,sale_price_inc_vat=?,acquisition_cost=? WHERE id=?");
            ResultSet r=read.executeQuery()) {
            while(r.next()) {
                double gross=convertUnit(r.getObject("sale_price_inc_vat"),rate);
                double vatRate=Numbers.vat(r.getObject("vat_rate"));
                write.setDouble(1,SalePrice.exVatFromIncVat(gross,vatRate));
                write.setDouble(2,gross);
                write.setDouble(3,convertUnit(r.getObject("acquisition_cost"),rate));
                write.setInt(4,r.getInt("id"));write.addBatch();
            }
            write.executeBatch();
        }
    }

    private static void convertSupplierRelationsAndLots(Connection c,BigDecimal rate) throws SQLException {
        try(PreparedStatement read=c.prepareStatement("SELECT id,purchase_price FROM product_suppliers");
            PreparedStatement write=c.prepareStatement("UPDATE product_suppliers SET purchase_price=? WHERE id=?");
            ResultSet r=read.executeQuery()) {
            while(r.next()) {
                write.setDouble(1,convertUnit(r.getObject("purchase_price"),rate));
                write.setInt(2,r.getInt("id"));write.addBatch();
            }
            write.executeBatch();
        }
        try(PreparedStatement read=c.prepareStatement("SELECT id,unit_cost FROM inventory_lots");
            PreparedStatement write=c.prepareStatement("UPDATE inventory_lots SET unit_cost=? WHERE id=?");
            ResultSet r=read.executeQuery()) {
            while(r.next()) {
                write.setDouble(1,convertUnit(r.getObject("unit_cost"),rate));
                write.setInt(2,r.getInt("id"));write.addBatch();
            }
            write.executeBatch();
        }
        try(PreparedStatement read=c.prepareStatement("SELECT id,unit_cost FROM inventory_lot_consumptions");
            PreparedStatement write=c.prepareStatement("UPDATE inventory_lot_consumptions SET unit_cost=? WHERE id=?");
            ResultSet r=read.executeQuery()) {
            while(r.next()) {
                write.setDouble(1,convertUnit(r.getObject("unit_cost"),rate));
                write.setInt(2,r.getInt("id"));write.addBatch();
            }
            write.executeBatch();
        }
    }

    private static void convertCustomerOrders(Connection c,BigDecimal rate) throws SQLException {
        try(PreparedStatement read=c.prepareStatement(
                "SELECT id,quantity,vat_rate,sale_unit_price_inc_vat,cost_total FROM order_details ORDER BY id");
            PreparedStatement write=c.prepareStatement(
                "UPDATE order_details SET sale_unit_price_ex_vat=?,sale_unit_price_inc_vat=?,cost_total=? WHERE id=?");
            ResultSet r=read.executeQuery()) {
            while(r.next()) {
                double gross=convertUnit(r.getObject("sale_unit_price_inc_vat"),rate);
                double vatRate=Numbers.vat(r.getObject("vat_rate"));
                write.setDouble(1,SalePrice.unitExVatForLine(Numbers.positiveQuantity(r.getObject("quantity")),gross,vatRate));
                write.setDouble(2,gross);
                Object cost=r.getObject("cost_total");
                if(cost==null) write.setNull(3,Types.REAL);
                else write.setDouble(3,convertMoney(cost,rate));
                write.setInt(4,r.getInt("id"));write.addBatch();
            }
            write.executeBatch();
        }

        try(PreparedStatement orders=c.prepareStatement("""
                SELECT o.id,o.total_ex_vat,o.total_inc_vat,o.payment_status,o.paid_amount,
                       i.id AS invoice_id,i.status AS invoice_status,
                       i.total AS invoice_total
                FROM orders o
                LEFT JOIN invoices i ON i.order_id=o.id
                ORDER BY o.id
                """);
            PreparedStatement lines=c.prepareStatement(
                "SELECT quantity,sale_unit_price_inc_vat,vat_rate FROM order_details WHERE order_id=? ORDER BY id");
            PreparedStatement update=c.prepareStatement(
                "UPDATE orders SET total_ex_vat=?,total_inc_vat=?,paid_amount=? WHERE id=?");
            ResultSet r=orders.executeQuery()) {
            while(r.next()) {
                int id=r.getInt("id");
                double totalExVat=0,totalIncVat=0;int lineCount=0;
                lines.setInt(1,id);
                try(ResultSet d=lines.executeQuery()) {
                    while(d.next()) {
                        lineCount++;
                        int quantity=Numbers.positiveQuantity(d.getObject("quantity"));
                        double gross=Numbers.nonNegative(d.getObject("sale_unit_price_inc_vat"));
                        double vatRate=Numbers.vat(d.getObject("vat_rate"));
                        totalExVat=Numbers.add(totalExVat,SalePrice.lineExVat(quantity,gross,vatRate));
                        totalIncVat=Numbers.add(totalIncVat,SalePrice.lineIncVat(quantity,gross));
                    }
                }
                if(lineCount==0) {
                    totalExVat=convertMoney(r.getObject("total_ex_vat"),rate);
                    totalIncVat=convertMoney(r.getObject("total_inc_vat"),rate);
                }

                Object invoiceId=r.getObject("invoice_id");
                String invoiceStatus=r.getString("invoice_status");
                boolean linked=invoiceId!=null;
                boolean historicalLinked=linked && !"Draft".equals(invoiceStatus);

                double paid = historicalLinked
                    ? Numbers.nonNegative(r.getObject("paid_amount"))
                    : convertMoney(r.getObject("paid_amount"),rate);

                update.setDouble(1,totalExVat);
                update.setDouble(2,totalIncVat);
                update.setDouble(3,paid);
                update.setInt(4,id);
                update.addBatch();
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
                "SELECT d.id,d.quantity,d.vat_rate,d.sale_unit_price_inc_vat FROM invoice_details d "+
                "JOIN invoices i ON i.id=d.invoice_id WHERE i.status='Draft' ORDER BY d.id");
            PreparedStatement write=c.prepareStatement(
                "UPDATE invoice_details SET sale_unit_price_ex_vat=?,sale_unit_price_inc_vat=?,total=? WHERE id=?");
            ResultSet r=read.executeQuery()) {
            while(r.next()) {
                int quantity=Numbers.positiveQuantity(r.getObject("quantity"));
                double vatRate=Numbers.vat(r.getObject("vat_rate"));
                double gross=convertUnit(r.getObject("sale_unit_price_inc_vat"),rate);
                double netUnit=SalePrice.unitExVatForLine(Numbers.positiveQuantity(r.getObject("quantity")),gross,vatRate);
                double netLine=SalePrice.lineExVat(quantity,gross,vatRate);
                write.setDouble(1,netUnit);
                write.setDouble(2,gross);
                write.setDouble(3,netLine);
                write.setInt(4,r.getInt("id"));write.addBatch();
            }
            write.executeBatch();
        }

        try(PreparedStatement drafts=c.prepareStatement(
                "SELECT id,taxable_amount,vat,total,payment_status,paid_amount FROM invoices WHERE status='Draft' ORDER BY id");
            PreparedStatement lines=c.prepareStatement(
                "SELECT quantity,sale_unit_price_inc_vat,vat_rate FROM invoice_details WHERE invoice_id=? ORDER BY id");
            PreparedStatement update=c.prepareStatement(
                "UPDATE invoices SET taxable_amount=?,vat=?,total=?,paid_amount=?,currency_code=? WHERE id=?");
            ResultSet r=drafts.executeQuery()) {
            while(r.next()) {
                int id=r.getInt("id");double taxable=0,vat=0,total=0;int lineCount=0;
                lines.setInt(1,id);
                try(ResultSet d=lines.executeQuery()) {
                    while(d.next()) {
                        lineCount++;
                        int quantity=Numbers.positiveQuantity(d.getObject("quantity"));
                        double grossUnit=Numbers.nonNegative(d.getObject("sale_unit_price_inc_vat"));
                        double vatRate=Numbers.vat(d.getObject("vat_rate"));
                        double lineGross=SalePrice.lineIncVat(quantity,grossUnit);
                        double lineNet=SalePrice.lineExVat(quantity,grossUnit,vatRate);
                        taxable=Numbers.add(taxable,lineNet);
                        total=Numbers.add(total,lineGross);
                        vat=Numbers.add(vat,Numbers.money(BigDecimal.valueOf(lineGross).subtract(BigDecimal.valueOf(lineNet))));
                    }
                }
                if(lineCount==0) {
                    taxable=convertMoney(r.getObject("taxable_amount"),rate);
                    vat=convertMoney(r.getObject("vat"),rate);
                    total=convertMoney(r.getObject("total"),rate);
                }
                double paidAmount = convertMoney(r.getObject("paid_amount"),rate);
                update.setDouble(1,taxable);update.setDouble(2,vat);
                update.setDouble(3,total);update.setDouble(4,paidAmount);
                update.setString(5,newCode);update.setInt(6,id);update.addBatch();
            }
            update.executeBatch();
        }
    }

    public static String confirmLegacyCurrency(java.awt.Component parent, Path file) throws SQLException {
        org.sqlite.SQLiteConfig settings=new org.sqlite.SQLiteConfig();settings.setReadOnly(true);
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath(),settings.toProperties())) {
            String code=read(c);if(code!=null)return code;
        }
        String currentCode = SettingsPanel.getCurrencyCode();
        String[] choices = SettingsPanel.getSupportedCurrencyChoices();
        Object chosen = Dialogs.showInputDialog(parent,
            "This older database does not record its currency. Select its ORIGINAL currency. Amounts will not be converted.",
            "Original Currency",
            JOptionPane.QUESTION_MESSAGE,
            null,
            choices,
            SettingsPanel.getCurrencyChoice(currentCode));
        if(chosen==null) return null;
        return codeFromSetting(chosen.toString());
    }
}

