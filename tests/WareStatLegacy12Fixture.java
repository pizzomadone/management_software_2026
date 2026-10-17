import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/** SQL DDL extracted from the actual WareStat 1.2 release branch. */
public final class WareStatLegacy12Fixture {
    private WareStatLegacy12Fixture() {}
    public static Path create(Path db) throws Exception {
        Files.deleteIfExists(db);
        String schema=Files.readString(Path.of("tests/fixtures/warestat-1.2-schema.sql"));
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db.toAbsolutePath());
            Statement s=c.createStatement()){
            s.execute("PRAGMA foreign_keys=ON");
            for(String ddl:schema.split(";")){
                if(!ddl.isBlank())s.execute(ddl.trim());
            }
            // These columns were added by DatabaseManager 1.2 at first start.
            s.execute("ALTER TABLE orders ADD COLUMN payment_status TEXT DEFAULT 'NOT_PAID'");
            s.execute("ALTER TABLE orders ADD COLUMN paid_amount REAL DEFAULT 0.0");
            s.executeUpdate("INSERT INTO suppliers(id,company_name,vat_number) VALUES(1,'WareStat 1.2 Supplier','TEST12-VAT')");
            s.executeUpdate("INSERT INTO customers(id,first_name,last_name,email) VALUES(1,'Historic','Customer','historic@example.invalid')");
            s.executeUpdate("INSERT INTO products(id,code,name,price,quantity,reserved_quantity,category,acquisition_cost,supplier_id,vat_rate) VALUES(1,'OLD12','Product from actual 1.2 schema',100,9,0,'Legacy',60,1,22)");
            s.executeUpdate("INSERT INTO supplier_price_lists(supplier_id,product_id,price,validity_start_date) VALUES(1,1,60,0)");
            s.executeUpdate("INSERT INTO orders(id,customer_id,order_date,status,total,payment_status,paid_amount) VALUES(1,1,0,'Completed',122,'PAID',122)");
            s.executeUpdate("INSERT INTO order_details(id,order_id,product_id,quantity,unit_price) VALUES(1,1,1,1,100)");
            s.executeUpdate("INSERT INTO invoices(id,number,date,customer_id,taxable_amount,vat,total,status) VALUES(1,'OLD12-INVOICE',0,1,100,22,122,'Issued')");
            s.executeUpdate("INSERT INTO invoice_details(id,invoice_id,product_id,quantity,unit_price,vat_rate,total) VALUES(1,1,1,1,100,22,122)");
            s.executeUpdate("INSERT INTO supplier_orders(id,supplier_id,number,order_date,status,total) VALUES(1,1,'OLD-PO-1',0,'New',120)");
            s.executeUpdate("INSERT INTO supplier_order_details(id,order_id,product_id,quantity,unit_price,total) VALUES(1,1,1,2,60,120)");
            s.executeUpdate("INSERT INTO company_data(id,company_name,vat_number) VALUES(1,'Historic Company','HISTORIC-VAT')");
            s.executeUpdate("INSERT INTO warehouse_movements(id,product_id,date,type,quantity,reason) VALUES(1,1,0,'INWARD',10,'Legacy 1.2')");
            s.executeUpdate("INSERT INTO minimum_stock(product_id,minimum_quantity,reorder_quantity) VALUES(1,3,10)");
        }
        WareStatLegacy12.validate(db);
        return db;
    }
}
