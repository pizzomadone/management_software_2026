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
        test("minimum stock stays synchronized in both editors",()->{
            fresh();sql("INSERT INTO minimum_stock(product_id,minimum_quantity,reorder_quantity) VALUES(1,5,10)");
            require("5".equals(value("SELECT minimum_quantity FROM products WHERE id=1")),"Warehouse threshold did not reach product");
            sql("UPDATE products SET minimum_quantity=7 WHERE id=1");require("7".equals(value("SELECT minimum_quantity FROM minimum_stock WHERE product_id=1")),"Product threshold did not reach warehouse");
            db.setAutoCommit(false);
            try {
                sql("UPDATE minimum_stock SET minimum_quantity=21 WHERE product_id=1");
                require("21".equals(value("SELECT minimum_quantity FROM products WHERE id=1")),"Minimum edit did not synchronize within the transaction");
                db.rollback();
            } finally {db.setAutoCommit(true);}
            require("7".equals(value("SELECT minimum_quantity FROM products WHERE id=1")) && "7".equals(value("SELECT minimum_quantity FROM minimum_stock WHERE product_id=1")),"Rollback left inconsistent thresholds");
        });
        test("minimum stock setup preserves product thresholds without advanced settings",()->{
            fresh();sql("UPDATE products SET minimum_quantity=7 WHERE id=1");
            require("0".equals(value("SELECT COUNT(*) FROM minimum_stock")),"Fixture already has advanced settings");
            WarehousePanel warehouse=new WarehousePanel();
            MinimumStock settings=(MinimumStock)call(warehouse,"loadMinimumStock",new Class<?>[]{String.class},"A");
            require(settings.getMinimumQuantity()==7,"Opening warehouse settings reset the product threshold to zero");
            MS first=minimumEditor(settings,settings.getMinimumQuantity());call(first,"saveStock");
            require(Boolean.TRUE.equals(get(first,"stockSaved")) && "7".equals(value("SELECT minimum_quantity FROM products WHERE id=1")),"Saving unchanged settings lost the threshold");
            MS edit=minimumEditor(settings,11);call(edit,"saveStock");
            require("11".equals(value("SELECT minimum_quantity FROM products WHERE id=1")) && "11".equals(value("SELECT minimum_quantity FROM minimum_stock WHERE product_id=1")),"Warehouse editor did not save both representations");
            PD product=OperationalRegressionTest.productEditor(10);((JSpinner)get(product,"minimumQuantitySpinner")).setValue(14);call(product,"saveProduct");
            require("14".equals(value("SELECT minimum_quantity FROM products WHERE id=1")) && "14".equals(value("SELECT minimum_quantity FROM minimum_stock WHERE product_id=1")),"Product editor did not synchronize the threshold");
            call(warehouse,"loadStockData");DefaultTableModel stock=(DefaultTableModel)get(warehouse,"stockModel");
            require(stock.getValueAt(0,5).equals(14),"Warehouse table differs from the product threshold");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
            require(((JLabel)get(dash,"lowStockCountLabel")).getText().equals("1"),"Dashboard uses a different threshold");
            sql("DELETE FROM minimum_stock WHERE product_id=1");
            settings=(MinimumStock)call(warehouse,"loadMinimumStock",new Class<?>[]{String.class},"A");
            require(settings.getMinimumQuantity()==14,"Removing optional reorder settings erased the product threshold");
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
