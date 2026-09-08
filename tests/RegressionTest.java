import java.sql.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.lang.reflect.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Regression checks use synthetic databases and invoke the application's actual save methods.
 * Native windows are bypassed so the suite can run on build servers.
 */
public class RegressionTest {
    static Connection db;
    static final Path OUT=Path.of(System.getProperty("test.output"));
    static final List<String> results=new ArrayList<>();
    static int failures;
    static sun.misc.Unsafe unsafe;
    interface Work { String run() throws Exception; }
    public static class OD extends OrderDialog {
        public OD(){super((JFrame)null,null);} public void dispose(){}
    }
    public static class SD extends SupplierOrderDialog {
        public SD(){super((JDialog)null,1,"Supplier",null);} public void dispose(){}
    }
    public static class ID extends InvoiceDialog {
        public ID(){super((JFrame)null,null);} public void dispose(){}
    }
    public static class PD extends ProductDialog {
        public PD(){super((JFrame)null,null);} public void dispose(){}
    }
    static Field field(Object o,String n)throws Exception{
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass()){
            try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
            catch(NoSuchFieldException e){}
        }throw new NoSuchFieldException(n);
    }
    static void put(Object o,String n,Object v)throws Exception{field(o,n).set(o,v);}
    static Object get(Object o,String n)throws Exception{return field(o,n).get(o);}
    static Object call(Object o,String n,Class<?>[] types,Object...args)throws Exception{
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass()){
            try{Method m=c.getDeclaredMethod(n,types);m.setAccessible(true);
                try{return m.invoke(o,args);}catch(InvocationTargetException e){
                    Throwable t=e.getCause();if(t instanceof Exception x)throw x;throw e;}
            }catch(NoSuchMethodException e){}
        }throw new NoSuchMethodException(n);
    }
    static Object call(Object o,String n)throws Exception{return call(o,n,new Class<?>[0]);}
    static <T>T empty(Class<T> c)throws Exception{return c.cast(unsafe.allocateInstance(c));}
    static void sql(String s)throws Exception{try(Statement st=db.createStatement()){st.execute(s);}}
    static String value(String s)throws Exception{
        try(Statement st=db.createStatement();ResultSet rs=st.executeQuery(s)){return rs.next()?rs.getString(1):null;}
    }
    static int qty()throws Exception{try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT quantity FROM products WHERE id=1")){r.next();return r.getInt(1);}}
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void fresh()throws Exception{
        if(db!=null)db.close();
        db=DatabaseManager.openConnection("jdbc:sqlite::memory:");
        sql("PRAGMA foreign_keys=ON");
        put(DatabaseManager.getInstance(),"connection",db);
        call(DatabaseManager.getInstance(),"createTables");DatabaseCurrency.bind(db);
        sql("INSERT INTO customers(id,first_name,last_name) VALUES(1,'Test','Customer')");
        sql("INSERT INTO suppliers(id,company_name,vat_number) VALUES(1,'Supplier','TEST1'),(2,'Other','TEST2')");
        sql("INSERT INTO products(id,code,name,description,price,quantity,acquisition_cost,supplier_id) VALUES(1,'A','Product A','',10,10,5,1),(2,'B','Product B','',20,10,8,2)");
    }
    static Customer customer(){return new Customer(1,"Test","Customer","","","");}
    static String today(){return new SimpleDateFormat("dd/MM/yyyy").format(new java.util.Date());}
    static void completed()throws Exception{
        sql("INSERT INTO orders(id,customer_id,order_date,status,total) VALUES(1,1,"+System.currentTimeMillis()+",'Completed',30)");
        sql("INSERT INTO order_details(order_id,product_id,quantity,unit_price) VALUES(1,1,3,10)");
        StockManager.createOrUpdateReservation(db,1,"ORDER",1,3,"");
        StockManager.completeReservationAndDecrementStock(db,"ORDER",1,new java.util.Date(),"1");
        require(qty()==7,"Invalid completed fixture");
    }
    static OD order(boolean existing,Object quantity,String status)throws Exception{
        OD d=empty(OD.class);
        put(d,"order",existing?new Order(1,1,"Test Customer",new java.util.Date(),"Completed",30):null);
        put(d,"previousStatus",existing?"Completed":null);
        put(d,"selectedCustomer",customer());put(d,"dateField",new DatePickerField(today()));
        put(d,"dateFormat",new SimpleDateFormat("dd/MM/yyyy"));
        put(d,"statusCombo",new JComboBox<>(new String[]{status}));
        put(d,"paymentStatusCombo",new JComboBox<>(new String[]{"Not Paid"}));
        put(d,"paidAmountField",new JTextField("0"));
        DefaultTableModel m=new DefaultTableModel(new Object[][]{{1,"Product A",quantity,"10.00","30.00"}},new String[]{"ID","Name","Qty","Price","Total"});
        put(d,"itemsTableModel",m);put(d,"itemsTable",new JTable(m));
        double total=0;try{total=Integer.parseInt(quantity.toString())*10.0;}catch(Exception e){}
        put(d,"totalLabel",new JLabel());put(d,"currentTotal",total);put(d,"productsCache",new HashMap<>());
        return d;
    }
    static SD supplier(boolean existing,Map<Integer,Product> cache)throws Exception{
        SD d=empty(SD.class);put(d,"supplierId",1);put(d,"supplierName","Supplier");
        put(d,"order",existing?new SupplierOrder(1,1,"Supplier","OF20260001",new java.util.Date(),null,"Completed",30,""):null);
        put(d,"previousStatus",existing?"Completed":null);put(d,"numberField",new JTextField("OF20260001"));
        put(d,"dateField",new DatePickerField(today()));put(d,"deliveryDateField",new DatePickerField(""));
        put(d,"dateFormat",new SimpleDateFormat("dd/MM/yyyy"));put(d,"statusCombo",new JComboBox<>(new String[]{"Completed"}));
        put(d,"notesArea",new JTextArea(""));put(d,"productsCache",cache);
        DefaultTableModel m=new DefaultTableModel(new Object[][]{{"A","Product A",3,"10.00","30.00",""}},new String[]{"Code","Name","Qty","Price","Total","Notes"});
        put(d,"itemsTableModel",m);put(d,"itemsTable",new JTable(m));put(d,"totalLabel",new JLabel());return d;
    }
    static ID invoice()throws Exception{
        ID d=empty(ID.class);put(d,"selectedCustomer",customer());put(d,"numberField",new JTextField(""));
        put(d,"dateField",new DatePickerField(today()));put(d,"dateFormat",new SimpleDateFormat("dd/MM/yyyy"));
        put(d,"statusCombo",new JComboBox<>(new String[]{"Issued"}));put(d,"productsCache",new HashMap<Integer,Product>());
        DefaultTableModel m=new DefaultTableModel(new Object[][]{{"A","Product A",3,"10.00","22.00","30.00"}},new String[]{"Code","Name","Qty","Price","VAT","Total"});
        put(d,"itemsTableModel",m);put(d,"itemsTable",new JTable(m));
        put(d,"taxableAmountLabel",new JLabel());put(d,"vatLabel",new JLabel());put(d,"totalLabel",new JLabel());put(d,"currentTaxableAmount",30.0);put(d,"currentVat",6.6);put(d,"currentTotal",36.6);return d;
    }
    interface Check { void run() throws Exception; }
    static int checks;
    static void test(String name, Check work) {
        try { work.run(); checks++; System.out.println("PASS " + name); }
        catch (Throwable e) { failures++; System.err.println("FAIL " + name); e.printStackTrace(); }
    }
    static void rejectedSave(Object dialog, String method, String savedField) throws Exception {
        try { call(dialog,method); }
        catch (java.awt.HeadlessException | NullPointerException expectedMessageWindow) { }
        require(!Boolean.TRUE.equals(get(dialog,savedField)), "Invalid document was saved");
    }
    static void runAll() throws Exception {
        test("completed saves are idempotent", () -> {
            fresh(); completed();
            for(int i=0;i<3;i++) { call(order(true,3,"Completed"),"saveOrder"); require(qty()==7,"Repeated stock decrement"); }
            require("1".equals(value("SELECT COUNT(*) FROM warehouse_movements")),"Duplicated movement");
        });
        test("editing and cancelling restores original quantity", () -> {
            fresh(); completed(); call(order(true,5,"Cancelled"),"saveOrder"); require(qty()==10,"Wrong stock reversal");
            require("0".equals(value("SELECT COUNT(*) FROM warehouse_movements")),"Stale movement");
        });
        test("all customer order state transitions preserve balances", () -> {
            for(String from:List.of("New","In Progress","Completed","Cancelled"))
                for(String to:List.of("New","In Progress","Completed","Cancelled")) {
                    fresh(); call(order(false,3,from),"saveOrder");
                    OD changed=order(true,5,to);call(changed,"saveOrder");
                    require(qty()==("Completed".equals(to)?5:10), from+" -> "+to+" wrong physical stock");
                    require(value("SELECT reserved_quantity FROM products WHERE id=1").equals(
                        ("New".equals(to)||"In Progress".equals(to))?"5":"0"), "Wrong reserved quantity");
                }
        });
        test("changing product reverses the original product", () -> {
            fresh();completed();OD d=order(true,5,"Completed");DefaultTableModel m=(DefaultTableModel)get(d,"itemsTableModel");
            m.setValueAt(2,0,0);m.setValueAt("Product B",0,1);call(d,"saveOrder");
            require(qty()==10 && "5".equals(value("SELECT quantity FROM products WHERE id=2")),"Wrong product stock");
        });
        test("cancelled rows do not create phantom availability", () -> {
            fresh();sql("UPDATE products SET quantity=3 WHERE id=1");call(order(false,8,"Cancelled"),"saveOrder");
            var shortage=StockManager.checkStockAvailability(db,List.of(new StockManager.StockItem(1,"A",8)),1,"ORDER");
            require(!shortage.isEmpty(),"Cancelled order inflated stock");
        });
        test("active table editor is committed before totals and persistence", () -> {
            fresh();OD d=order(false,3,"Completed");JTable table=(JTable)get(d,"itemsTable");
            require(table.editCellAt(0,2),"Cannot start editor");((JTextField)table.getEditorComponent()).setText("5");
            call(d,"saveOrder");require(qty()==5,"Editor value ignored");
            require("50.0".equals(value("SELECT total FROM orders")),"Wrong header total");
        });
        test("invalid quantities and non-finite payments never persist", () -> {
            for(Object q:List.of(-3,0,"2.5","","2147483648")) {
                fresh();rejectedSave(order(false,q,"Completed"),"saveOrder","orderSaved");
                require(qty()==10 && "0".equals(value("SELECT COUNT(*) FROM orders")),"Partial invalid order");
            }
            fresh();OD d=order(false,3,"New");put(d,"paymentStatusCombo",new JComboBox<>(new String[]{"Partially Paid"}));
            put(d,"paidAmountField",new JTextField("NaN"));rejectedSave(d,"saveOrder","orderSaved");
            require("0".equals(value("SELECT COUNT(*) FROM orders")),"NaN stored");
        });
        test("new invoice product resolves despite empty cache", () -> {
            fresh();ID d=invoice();call(d,"saveInvoice");
            require("1".equals(value("SELECT COUNT(*) FROM invoice_details")),"Lost invoice row");
            require("36.6".equals(value("SELECT total FROM invoices")),"Invoice totals differ");
        });
        test("supplier reassignment preserves existing purchase rows and stock", () -> {
            fresh();SD d=supplier(false,new HashMap<>());call(d,"saveOrder");require(qty()==13,"Receipt failed");
            sql("UPDATE products SET supplier_id=2 WHERE id=1");SD again=supplier(true,new HashMap<>());
            put(again,"order",new SupplierOrder(1,1,"Supplier",value("SELECT number FROM supplier_orders"),new java.util.Date(),null,"Completed",30,""));
            ((JTextField)get(again,"numberField")).setText(value("SELECT number FROM supplier_orders"));
            call(again,"saveOrder");require(qty()==13,"Reassignment lost received stock");
            require("1".equals(value("SELECT COUNT(*) FROM supplier_order_details")),"Lost purchase row");
        });
        test("automatic movements cannot be edited independently", () -> {
            fresh();completed();boolean blocked=false;
            try { StockManager.requireManualMovement(db,Integer.parseInt(value("SELECT id FROM warehouse_movements"))); }
            catch(SQLException e){blocked=true;}
            require(blocked && qty()==7,"Automatic movement is editable");
        });
        test("overflow aborts the entire receiving transaction", () -> {
            fresh();SD d=supplier(false,new HashMap<>());
            ((DefaultTableModel)get(d,"itemsTableModel")).setValueAt(Integer.MAX_VALUE,0,2);
            rejectedSave(d,"saveOrder","orderSaved");
            require(qty()==10 && "0".equals(value("SELECT COUNT(*) FROM supplier_orders")),"Overflow left partial changes");
        });
        test("dates reject impossible days and trailing text", () -> {
            for(String input:List.of("31/02/2026","08/09/2026junk")) {
                boolean blocked=false;try{DateUtils.parseDate(input,new SimpleDateFormat("dd/MM/yyyy"));}catch(java.text.ParseException e){blocked=true;}
                require(blocked,"Invalid date accepted: "+input);
            }
        });
        test("decimal calculations preserve unit precision", () -> {
            boolean precisionRejected=false;try{Numbers.nonNegative("0.123456789012345678901");}catch(IllegalArgumentException e){precisionRejected=true;}
            require(precisionRejected,"Unsupported decimal precision was silently lost");
            require(Numbers.lineTotal(1000,0.004)==4.0,"Early price rounding");
            require(Numbers.tax(10000,8.25)==825.0,"VAT precision lost");
            require(Numbers.lineTotal(3,0.335)==1.01,"Half-up rounding differs");
        });
        test("supplier sequence excludes year digits", () -> {
            fresh();sql("INSERT INTO supplier_orders(supplier_id,number,order_date,status,total) VALUES(1,'OF20260001',0,'Draft',0)");
            require("OF20260002".equals(DocumentNumbers.nextSupplierNumber(db,new SimpleDateFormat("yyyy-MM-dd").parse("2026-09-08"))),"Wrong sequence");
        });
        test("supplier pricing respects dates and minimum quantities", () -> {
            fresh();long start=System.currentTimeMillis()-86400000L,end=System.currentTimeMillis()+86400000L;
            sql("INSERT INTO supplier_price_lists(supplier_id,product_id,price,minimum_quantity,validity_start_date,validity_end_date) VALUES(1,1,2,1,"+start+","+end+"),(1,1,1,100,"+start+","+end+"),(1,1,99,1,"+(end+86400000L)+",NULL)");
            require(SupplierPricing.price(db,1,1,3,new java.util.Date())==2,"Ignored date/minimum quantity");
            require(SupplierPricing.price(db,1,1,100,new java.util.Date())==1,"Missing quantity break");
        });
        test("WAL import includes committed data and retains a prior copy", () -> {
            if(db!=null)db.close();Path current=AppConstants.getDatabasePath();Files.deleteIfExists(current);
            DatabaseManager.getInstance().initDatabase();db=DatabaseManager.getInstance().getConnection();
            BackupManager b=BackupManager.getInstance();Path source=OUT.resolve("open-wal.db");b.exportDatabase(source);
            try(Connection c=DatabaseManager.openConnection("jdbc:sqlite:"+source);Statement st=c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");st.execute("PRAGMA wal_autocheckpoint=0");
                st.execute("INSERT INTO customers(first_name,last_name) VALUES('WAL','Committed')");
                b.importDatabase(source);db=DatabaseManager.getInstance().getConnection();
                require("1".equals(value("SELECT COUNT(*) FROM customers")),"Committed WAL data lost");
            }
        });
        test("incompatible imports leave live data intact", () -> {
            Path invalid=OUT.resolve("invalid-schema.db");
            try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+invalid);Statement s=c.createStatement()) {
                s.execute("CREATE TABLE products(id INTEGER)");s.execute("CREATE TABLE orders(id INTEGER)");
            }
            boolean blocked=false;try{BackupManager.getInstance().importDatabase(invalid);}catch(RuntimeException e){blocked=true;}
            require(blocked,"Invalid schema accepted");require("1".equals(value("SELECT COUNT(*) FROM customers")),"Current data changed");
        });
        test("retention only removes managed backup files", () -> {
            BackupManager b=BackupManager.getInstance();Path folder=OUT.resolve("backups");Files.createDirectories(folder);
            Path unrelated=folder.resolve("unrelated.db");Files.writeString(unrelated,"unrelated data");
            Files.setLastModifiedTime(unrelated,FileTime.fromMillis(1));b.setBackupDirectory(folder.toString());b.performBackup();
            require(Files.exists(unrelated),"Unrelated database deleted");
            boolean blocked=false;try{b.importBackup(AppConstants.getDatabasePath());}catch(RuntimeException e){blocked=true;}
            require(blocked,"Live database registered as backup");
            var imported=b.importBackup(OUT.resolve("open-wal.db"));
            require(!Files.isSameFile(Path.of(imported.absolutePath()),OUT.resolve("open-wal.db")),"Imported backup is only a reference");
            b.deleteBackup(imported.id());require(Files.exists(OUT.resolve("open-wal.db")),"Deleted external original");
        });
    }
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Files.createDirectories(OUT);
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);
        SwingUtilities.invokeAndWait(()->{try{runAll();ExtendedRegressionTest.extended();OperationalRegressionTest.operational();}catch(Exception e){e.printStackTrace();failures++;}});
        System.out.println("Regression checks: " + checks + " passed, " + failures + " failed");
        System.exit(failures==0?0:1);
    }
}
