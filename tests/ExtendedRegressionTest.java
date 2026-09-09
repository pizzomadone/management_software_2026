import java.sql.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/** Financial, reporting, upgrade and document regression scenarios. */
public class ExtendedRegressionTest extends RegressionTest {
    public static class MS extends MinimumStockDialog {
        public MS() { super((JFrame)null, null); }
        public void dispose() {}
    }
    static MS minimumEditor(MinimumStock settings, int minimum) throws Exception {
        MS dialog=empty(MS.class);put(dialog,"minStock",settings);
        put(dialog,"minQuantitySpinner",new JSpinner(new SpinnerNumberModel(minimum,0,Integer.MAX_VALUE,1)));
        put(dialog,"reorderQuantitySpinner",new JSpinner(new SpinnerNumberModel(Math.max(minimum,settings.getReorderQuantity()),0,Integer.MAX_VALUE,1)));
        put(dialog,"leadTimeSpinner",new JSpinner(new SpinnerNumberModel(0,0,365,1)));
        put(dialog,"supplierCombo",new JComboBox<>());put(dialog,"notesArea",new JTextArea(""));
        return dialog;
    }
    static void legacyThresholdSchema(String firstPending) throws Exception {
        sql("ALTER TABLE products ADD COLUMN minimum_quantity INTEGER DEFAULT 0");
        sql("DELETE FROM schema_migrations WHERE version>='"+firstPending+"'");
        if(firstPending.equals("010")) {
            java.lang.reflect.Method method=DatabaseMigrationManager.class.getDeclaredMethod("migrateStockThresholds",Connection.class);
            method.setAccessible(true);method.invoke(null,db);
        }
        for(String action:List.of("INSERT","UPDATE")) {
            sql("DROP TRIGGER numeric_guard_products_"+action);
            sql("CREATE TRIGGER numeric_guard_products_"+action+" BEFORE "+action+" ON products " +
                "WHEN typeof(NEW.minimum_quantity)<>'integer' OR NEW.minimum_quantity NOT BETWEEN 0 AND 2147483647 " +
                "BEGIN SELECT RAISE(ABORT,'Invalid legacy minimum'); END");
        }
    }
    static void requireNormalizedThresholdSchema() throws Exception {
        require("0".equals(value("SELECT COUNT(*) FROM pragma_table_info('products') WHERE name='minimum_quantity'")),"Duplicate product column remains");
        require("0".equals(value("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name IN ('minimum_stock_insert','minimum_stock_update','product_minimum_update')")),"Synchronization triggers remain");
        require("1".equals(value("SELECT COUNT(*) FROM schema_migrations WHERE version='010'")),"Migration was not recorded exactly once");
        DatabaseIntegrity.validate(db,false);
    }
    static String pdfText(Path file) throws Exception {
        try(PDDocument doc=Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper=new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition text) {
                    require(text.getXDirAdj()>=35 && text.getXDirAdj()+text.getWidthDirAdj()<getCurrentPage().getMediaBox().getWidth()-30,"PDF text crosses horizontal page bounds");
                    require(text.getYDirAdj()>=25 && text.getYDirAdj()<getCurrentPage().getMediaBox().getHeight()-20,"PDF text crosses vertical page bounds");
                    super.processTextPosition(text);
                }
            };
            return stripper.getText(doc);
        }
    }
    static void extended() throws Exception {
        test("all supplier order state transitions preserve receipts",()->{
            for(String from:List.of("Draft","Confirmed","In Transit","Completed","Cancelled"))
                for(String to:List.of("Draft","Confirmed","In Transit","Completed","Cancelled")) {
                    fresh();SD first=supplier(false,new HashMap<>());put(first,"statusCombo",new JComboBox<>(new String[]{from}));call(first,"saveOrder");
                    SD second=supplier(true,new HashMap<>());put(second,"statusCombo",new JComboBox<>(new String[]{to}));call(second,"saveOrder");
                    require(qty()==(to.equals("Completed")?13:10),from+" -> "+to+" changed stock incorrectly");
                }
        });
        test("invoice rounding agrees between lines and saved header",()->{
            fresh();ID d=invoice();DefaultTableModel m=(DefaultTableModel)get(d,"itemsTableModel");m.setValueAt("0.335",0,3);call(d,"saveInvoice");
            require("1.01".equals(value("SELECT taxable_amount FROM invoices")),"Taxable rounding differs");
            require("1.23".equals(value("SELECT total FROM invoices")),"VAT rounding differs");
        });
        test("completed sales exclude cancelled documents and have no 1000 row cap",()->{
            fresh();db.setAutoCommit(false);
            for(int i=0;i<1005;i++)sql("INSERT INTO orders(customer_id,order_date,status,total) VALUES(1,"+System.currentTimeMillis()+",'Completed',1)");
            sql("INSERT INTO orders(customer_id,order_date,status,total) VALUES(1,"+System.currentTimeMillis()+",'Cancelled',900)");db.commit();db.setAutoCommit(true);
            SalesReportPanel p=new SalesReportPanel();call(p,"loadReportData");
            require(((DefaultTableModel)get(p,"tableModel")).getRowCount()==1005,"Sales rows="+((DefaultTableModel)get(p,"tableModel")).getRowCount()+", today="+today()+", bounds="+((DatePickerField)get(p,"startDateField")).getText()+".."+((DatePickerField)get(p,"endDateField")).getText()+", SQL day="+value("SELECT "+DateUtils.sqlDate("order_date")+" FROM orders LIMIT 1"));
            require(((JLabel)get(p,"totalSalesLabel")).getText().contains("1,005.00"),"Sales total differs");
        });
        test("dominant product belongs to ABC class A",()->{
            fresh();completed();sql("DELETE FROM products WHERE id=2");DashboardPanel p=new DashboardPanel(null);call(p,"loadABCAnalysis");
            JTable t=(JTable)get(p,"abcAnalysisTable");require(((Number)t.getModel().getValueAt(0,1)).intValue()==1,"Dominant product is not A");
        });
        test("top ten percentages include all products in the denominator",()->{
            fresh();sql("INSERT INTO orders(id,customer_id,order_date,status,total) VALUES(1,1,"+System.currentTimeMillis()+",'Completed',200)");
            for(int i=1;i<=20;i++) {
                if(i>2)sql("INSERT INTO products(id,code,name,price) VALUES("+i+",'P"+i+"','Product "+i+"',10)");
                sql("INSERT INTO order_details(order_id,product_id,quantity,unit_price) VALUES(1,"+i+",1,10)");
            }
            AdvancedStatsPanel p=new AdvancedStatsPanel();call(p,"loadProductsData");
            JTable t=(JTable)get(p,"topProductsTable");require(t.getRowCount()==10 && "5.0%".equals(t.getValueAt(0,3)),"Wrong market share");
            require((double)get(p,"productsTotalRevenue")==200,"Chart denominator differs from table");
        });
        test("both editors use the warehouse threshold without overwriting reorder settings",()->{
            fresh();sql("INSERT INTO minimum_stock(product_id,minimum_quantity,reorder_quantity) VALUES(1,5,10)");
            sql("UPDATE minimum_stock SET reorder_quantity=30,lead_time_days=6,preferred_supplier_id=2,notes='Keep these settings' WHERE product_id=1");
            PD product=OperationalRegressionTest.productEditor(10);((JSpinner)get(product,"minimumQuantitySpinner")).setValue(14);call(product,"saveProduct");
            require(Boolean.TRUE.equals(get(product,"productSaved")),"Product editor did not save");
            require("14|30|6|2|Keep these settings".equals(value("SELECT minimum_quantity||'|'||reorder_quantity||'|'||lead_time_days||'|'||preferred_supplier_id||'|'||notes FROM minimum_stock WHERE product_id=1")),"Product edit changed other reorder settings");
            WarehousePanel warehouse=new WarehousePanel();
            MinimumStock settings=(MinimumStock)call(warehouse,"loadMinimumStock",new Class<?>[]{String.class},"A");
            require(settings.getMinimumQuantity()==14 && settings.getReorderQuantity()==30,"Warehouse editor reads another threshold");
            MS edit=minimumEditor(settings,18);call(edit,"saveStock");
            require(Boolean.TRUE.equals(get(edit,"stockSaved")) && "18".equals(value("SELECT minimum_quantity FROM minimum_stock WHERE product_id=1")),"Warehouse editor did not save");
            ProductsPanel catalog=new ProductsPanel();DefaultTableModel products=(DefaultTableModel)get(catalog,"tableModel");
            require(products.getValueAt(0,11).equals(18),"Product table reads another threshold");
            ((JTextField)get(catalog,"searchField")).setText("Product A");call(catalog,"searchProducts");
            require(products.getRowCount()==1 && products.getValueAt(0,11).equals(18),"Product search lost the threshold");
            call(warehouse,"loadStockData");require(((DefaultTableModel)get(warehouse,"stockModel")).getValueAt(0,5).equals(18),"Warehouse table differs");
            DashboardPanel dashboard=new DashboardPanel(null);call(dashboard,"loadKPIData");
            require(((JLabel)get(dashboard,"lowStockCountLabel")).getText().equals("1"),"Dashboard differs");
            WarehouseReportPanel report=new WarehouseReportPanel();
            require(((DefaultTableModel)get(report,"productsModel")).getValueAt(0,5).equals("LOW STOCK"),"Warehouse report differs");
            db.setAutoCommit(false);
            try {
                sql("UPDATE minimum_stock SET minimum_quantity=21 WHERE product_id=1");
                db.rollback();
            } finally {db.setAutoCommit(true);}
            require("18".equals(value("SELECT minimum_quantity FROM minimum_stock WHERE product_id=1")),"Rollback lost the threshold");
            requireNormalizedThresholdSchema();
        });
        test("missing and zero thresholds remain disabled and product saves create warehouse settings",()->{
            fresh();requireNormalizedThresholdSchema();
            require("0".equals(value("SELECT COUNT(*) FROM minimum_stock")),"Fixture already has warehouse settings");
            WarehousePanel warehouse=new WarehousePanel();
            MinimumStock settings=(MinimumStock)call(warehouse,"loadMinimumStock",new Class<?>[]{String.class},"A");
            require(settings.getMinimumQuantity()==0,"Missing settings did not disable the threshold");
            PD product=OperationalRegressionTest.productEditor(10);((JSpinner)get(product,"minimumQuantitySpinner")).setValue(14);call(product,"saveProduct");
            require("14".equals(value("SELECT minimum_quantity FROM minimum_stock WHERE product_id=1")),"Product threshold was lost without prior settings");
            call(product,"saveProduct");require("1".equals(value("SELECT COUNT(*) FROM minimum_stock")),"Repeated save created duplicate settings");
            MS clear=minimumEditor(settings,0);call(clear,"saveStock");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");require(((JLabel)get(dash,"lowStockCountLabel")).getText().equals("0"),"Zero threshold was not disabled");
            sql("DELETE FROM minimum_stock WHERE product_id=1");
            settings=(MinimumStock)call(warehouse,"loadMinimumStock",new Class<?>[]{String.class},"A");
            require(settings.getMinimumQuantity()==0,"Deleted settings retained a second threshold");
            MS first=minimumEditor(settings,7);call(first,"saveStock");
            require(Boolean.TRUE.equals(get(first,"stockSaved")) && "7".equals(value("SELECT minimum_quantity FROM minimum_stock WHERE product_id=1")),"First warehouse settings save failed");
            PD added=OperationalRegressionTest.productEditor(0);put(added,"product",null);
            ((JTextField)get(added,"codeField")).setText("NEW");((JTextField)get(added,"nameField")).setText("New product");
            ((JSpinner)get(added,"minimumQuantitySpinner")).setValue(9);call(added,"saveProduct");
            require(Boolean.TRUE.equals(get(added,"productSaved")) && "9".equals(value("SELECT sm.minimum_quantity FROM minimum_stock sm JOIN products p ON p.id=sm.product_id WHERE p.code='NEW'")),"New product threshold did not reach warehouse settings");
            ProductsPanel catalog=new ProductsPanel();
            int newId=Integer.parseInt(value("SELECT id FROM products WHERE code='NEW'"));
            call(catalog,"deleteProductAndNonCriticalData",new Class<?>[]{Connection.class,int.class},db,newId);
            require("0".equals(value("SELECT COUNT(*) FROM minimum_stock WHERE product_id="+newId)),"Product deletion left orphaned settings");
            requireNormalizedThresholdSchema();
        });
        test("failed threshold saves roll back product changes and inventory movements",()->{
            for(boolean newProduct:List.of(false,true)) {
                fresh();sql("INSERT INTO minimum_stock(product_id,minimum_quantity,reorder_quantity) VALUES(1,7,20)");
                sql("CREATE TRIGGER reject_minimum_write BEFORE INSERT ON minimum_stock BEGIN SELECT RAISE(ABORT,'Simulated settings write failure'); END");
                PD editor=OperationalRegressionTest.productEditor(15);
                ((JSpinner)get(editor,"minimumQuantitySpinner")).setValue(14);
                if(newProduct) {put(editor,"product",null);((JTextField)get(editor,"codeField")).setText("NEW");}
                try {call(editor,"saveProduct");}catch(RuntimeException expected) {
                    // The unconstructed headless dialog cannot display the save-error message.
                    require(Arrays.stream(expected.getStackTrace()).anyMatch(frame ->
                        frame.getClassName().equals("javax.swing.JOptionPane") && frame.getMethodName().equals("showMessageDialog")),
                        "Unexpected failure before save-error reporting: "+expected);
                }
                require(!Boolean.TRUE.equals(get(editor,"productSaved")),"Failed save reported success");
                require(qty()==10 && "2".equals(value("SELECT COUNT(*) FROM products")) && "1".equals(value("SELECT supplier_id FROM products WHERE id=1")),"Failed threshold write left product changes");
                require("0".equals(value("SELECT COUNT(*) FROM warehouse_movements")),"Failed threshold write left a stock adjustment");
                require("7|20".equals(value("SELECT minimum_quantity||'|'||reorder_quantity FROM minimum_stock WHERE product_id=1")) && "1".equals(value("SELECT COUNT(*) FROM minimum_stock")),"Failed save changed settings");
                require(db.getAutoCommit(),"Product save did not restore autocommit");requireNormalizedThresholdSchema();
            }
        });
        test("stock threshold migration preserves settings stock history and repeated upgrades",()->{
            fresh();completed();legacyThresholdSchema("010");
            sql("UPDATE products SET minimum_quantity=8 WHERE id=1");
            sql("INSERT INTO minimum_stock(product_id,minimum_quantity,reorder_quantity,lead_time_days,preferred_supplier_id,notes) VALUES(2,4,25,5,1,'Original reorder settings')");
            DatabaseMigrationManager.migrate(db);requireNormalizedThresholdSchema();
            require("8|0".equals(value("SELECT minimum_quantity||'|'||reorder_quantity FROM minimum_stock WHERE product_id=1")),"Product-only threshold lost");
            require("4|25|5|1|Original reorder settings".equals(value("SELECT minimum_quantity||'|'||reorder_quantity||'|'||lead_time_days||'|'||preferred_supplier_id||'|'||notes FROM minimum_stock WHERE product_id=2")),"Existing reorder settings changed");
            require(qty()==7 && "1|3|10.0".equals(value("SELECT product_id||'|'||quantity||'|'||unit_price FROM order_details WHERE order_id=1")),"Migration altered stock or order history");
            DatabaseMigrationManager.migrate(db);requireNormalizedThresholdSchema();
            require("2".equals(value("SELECT COUNT(*) FROM minimum_stock")),"Repeated upgrade duplicated settings");
            sql("UPDATE products SET price=12 WHERE id=1");sql("UPDATE minimum_stock SET minimum_quantity=12 WHERE product_id=1");
            require("12".equals(value("SELECT minimum_quantity FROM minimum_stock WHERE product_id=1")),"Old triggers broke post-upgrade writes");
        });
        test("legacy warehouse thresholds including zero win conflicts without losing other settings",()->{
            fresh();legacyThresholdSchema("007");
            sql("UPDATE products SET minimum_quantity=19");
            sql("INSERT INTO minimum_stock(product_id,minimum_quantity,reorder_quantity,lead_time_days,notes) VALUES(1,0,30,7,'Disabled'),(2,11,40,9,'Enabled')");
            DatabaseMigrationManager.migrate(db);requireNormalizedThresholdSchema();
            require("0|30|7|Disabled".equals(value("SELECT minimum_quantity||'|'||reorder_quantity||'|'||lead_time_days||'|'||notes FROM minimum_stock WHERE product_id=1")),"Disabled warehouse threshold was replaced by product minimum");
            require("11|40|9|Enabled".equals(value("SELECT minimum_quantity||'|'||reorder_quantity||'|'||lead_time_days||'|'||notes FROM minimum_stock WHERE product_id=2")),"Warehouse threshold lost the conflict");
        });
        test("failed stock migration restores the old schema values and triggers atomically",()->{
            fresh();legacyThresholdSchema("010");sql("UPDATE products SET minimum_quantity=8 WHERE id=1");
            sql("CREATE TRIGGER reject_stock_migration BEFORE INSERT ON schema_migrations WHEN NEW.version='010' BEGIN SELECT RAISE(ABORT,'Simulated migration history failure'); END");
            boolean blocked=false;try{DatabaseMigrationManager.migrate(db);}catch(SQLException e){blocked=true;}
            require(blocked,"Injected migration failure was ignored");
            require("8".equals(value("SELECT minimum_quantity FROM products WHERE id=1")) && "0".equals(value("SELECT COUNT(*) FROM minimum_stock")),"Failed upgrade partially moved the threshold");
            require("3".equals(value("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name IN ('minimum_stock_insert','minimum_stock_update','product_minimum_update')")),"Failed upgrade did not restore old triggers");
            require(db.getAutoCommit() && "1".equals(value("PRAGMA foreign_keys")) && "0".equals(value("SELECT COUNT(*) FROM schema_migrations WHERE version='010'")),"Failed migration left an invalid connection or history");
            sql("DROP TRIGGER reject_stock_migration");DatabaseMigrationManager.migrate(db);requireNormalizedThresholdSchema();
        });
        test("stock thresholds reject invalid legacy and new values and accept the integer limit",()->{
            for(String invalid:List.of("NULL","-1","2.5","2147483648")) {
                fresh();legacyThresholdSchema("010");sql("DROP TRIGGER numeric_guard_products_UPDATE");
                sql("UPDATE products SET minimum_quantity="+invalid+" WHERE id=1");
                boolean blocked=false;try{DatabaseMigrationManager.migrate(db);}catch(SQLException e){blocked=true;}
                require(blocked && "0".equals(value("SELECT COUNT(*) FROM minimum_stock")) && "0".equals(value("SELECT COUNT(*) FROM schema_migrations WHERE version='010'")),"Invalid legacy threshold was coerced or partly migrated: "+invalid);
            }
            fresh();legacyThresholdSchema("010");sql("UPDATE products SET minimum_quantity=2147483647 WHERE id=1");
            DatabaseMigrationManager.migrate(db);requireNormalizedThresholdSchema();
            require("2147483647".equals(value("SELECT minimum_quantity FROM minimum_stock WHERE product_id=1")),"Migration overflowed the threshold");
            for(String invalid:List.of("NULL","-1","2.5","2147483648")) {
                boolean blocked=false;try{sql("UPDATE minimum_stock SET minimum_quantity="+invalid+" WHERE product_id=1");}catch(SQLException e){blocked=true;}
                require(blocked,"New schema accepted an invalid threshold: "+invalid);
            }
        });
        test("advanced statistics exclude unfinished cancelled and out-of-period orders",()->{
            fresh();completed();
            for(String status:List.of("New","In Progress","Cancelled")) {
                sql("INSERT INTO orders(customer_id,order_date,status,total) VALUES(1,"+System.currentTimeMillis()+",'"+status+"',2000)");
                sql("INSERT INTO order_details(order_id,product_id,quantity,unit_price) VALUES((SELECT MAX(id) FROM orders),2,100,20)");
            }
            for(String date:List.of("2000-01-01",java.time.LocalDate.now().plusDays(1).toString())) {
                sql("INSERT INTO orders(customer_id,order_date,status,total) VALUES(1,'"+date+"','Completed',2000)");
                sql("INSERT INTO order_details(order_id,product_id,quantity,unit_price) VALUES((SELECT MAX(id) FROM orders),2,100,20)");
            }
            AdvancedStatsPanel panel=new AdvancedStatsPanel();
            for(int period=0;period<3;period++) {
                ((JComboBox<?>)get(panel,"periodCombo")).setSelectedIndex(period);call(panel,"loadData");
                @SuppressWarnings("unchecked") Map<String,double[]> sales=(Map<String,double[]>)get(panel,"monthlySales");
                require(sales.size()==1 && sales.values().iterator().next()[0]==30 && sales.values().iterator().next()[1]==1,"Monthly statistics include non-completed or out-of-period documents");
                JTable products=(JTable)get(panel,"topProductsTable");
                require(products.getRowCount()==1 && products.getValueAt(0,0).equals("Product A") && ((Number)products.getValueAt(0,1)).longValue()==3,"Product statistics include invalid order details");
                require((double)get(panel,"productsTotalRevenue")==30,"Product chart total differs from completed sales");
            }
            sql("UPDATE orders SET status='Cancelled' WHERE id=1");call(panel,"loadData");
            require(((Map<?,?>)get(panel,"monthlySales")).isEmpty() && ((JTable)get(panel,"topProductsTable")).getRowCount()==0 && (double)get(panel,"productsTotalRevenue")==0,"Cancelled sale remained in the charts");
        });
        test("PDF export finds embedded source-folder and bundled fallback fonts",()->{
            Path isolated=Files.createDirectories(OUT.resolve("pdf-packaging"));
            Path withFonts=isolated.resolve("with-fonts.jar"), withoutFonts=isolated.resolve("without-fonts.jar");
            for(Path jar:List.of(withFonts,withoutFonts)) {
                try(var out=new java.util.jar.JarOutputStream(Files.newOutputStream(jar))) {
                    List<String> resources=new ArrayList<>(List.of("PdfWriter.class","PdfPackagingProbe.class"));
                    if(jar.equals(withFonts))resources.addAll(List.of("fonts/DejaVuSans.ttf","fonts/DejaVuSans-Bold.ttf","fonts/LICENSE-DejaVu.txt"));
                    for(String name:resources) {
                        out.putNextEntry(new java.util.jar.JarEntry(name));
                        try(var in=PdfWriter.class.getResourceAsStream("/"+name)) {
                            require(in!=null,"Missing packaging test input: "+name);in.transferTo(out);
                        }
                        out.closeEntry();
                    }
                }
            }
            String javaExecutable=Path.of(System.getProperty("java.home"),"bin","java").toString();
            String pdfbox=Path.of(PDDocument.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            for(int mode=0;mode<3;mode++) {
                Path jar=mode==0?withFonts:withoutFonts;
                Path cwd=mode==1?Path.of("").toAbsolutePath():isolated;
                String expected=mode==2?"LiberationSans":"DejaVuSans";
                Path log=isolated.resolve("export-"+mode+".log");
                Process process=new ProcessBuilder(javaExecutable,"-Djava.awt.headless=true","-cp",jar.toAbsolutePath()+java.io.File.pathSeparator+pdfbox,
                    "PdfPackagingProbe",isolated.resolve("export-"+mode+".pdf").toAbsolutePath().toString(),expected)
                    .directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                if(!process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)) {process.destroyForcibly();throw new AssertionError("PDF packaging probe timed out");}
                require(process.exitValue()==0,"PDF packaging probe failed: "+Files.readString(log));
            }
        });
        test("movement reports include timestamp and SQL text dates",()->{
            fresh();completed();sql("INSERT INTO warehouse_movements(product_id,type,quantity,reason) VALUES(1,'INWARD',1,'Manual test')");
            WarehouseReportPanel p=new WarehouseReportPanel();require(((DefaultTableModel)get(p,"movementsModel")).getRowCount()==2,"Movement rows="+((DefaultTableModel)get(p,"movementsModel")).getRowCount()+", bounds="+((DatePickerField)get(p,"startDateField")).getText()+".."+((DatePickerField)get(p,"endDateField")).getText()+", SQL day="+value("SELECT "+DateUtils.sqlDate("date")+" FROM warehouse_movements LIMIT 1"));
        });
        test("business text is never translated",()->{
            I18n.setLanguage("it");try {
                JTable t=new JTable(new Object[][]{{"New","Back","Total"}},new String[]{"Code","Name","Note"});I18n.localize(t);
                for(int i=0;i<3;i++)require(t.getModel().getValueAt(0,i).equals(((JLabel)t.prepareRenderer(t.getCellRenderer(0,i),0,i)).getText()),"Business value translated");
                JButton selected=new JButton("Back");selected.putClientProperty("i18n.literal",true);I18n.localize(selected);require(selected.getText().equals("Back"),"Selected party translated");
            } finally {I18n.setLanguage("en");}
        });
        test("numeric and formatted currency sorting is numeric",()->{
            for(Locale locale:List.of(Locale.US,Locale.ITALY)) {
                Locale.setDefault(locale);
                JTable t=new JTable(new Object[][]{{100,SettingsPanel.formatCurrency(1000)},{20,SettingsPanel.formatCurrency(20)},{9,SettingsPanel.formatCurrency(9)}},new String[]{"Qty","Total"});TableSorterUtil.enableSorting(t);
                t.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(1,SortOrder.DESCENDING)));require(((Number)t.getValueAt(0,0)).intValue()==100,"Currency sorted as text");
                t.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(0,SortOrder.DESCENDING)));require(((Number)t.getValueAt(2,0)).intValue()==9,"Quantity sorted as text");
            }
            Locale.setDefault(Locale.US);
        });
        test("CSV preserves quotes commas and newlines and detects write failure",()->{
            require(Csv.cell("A,\"B\"\nC").equals("\"A,\"\"B\"\"\nC\""),"CSV escaped incorrectly");
            java.io.PrintWriter broken=new java.io.PrintWriter(new java.io.Writer(){public void write(char[] b,int o,int n)throws java.io.IOException{throw new java.io.IOException("Disk full");}public void flush(){}public void close(){}});
            broken.print("data");boolean blocked=false;try{Csv.finish(broken);}catch(java.io.IOException e){blocked=true;}require(blocked,"Writer failure reported as success");
        });
        test("price list lookup identifies the selected period by ID",()->{
            fresh();sql("INSERT INTO supplier_price_lists(id,supplier_id,product_id,supplier_product_code,price,validity_start_date,validity_end_date) VALUES(1,1,1,'SUP-A',5,1767225600000,1782777600000),(2,1,1,'SUP-A',8,1782864000000,NULL)");
            SupplierPriceListWindow w=empty(SupplierPriceListWindow.class);put(w,"supplierId",1);
            SupplierPriceList selected=(SupplierPriceList)call(w,"loadPriceListItem",new Class<?>[]{int.class},2);require(selected.getId()==2 && selected.getPrice()==8,"Selected price period lost");
        });
        test("legacy supplier migration preserves referenced products",()->{
            fresh();try(Connection c=DatabaseManager.openConnection("jdbc:sqlite::memory:");Statement s=c.createStatement()) {
                s.execute("CREATE TABLE suppliers(id INTEGER PRIMARY KEY,company_name TEXT)");s.execute("INSERT INTO suppliers VALUES(1,'Supplier')");
                s.execute("CREATE TABLE products(id INTEGER PRIMARY KEY,code TEXT,supplier TEXT)");s.execute("INSERT INTO products VALUES(1,'A','Supplier')");
                s.execute("CREATE TABLE detail(product_id INTEGER REFERENCES products(id))");s.execute("INSERT INTO detail VALUES(1)");
                java.lang.reflect.Method method=DatabaseMigrationManager.class.getDeclaredMethod("migrateSupplierData",Connection.class);method.setAccessible(true);method.invoke(null,c);
                try(ResultSet r=s.executeQuery("SELECT supplier_id FROM products WHERE id=1")){require(r.next() && r.getInt(1)==1,"Legacy supplier lost");}
                try(ResultSet r=s.executeQuery("PRAGMA foreign_key_check")){require(!r.next(),"Legacy reference broken");}
            }
        });
        test("invoice source order foreign key survives migration",()->{
            fresh();sql("DROP TABLE invoices");sql("CREATE TABLE invoices(id INTEGER PRIMARY KEY,number TEXT UNIQUE,date DATETIME,customer_id INTEGER REFERENCES customers(id),taxable_amount REAL,vat REAL,total REAL,status TEXT,order_id INTEGER)");
            sql("DELETE FROM schema_migrations WHERE version>='006'");DatabaseMigrationManager.migrate(db);
            boolean found=false;try(Statement s=db.createStatement();ResultSet r=s.executeQuery("PRAGMA foreign_key_list(invoices)")){while(r.next())if("order_id".equals(r.getString("from")))found=true;}
            require(found,"Missing order foreign key");
            boolean blocked=false;try{sql("INSERT INTO invoices(number,date,customer_id,taxable_amount,vat,total,status,order_id) VALUES('ORPHAN',0,1,0,0,0,'Draft',999)");}catch(SQLException e){blocked=true;}require(blocked,"Orphan invoice accepted");
        });
        test("database rejects non-finite and invalid persisted numbers",()->{
            fresh();for(String query:List.of("UPDATE products SET price=1e999 WHERE id=1","UPDATE products SET quantity=2.5 WHERE id=1","UPDATE products SET vat_rate=101 WHERE id=1","INSERT INTO supplier_orders(supplier_id,number,order_date,status,total) VALUES(1,'BAD',0,'Draft',-1)")) {
                boolean blocked=false;try{sql(query);}catch(SQLException e){blocked=true;}require(blocked,"Numeric domain accepted: "+query);
            }
            DatabaseIntegrity.validate(db,false);
        });
        test("changing currency cannot relabel an existing database",()->{
            fresh();String original=DatabaseCurrency.read(db);boolean blocked=false;try{DatabaseCurrency.change("USD");}catch(SQLException e){blocked=true;}
            require(blocked && original.equals(DatabaseCurrency.read(db)),"Existing prices relabelled");
            sql("DELETE FROM products");DatabaseCurrency.change("USD");require("USD".equals(SettingsPanel.getCurrencyCode()),"Empty database currency did not change");DatabaseCurrency.change(original);
        });
        test("invoice snapshots survive later party and catalog changes",()->{
            fresh();sql("UPDATE customers SET first_name='Łódź',last_name='Original' WHERE id=1");
            ID d=invoice();DefaultTableModel m=(DefaultTableModel)get(d,"itemsTableModel");m.setValueAt("5901234123457",0,0);m.setValueAt("Crème Łódź original",0,1);
            sql("UPDATE products SET code='5901234123457' WHERE id=1");call(d,"saveInvoice");
            sql("UPDATE products SET code='RENAMED',name='Changed' WHERE id=1");sql("UPDATE customers SET first_name='Changed' WHERE id=1");
            Invoice i=new Invoice(1,"PDF",new java.util.Date(),1,"",30,6.6,36.6,"Issued");
            Path file=OUT.resolve("invoice-unicode-barcode.pdf");call(new InvoicePDFGenerator(i,customer()),"generatePDF",new Class<?>[]{java.io.File.class},file.toFile());
            String text=pdfText(file);require(text.contains("Łódź Original") && text.contains("5901234123457") && text.contains("Crème Łódź original") && !text.contains("Changed"),"Historical invoice text changed or was truncated");
        });
        test("invoice PDF covers statuses languages sparse parties and VAT variants",()->{
            String previousLanguage=I18n.getLanguage();
            try {
                for(String status:List.of("Draft","Issued","Paid","Canceled")) {
                    fresh();
                    ID d=invoice();put(d,"statusCombo",new JComboBox<>(new String[]{status}));call(d,"saveInvoice");
                    Invoice i=new Invoice(1,"PDF",new java.util.Date(),1,"",30,6.6,36.6,status);
                    Path file=OUT.resolve("invoice-status-"+status+".pdf");
                    call(new InvoicePDFGenerator(i,customer()),"generatePDF",new Class<?>[]{java.io.File.class},file.toFile());
                    String text=pdfText(file);
                    require(text.contains(status) && text.contains("FROM") && text.contains("BILL TO"),"Missing invoice state or party section: "+status);
                    require(text.contains("10.00") && text.contains("TOTAL: EUR 36.60"),"Currency formatting changed: "+status);
                    require(!text.contains("null"),"Sparse party data leaked null text: "+status);
                }

                fresh();
                sql("UPDATE customers SET first_name='Very Long Customer Name With Unicode Łódź',last_name='Crème Société',address='123 Long Address Street, Building 45, Floor 6, Suite 789, International Business District' WHERE id=1");
                sql("INSERT INTO company_data(company_name,vat_number,tax_code,address,city,postal_code,country,phone,email,website) VALUES('WareStat Example Company With A Long Legal Name','IT12345678901','ABCDEF12G34H567I','456 Very Long Corporate Avenue, Industrial Park','Palermo','90100','Italia','+39 091 000000','billing@example.com','https://example.com')");
                ID longInvoice=invoice();call(longInvoice,"saveInvoice");
                Invoice longModel=new Invoice(1,"PDF",new java.util.Date(),1,"",30,6.6,36.6,"Issued");
                for(String language:List.of("en","it","es","pt_BR","fr","de","pl")) {
                    I18n.setLanguage(language);
                    Path file=OUT.resolve("invoice-language-"+language+".pdf");
                    call(new InvoicePDFGenerator(longModel,customer()),"generatePDF",new Class<?>[]{java.io.File.class},file.toFile());
                    String text=pdfText(file);
                    require(text.contains(I18n.tr("INVOICE")) && text.contains(I18n.tr("FROM")) &&
                        text.contains(I18n.tr("BILL TO")) && text.contains(I18n.tr("TOTAL")),
                        "Invoice labels not localized in "+language);
                    require(text.contains("Łódź") && text.contains("Crème Société"),"Unicode party data missing in "+language);
                }

                I18n.setLanguage("en");fresh();
                sql("INSERT INTO invoices(id,number,date,customer_id,taxable_amount,vat,total,status) VALUES(1,'VAT-MIX',"+System.currentTimeMillis()+",1,30,4.4,34.4,'Issued')");
                sql("INSERT INTO invoice_details(invoice_id,product_id,quantity,unit_price,vat_rate,total,product_code,product_name) VALUES(1,1,1,10,0,10,'A','Zero VAT'),(1,2,1,20,22,20,'B','Standard VAT')");
                InvoiceSnapshots.capture(db,1,false);
                Invoice mixed=new Invoice(1,"VAT-MIX",new java.util.Date(),1,"",30,4.4,34.4,"Issued");
                Path mixedFile=OUT.resolve("invoice-mixed-vat.pdf");
                call(new InvoicePDFGenerator(mixed,customer()),"generatePDF",new Class<?>[]{java.io.File.class},mixedFile.toFile());
                String mixedText=pdfText(mixedFile);
                require(mixedText.contains("Zero VAT") && mixedText.contains("Standard VAT") &&
                    mixedText.contains("EUR 4.40") && mixedText.contains("EUR 34.40"),"Mixed VAT invoice rendered incorrectly");

                fresh();
                sql("INSERT INTO invoices(id,number,date,customer_id,taxable_amount,vat,total,status) VALUES(1,'LEGACY',"+System.currentTimeMillis()+",1,10,2.2,12.2,'Issued')");
                sql("INSERT INTO invoice_details(invoice_id,product_id,quantity,unit_price,vat_rate,total) VALUES(1,1,1,10,22,10)");
                InvoiceSnapshots.capture(db,1,true);
                Invoice legacy=new Invoice(1,"LEGACY",new java.util.Date(),1,"",10,2.2,12.2,"Issued");
                Path legacyFile=OUT.resolve("invoice-legacy.pdf");
                call(new InvoicePDFGenerator(legacy,customer()),"generatePDF",new Class<?>[]{java.io.File.class},legacyFile.toFile());
                String legacyText=pdfText(legacyFile);
                String legacyNote=I18n.tr("Legacy invoice: party and product descriptions were captured during upgrade and may differ from the original document.");
                require(legacyText.contains(legacyNote),"Legacy warning missing");
                require(legacyText.indexOf("Product A")<legacyText.indexOf(legacyNote),"Legacy warning still dominates the invoice header");
            } finally {I18n.setLanguage(previousLanguage);}
        });
        test("fifty line invoice prints every row and totals within page bounds",()->{
            fresh();sql("INSERT INTO invoices(id,number,date,customer_id,taxable_amount,vat,total,status) VALUES(1,'TEST-50',"+System.currentTimeMillis()+",1,500,110,610,'Issued')");
            for(int i=0;i<50;i++)sql("INSERT INTO invoice_details(invoice_id,product_id,quantity,unit_price,vat_rate,total,product_code,product_name) VALUES(1,1,1,10,22,10,'5901234123457','ROW-"+String.format("%03d",i)+"')");
            InvoiceSnapshots.capture(db,1,false);Invoice i=new Invoice(1,"TEST-50",new java.util.Date(),1,"",500,110,610,"Issued");
            Path file=OUT.resolve("invoice-50-lines.pdf");call(new InvoicePDFGenerator(i,customer()),"generatePDF",new Class<?>[]{java.io.File.class},file.toFile());
            String text=pdfText(file);for(int row=0;row<50;row++)require(text.contains("ROW-"+String.format("%03d",row)),"Invoice row clipped: "+row);
            require(text.contains("TOTAL: EUR 610.00"),"Final invoice total missing");
            try(PDDocument doc=Loader.loadPDF(file.toFile())){require(doc.getNumberOfPages()>1,"Invoice did not paginate");}
            sql("UPDATE invoices SET total=611 WHERE id=1");byte[] original=Files.readAllBytes(file);boolean blocked=false;
            try{call(new InvoicePDFGenerator(i,customer()),"generatePDF",new Class<?>[]{java.io.File.class},file.toFile());}catch(java.io.IOException e){blocked=true;}
            require(blocked && Arrays.equals(original,Files.readAllBytes(file)),"Inconsistent invoice overwrote prior export");
        });
        test("empty and long report PDFs remain complete and readable",()->{
            DefaultTableModel m=new DefaultTableModel(new String[]{"Code","Description"},0);Path empty=OUT.resolve("report-empty.pdf");
            call(new ReportPDFGenerator(m,"Empty report",null,"empty.pdf"),"generatePDF",new Class<?>[]{java.io.File.class},empty.toFile());
            require(pdfText(empty).contains("No data"),"Empty report contains no explanation");try(PDDocument doc=Loader.loadPDF(empty.toFile())){require(doc.getNumberOfPages()==1,"Empty PDF has no page");}
            for(int i=0;i<120;i++)m.addRow(new Object[]{"ITEM-"+String.format("%03d",i),"Łódź — Crème"});
            m.addRow(new Object[]{"LONG",("description ").repeat(800)+"END-MARKER"});
            Path file=OUT.resolve("report-120-lines.pdf");call(new ReportPDFGenerator(m,"Complete report",null,"report.pdf"),"generatePDF",new Class<?>[]{java.io.File.class},file.toFile());
            String text=pdfText(file);require(text.contains("ITEM-119") && text.contains("END-MARKER"),"Long report truncated");
        });
    }
}
