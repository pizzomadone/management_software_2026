import java.sql.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;

/** Immutable parties and product descriptions attached to each saved invoice. */
public final class InvoiceSnapshots {
    private InvoiceSnapshots() {}
    public static void capture(Connection c,int invoiceId,boolean legacy) throws SQLException {
        Properties customer=new Properties(), company=new Properties();
        boolean needCustomer,needCompany;
        try(PreparedStatement p=c.prepareStatement("SELECT customer_snapshot,company_snapshot FROM invoices WHERE id=?")) {
            p.setInt(1,invoiceId);try(ResultSet r=p.executeQuery()) {
                if(!r.next())throw new SQLException("Invoice no longer exists");
                needCustomer=r.getString(1)==null;needCompany=r.getString(2)==null;
            }
        }
        if(needCustomer) try(PreparedStatement p=c.prepareStatement("SELECT c.* FROM customers c JOIN invoices i ON i.customer_id=c.id WHERE i.id=?")) {
            p.setInt(1,invoiceId);try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw new SQLException("Invoice customer no longer exists");
                for(String key:List.of("first_name","last_name","email","phone","address"))customer.setProperty(key,Objects.toString(r.getString(key),""));
            }
        }
        byte[] logo=null;
        if(needCompany) try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM company_data ORDER BY id LIMIT 1")) {
            if(r.next()) {
                for(String key:List.of("company_name","vat_number","tax_code","address","city","postal_code","country","phone","email","website"))
                    company.setProperty(key,Objects.toString(r.getString(key),""));
                String path=r.getString("logo_path");
                if(path!=null && !path.isBlank() && Files.isRegularFile(Path.of(path))) {
                    try {logo=Files.readAllBytes(Path.of(path));}
                    catch(IOException e) {throw new SQLException("Unable to preserve company logo in invoice",e);}
                }
            }
        }
        try(PreparedStatement p=c.prepareStatement("UPDATE invoices SET currency_code=COALESCE(currency_code,?)," +
                "customer_snapshot=COALESCE(customer_snapshot,?),company_snapshot=COALESCE(company_snapshot,?)," +
                "logo_snapshot=CASE WHEN snapshot_version IS NULL THEN ? ELSE logo_snapshot END," +
                "snapshot_version=COALESCE(snapshot_version,?) WHERE id=?")) {
            p.setString(1,DatabaseCurrency.read(c));p.setString(2,encode(customer));p.setString(3,encode(company));
            p.setBytes(4,logo);p.setInt(5,legacy?0:1);p.setInt(6,invoiceId);p.executeUpdate();
        }
        try(PreparedStatement p=c.prepareStatement("UPDATE invoice_details SET " +
                "product_code=COALESCE(product_code,(SELECT code FROM products WHERE id=product_id))," +
                "product_name=COALESCE(product_name,(SELECT name FROM products WHERE id=product_id)) WHERE invoice_id=?")) {
            p.setInt(1,invoiceId);p.executeUpdate();
        }
    }
    public static void captureLegacy(Connection c) throws SQLException {
        List<Integer> ids=new ArrayList<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT id FROM invoices WHERE snapshot_version IS NULL")) {
            while(r.next())ids.add(r.getInt(1));
        }
        for(int id:ids)capture(c,id,true);
    }
    /**
     * Some legacy 1.2 -> 1.3 archives were left with missing party snapshots.
     * Repair only explicitly legacy/unversioned rows, never replace a saved
     * 1.3 (snapshot_version=1) historical identity.
     *
     * The legacy format did not retain immutable invoice party information;
     * the surviving customer/company record is the best available source.
     * Repair is atomic: on an error nothing is partially filled in.
     */
    public static int repairIncompleteLegacy(Connection c) throws SQLException {
        List<Integer> ids=new ArrayList<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery(
                "SELECT id FROM invoices WHERE (snapshot_version IS NULL OR snapshot_version=0) " +
                "AND (customer_snapshot IS NULL OR company_snapshot IS NULL OR snapshot_version IS NULL)")) {
            while(r.next())ids.add(r.getInt(1));
        }
        if(ids.isEmpty())return 0;
        if(!c.getAutoCommit())
            throw new SQLException("Invoice snapshot repair requires an independent transaction");
        DatabaseManager.beginTransaction(c);
        try {
            for(int id:ids)capture(c,id,true);
            c.commit();
            return ids.size();
        }catch(SQLException | RuntimeException e){
            c.rollback();
            throw e;
        }finally{
            c.setAutoCommit(true);
        }
    }

    /** Require complete snapshots when publishing a newly upgraded 1.2 WSBQ. */
    public static void validateMigratedSnapshots(Connection c) throws SQLException {
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery(
                "SELECT number FROM invoices WHERE customer_snapshot IS NULL " +
                "OR company_snapshot IS NULL OR snapshot_version IS NULL LIMIT 1")) {
            if(r.next())throw new SQLException("Migrated invoice " + r.getString(1) +
                " has missing historical customer/company snapshots");
        }
    }

    private static String encode(Properties p) throws SQLException {
        StringWriter writer=new StringWriter();
        try {p.store(writer,null);return writer.toString();}
        catch(IOException e) {throw new SQLException("Unable to capture invoice details",e);}
    }
    public static Properties decode(String text) throws SQLException {
        if(text==null) throw new SQLException("Invoice snapshot is missing");
        Properties p=new Properties();
        try {p.load(new StringReader(text));return p;}
        catch(IOException | IllegalArgumentException e) {throw new SQLException("Invalid invoice snapshot",e);}
    }
    public static Customer customer(Connection c,Invoice invoice) throws SQLException {
        // Old imports may have thousands of missing legacy snapshots. Rebuild
        // only the selected invoice at actual print/view time, never all
        // historical invoices during startup. Immutable v1.3 is untouched.
        try(PreparedStatement p=c.prepareStatement("""
                SELECT 1 FROM invoices WHERE id=? AND
                    (snapshot_version IS NULL OR snapshot_version=0) AND
                    (customer_snapshot IS NULL OR company_snapshot IS NULL)
                """)) {
            p.setInt(1,invoice.getId());
            try(ResultSet rows=p.executeQuery()){
                if(rows.next())capture(c,invoice.getId(),true);
            }
        }
        try(PreparedStatement p=c.prepareStatement("SELECT customer_snapshot FROM invoices WHERE id=?")) {
            p.setInt(1,invoice.getId());try(ResultSet r=p.executeQuery()) {
                if(!r.next())throw new SQLException("Invoice no longer exists");
                Properties v=decode(r.getString(1));
                return new Customer(invoice.getCustomerId(),v.getProperty("first_name",""),v.getProperty("last_name",""),
                    v.getProperty("email",""),v.getProperty("phone",""),v.getProperty("address",""));
            }
        }
    }
}
