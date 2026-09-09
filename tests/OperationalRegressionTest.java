import java.sql.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;

public class OperationalRegressionTest extends RegressionTest {
    static String kpi(DashboardPanel panel, String field) throws Exception {
        return ((JLabel)get(panel, field)).getText();
    }
    static List<JLabel> labels(java.awt.Component component) {
        List<JLabel> result = new ArrayList<>();
        if (component instanceof JLabel label) result.add(label);
        if (component instanceof java.awt.Container container)
            for (java.awt.Component child : container.getComponents()) result.addAll(labels(child));
        return result;
    }
    static void layoutTree(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents())
            if (child instanceof java.awt.Container nested) layoutTree(nested);
    }
    static PD productEditor(int quantity) throws Exception {
        PD d=empty(PD.class);put(d,"product",new Product(1,"A","Product A","",10,10));
        String[][] fields={{"codeField","A"},{"nameField","Product A"},{"priceField","10"},{"alternativeSkuField",""},{"weightField","0"},{"acquisitionCostField","5"},{"warehousePositionField",""},{"vatRateField","22"}};
        for(String[] f:fields)put(d,f[0],new JTextField(f[1]));
        put(d,"descriptionArea",new JTextArea(""));put(d,"quantitySpinner",new JSpinner(new SpinnerNumberModel(quantity,0,999999,1)));
        put(d,"minimumQuantitySpinner",new JSpinner(new SpinnerNumberModel(0,0,999999,1)));put(d,"categoryComboBox",new JComboBox<>(new String[]{""}));
        put(d,"unitOfMeasureComboBox",new JComboBox<>(new String[]{"pcs"}));put(d,"activeCheckBox",new JCheckBox("",true));return d;
    }
    static void operational() throws Exception {
        test("inventory correction records only the physical delta",()->{
            fresh();call(productEditor(15),"saveProduct");require(qty()==15,"Stock count not saved");
            require("5".equals(value("SELECT quantity FROM warehouse_movements")) && "INWARD".equals(value("SELECT type FROM warehouse_movements")),"Wrong inventory delta");
            call(productEditor(15),"saveProduct");require("1".equals(value("SELECT COUNT(*) FROM warehouse_movements")),"Unchanged save duplicated adjustment");
            call(productEditor(8),"saveProduct");require("7".equals(value("SELECT quantity FROM warehouse_movements ORDER BY id DESC LIMIT 1")),"Updated count used stale product quantity");
        });
        test("notification actions target selected database IDs",()->{
            fresh();sql("INSERT INTO warehouse_notifications(id,product_id,type,message) VALUES(1,1,'LOW_STOCK','Same text'),(2,1,'LOW_STOCK','Same text')");
            WarehousePanel p=new WarehousePanel();JTable table=(JTable)get(p,"notificationsTable");DefaultTableModel model=(DefaultTableModel)get(p,"notificationsModel");
            int wanted=((Number)model.getValueAt(0,5)).intValue();table.setRowSelectionInterval(0,0);call(p,"markSelectedNotifications",new Class<?>[]{String.class},"READ");
            require("READ".equals(value("SELECT status FROM warehouse_notifications WHERE id="+wanted)),"Wrong notification updated");
            require("1".equals(value("SELECT COUNT(*) FROM warehouse_notifications WHERE status='NEW'")),"Duplicate display text changed another notification");
        });
        test("warehouse valuation agrees with dashboard and zero cost margin",()->{
            fresh();sql("UPDATE products SET price=10,acquisition_cost=0.004,quantity=1 WHERE id=1");sql("UPDATE products SET acquisition_cost=99,active=0 WHERE id=2");
            WarehouseReportPanel report=new WarehouseReportPanel();DefaultTableModel model=(DefaultTableModel)get(report,"productsModel");require(model.getRowCount()==1,"Inactive catalog included");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
            require(((JLabel)get(dash,"warehouseValueLabel")).getText().equals(SettingsPanel.formatCurrency(Numbers.nonNegative(model.getValueAt(0,4)))),"Valuation rounding differs");
            sql("UPDATE products SET acquisition_cost=0 WHERE id=1");call(dash,"loadKPIData");require(((JLabel)get(dash,"avgMarginLabel")).getText().equals("100.0%"),"Zero cost margin is not 100%");
        });
        test("monthly invoiced total includes the exact first millisecond",()->{
            fresh();Calendar month=Calendar.getInstance();month.set(Calendar.DAY_OF_MONTH,1);month.set(Calendar.HOUR_OF_DAY,0);month.set(Calendar.MINUTE,0);month.set(Calendar.SECOND,0);month.set(Calendar.MILLISECOND,0);
            sql("INSERT INTO invoices(number,date,customer_id,taxable_amount,vat,total,status) VALUES('START',"+month.getTimeInMillis()+",1,10,0,10,'Issued'),('CANCELLED',"+month.getTimeInMillis()+",1,900,0,900,'Cancelled')");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");require(kpi(dash,"monthInvoicedLabel").equals(SettingsPanel.formatCurrency(10)),"Wrong monthly invoice boundary/status");
        });
        test("stock at zero cost is distinct from sale value and an empty warehouse",()->{
            fresh();sql("UPDATE products SET quantity=10,acquisition_cost=0,price=12.50 WHERE id=1");
            sql("UPDATE products SET active=0,quantity=100,acquisition_cost=99 WHERE id=2");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(0)),"Invented an acquisition cost");
            require(kpi(dash,"warehouseSaleValueLabel").equals(SettingsPanel.formatCurrency(125)),"Sale value missing or inactive stock included");
            require(kpi(dash,"zeroCostCountLabel").equals("1"),"Zero cost is not explained");
            sql("UPDATE products SET acquisition_cost=5 WHERE id=1");call(dash,"loadKPIData");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(50)) && kpi(dash,"zeroCostCountLabel").equals("0"),"Cost edit not reflected");
            sql("DELETE FROM products");call(dash,"loadKPIData");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(0)) && kpi(dash,"warehouseSaleValueLabel").equals(SettingsPanel.formatCurrency(0)),"Empty warehouse retained old values");
        });
        test("negative stock cannot cancel positive assets and stays visible after restocking",()->{
            fresh();sql("UPDATE products SET quantity=3,acquisition_cost=0.335,price=2.335 WHERE id=1");
            sql("UPDATE products SET quantity=-100,acquisition_cost=80,minimum_quantity=0 WHERE id=2");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");call(dash,"loadAlerts");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(1.01)),"Deficit cancelled another product's cost value");
            require(kpi(dash,"warehouseSaleValueLabel").equals(SettingsPanel.formatCurrency(7.01)),"Deficit cancelled another product's sale value");
            require(kpi(dash,"negativeStockCountLabel").equals("1") && kpi(dash,"lowStockCountLabel").equals("1"),"Negative stock without a minimum was hidden");
            require(labels((JPanel)get(dash,"alertsPanel")).stream().anyMatch(l->l.getText().contains("-100")),"No deficit alert");
            WarehouseReportPanel report=new WarehouseReportPanel();DefaultTableModel rows=(DefaultTableModel)get(report,"productsModel");
            require(rows.getValueAt(0,4).equals("1.01") && rows.getValueAt(1,4).equals("0.00"),"Report valuation differs from dashboard");
            require(rows.getValueAt(1,2).equals(-100) && value("SELECT quantity FROM products WHERE id=2").equals("-100"),"Valuation overwrote the actual deficit");
            sql("UPDATE products SET quantity=2 WHERE id=2");call(dash,"loadKPIData");call(report,"loadProductsData");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(161.01)) && kpi(dash,"negativeStockCountLabel").equals("0"),"Restock did not restore asset value");
            require(rows.getValueAt(1,4).equals("160.00"),"Report did not refresh after restock");
            sql("UPDATE products SET quantity=-2147483648,minimum_quantity=2147483647 WHERE id=2");call(dash,"loadKPIData");call(dash,"loadAlerts");
            require(labels((JPanel)get(dash,"alertsPanel")).stream().anyMatch(l->l.getText().contains("4294967295")),"Deficit alert overflowed");
            sql("UPDATE products SET quantity=0 WHERE id=1");call(dash,"loadKPIData");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(0)) && kpi(dash,"warehouseSaleValueLabel").equals(SettingsPanel.formatCurrency(0)),"All depleted stock produced a negative value");
        });
        test("monthly sales follow completed order edits resaves and cancellations without an invoice",()->{
            fresh();call(order(false,3,"Completed"),"saveOrder");DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
            require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(30)),"Uninvoiced sale disappeared");
            require(kpi(dash,"monthInvoicedLabel").equals(SettingsPanel.formatCurrency(0)),"An order became a phantom invoice");
            for(int i=0;i<2;i++) {call(order(true,5,"Completed"),"saveOrder");call(dash,"loadKPIData");
                require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(50)),"Edit/resave duplicated or retained stale sales");}
            SalesReportPanel report=new SalesReportPanel();call(report,"loadReportData");DefaultTableModel rows=(DefaultTableModel)get(report,"tableModel");
            require(rows.getRowCount()==1 && rows.getValueAt(0,4).equals("50.00"),"Sales report disagrees with dashboard");
            call(order(true,5,"Cancelled"),"saveOrder");call(dash,"loadKPIData");
            require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(0)) && qty()==10,"Cancelled sale remained in the KPI");
        });
        test("monthly orders and invoices respect statuses date formats and local month boundaries",()->{
            TimeZone previous=TimeZone.getDefault();
            try {
                for(String zone:List.of("UTC","Europe/Rome","America/New_York")) {
                    TimeZone.setDefault(TimeZone.getTimeZone(zone));fresh();
                    Calendar month=Calendar.getInstance();month.set(Calendar.DAY_OF_MONTH,1);month.set(Calendar.HOUR_OF_DAY,0);month.set(Calendar.MINUTE,0);month.set(Calendar.SECOND,0);month.set(Calendar.MILLISECOND,0);
                    long start=month.getTimeInMillis();month.add(Calendar.MONTH,1);long end=month.getTimeInMillis();
                    String day=new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(start));
                    Object[] dates={start-1,start,end-1,end,day+" 12:00:00",day};
                    try(PreparedStatement order=db.prepareStatement("INSERT INTO orders(customer_id,order_date,status,total) VALUES(1,?,'Completed',10)");
                        PreparedStatement invoice=db.prepareStatement("INSERT INTO invoices(number,date,customer_id,taxable_amount,vat,total,status) VALUES(?,?,1,10,0,10,'Issued')")) {
                        for(int i=0;i<dates.length;i++) {order.setObject(1,dates[i]);order.executeUpdate();invoice.setString(1,"BOUNDARY-"+i);invoice.setObject(2,dates[i]);invoice.executeUpdate();}
                    }
                    for(String status:List.of("New","In Progress","Cancelled"))
                        sql("INSERT INTO orders(customer_id,order_date,status,total) VALUES(1,"+start+",'"+status+"',900)");
                    for(String status:List.of("Draft","Canceled"))
                        sql("INSERT INTO invoices(number,date,customer_id,taxable_amount,vat,total,status) VALUES('"+status+"',"+start+",1,900,0,900,'"+status+"')");
                    sql("INSERT INTO invoices(number,date,customer_id,taxable_amount,vat,total,status) VALUES('PAID',"+start+",1,20,0,20,'Paid')");
                    DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
                    require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(40)),"Wrong monthly orders in "+zone);
                    require(kpi(dash,"monthInvoicedLabel").equals(SettingsPanel.formatCurrency(60)),"Wrong monthly invoices in "+zone);
                }
            } finally {TimeZone.setDefault(previous);}
        });
        test("invoicing a sale keeps net orders and gross invoices separate",()->{
            fresh();completed();ID invoice=invoice();call(invoice,"saveInvoice");sql("UPDATE invoices SET order_id=1");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
            require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(30)),"Invoice double-counted a sale or included VAT in orders");
            require(kpi(dash,"monthInvoicedLabel").equals(SettingsPanel.formatCurrency(36.60)),"Invoice VAT missing");
            sql("UPDATE invoices SET status='Paid'");call(dash,"loadKPIData");
            require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(30)) && kpi(dash,"monthInvoicedLabel").equals(SettingsPanel.formatCurrency(36.60)),"Payment counted a second sale");
            sql("UPDATE invoices SET status='Canceled'");call(dash,"loadKPIData");
            require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(30)) && kpi(dash,"monthInvoicedLabel").equals(SettingsPanel.formatCurrency(0)),"Invoice cancellation incorrectly cancelled the independent order");
        });
        test("KPI titles notes and explanations translate in every supported language",()->{
            fresh();DashboardPanel dash=new DashboardPanel(null);JPanel panel=(JPanel)call(dash,"createKPIPanel");
            String[] titles={"Stock at purchase cost","Stock at sale price","Monthly sales (excl. VAT)","Monthly invoices (incl. VAT)",
                "In-stock products with zero purchase cost","Negative-stock products (excluded from valuation)"};
            try {
                for(String language:List.of("it","es","pt_BR","fr","de","pl","en")) {
                    I18n.setLanguage(language);I18n.localize(panel);call(dash,"loadKPIData");
                    List<JLabel> labels=labels(panel);
                    for(String title:titles) {
                        String translated=I18n.tr(title);
                        require(language.equals("en") || !translated.equals(title),"Missing "+language+" translation: "+title);
                        require(labels.stream().anyMatch(l->translated.equals(l.getText())),"Visible title not localized after switching to "+language);
                    }
                    for(String field:List.of("warehouseValueLabel","warehouseSaleValueLabel","monthRevenueLabel","monthInvoicedLabel")) {
                        String tooltip=((JLabel)get(dash,field)).getToolTipText();
                        require(tooltip!=null && (language.equals("en") || !tooltip.equals("Only active products with positive stock are valued at current catalog prices.") && !tooltip.startsWith("Completed customer orders") && !tooltip.startsWith("Issued or paid invoices")),"Untranslated KPI explanation");
                    }
                    panel.setSize(1200,panel.getPreferredSize().height);layoutTree(panel);
                    for(JLabel label:labels) require(label.getWidth()>=label.getPreferredSize().width,"Clipped KPI text in "+language+": "+label.getText());
                    if(language.equals("it") || language.equals("de")) {
                        var image=new java.awt.image.BufferedImage(panel.getWidth(),panel.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
                        var graphics=image.createGraphics();try{panel.printAll(graphics);}finally{graphics.dispose();}
                        javax.imageio.ImageIO.write(image,"png",OUT.resolve("dashboard-kpis-"+language+".png").toFile());
                    }
                }
            } finally {I18n.setLanguage("en");}
        });
        test("failed dashboard and warehouse refreshes clear stale or partial figures",()->{
            fresh();DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
            WarehouseReportPanel report=new WarehouseReportPanel();
            sql("SAVEPOINT broken_kpi_read");
            try {
                sql("UPDATE products SET quantity=100 WHERE id=1");sql("ALTER TABLE invoices RENAME COLUMN total TO unavailable_total");
                try{call(dash,"loadKPIData");}catch(java.awt.HeadlessException expectedErrorDialog){}
                for(String field:List.of("warehouseValueLabel","warehouseSaleValueLabel","monthRevenueLabel","monthInvoicedLabel","negativeStockCountLabel"))
                    require(kpi(dash,field).equals("—"),"Stale or partial figure after read failure: "+field);
                sql("ALTER TABLE products RENAME COLUMN acquisition_cost TO unavailable_cost");
                try{call(report,"loadProductsData");}catch(java.awt.HeadlessException expectedErrorDialog){}
                require(!Boolean.TRUE.equals(get(report,"productsValid")) && ((DefaultTableModel)get(report,"productsModel")).getRowCount()==0,"Invalid report remained exportable");
                JPanel statistics=(JPanel)((JPanel)((JTabbedPane)get(report,"tabbedPane")).getComponentAt(0)).getComponent(0);
                require(labels(statistics).stream().allMatch(l->l.getText().equals("—")),"Report retained stale valuation statistics");
            } finally {sql("ROLLBACK TO broken_kpi_read");sql("RELEASE broken_kpi_read");}
            call(dash,"loadKPIData");require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(130)),"KPI did not recover after data became available");
        });
        test("automatic backup schedule honors interval retry and running state",()->{
            BackupManager b=BackupManager.getInstance();Properties config=(Properties)get(b,"config");Properties previous=(Properties)config.clone();
            try {
                b.setAutoBackupEnabled(true);b.setBackupIntervalHours(1);long now=System.currentTimeMillis();config.setProperty("backup.last_success",Long.toString(now-7200000));
                require(b.isAutomaticBackupDue(now),"Overdue backup not scheduled");put(b,"automaticRunning",true);require(!b.isAutomaticBackupDue(now),"Overlapping backup scheduled");put(b,"automaticRunning",false);
                put(b,"retryAfter",now+1000);require(!b.isAutomaticBackupDue(now),"Retry throttle ignored");put(b,"retryAfter",0L);
                config.setProperty("backup.last_success",Long.toString(now));require(!b.isAutomaticBackupDue(now),"Backup ran before interval");
            } finally {config.clear();config.putAll(previous);put(b,"automaticRunning",false);put(b,"retryAfter",0L);}
        });
        test("IMMEDIATE transactions serialize stock availability checks",()->{
            fresh();Path file=OUT.resolve("serialized-stock.db");try(PreparedStatement p=db.prepareStatement("VACUUM INTO ?")){p.setString(1,file.toString());p.execute();}
            try(Connection a=DatabaseManager.openConnection("jdbc:sqlite:"+file);Connection b=DatabaseManager.openConnection("jdbc:sqlite:"+file)) {
                try(Statement s=b.createStatement()){s.execute("PRAGMA busy_timeout=50");}
                DatabaseManager.beginTransaction(a);boolean blocked=false;try{DatabaseManager.beginTransaction(b);}catch(SQLException e){blocked=true;}
                require(blocked,"Second writer entered before first stock check committed");
                try(Statement s=a.createStatement()){s.execute("UPDATE products SET quantity=3 WHERE id=1");}a.commit();a.setAutoCommit(true);
                require(b.isClosed(),"Failed BEGIN left a poisoned JDBC connection available");
                try(Connection next=DatabaseManager.openConnection("jdbc:sqlite:"+file)) {
                    DatabaseManager.beginTransaction(next);
                    require(!StockManager.checkStockAvailability(next,List.of(new StockManager.StockItem(1,"A",7)),null,"ORDER").isEmpty(),"Second writer used stale availability");next.rollback();next.setAutoCommit(true);
                }
            }
        });
        test("legacy import requires and preserves the declared original currency",()->{
            fresh();Path file=OUT.resolve("legacy-currency.db");try(PreparedStatement p=db.prepareStatement("VACUUM INTO ?")){p.setString(1,file.toString());p.execute();}
            try(Connection c=DatabaseManager.openConnection("jdbc:sqlite:"+file);Statement s=c.createStatement()){s.execute("DROP TABLE application_metadata");}
            boolean blocked=false;try{BackupManager.getInstance().importDatabase(file);}catch(RuntimeException e){blocked=true;}require(blocked,"Legacy currency guessed");
            BackupManager.getInstance().importDatabase(file,"USD");db=DatabaseManager.getInstance().getConnection();require("USD".equals(DatabaseCurrency.read(db)) && "USD".equals(SettingsPanel.getCurrencyCode()),"Original currency lost at import");
            require("10.0".equals(value("SELECT price FROM products WHERE id=1")),"Currency label changed numeric price");
        });
        test("SQL and UI use the same calendar day across time zones",()->{
            fresh();TimeZone previous=TimeZone.getDefault();
            try {
                long instant=java.time.Instant.parse("2026-08-31T23:30:00Z").toEpochMilli();
                for(String zone:List.of("UTC","Europe/Rome","America/New_York")) {
                    TimeZone.setDefault(TimeZone.getTimeZone(zone));
                    String expected=new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(instant));
                    require(expected.equals(value("SELECT warestat_date("+instant+")")),"Integer date differs in "+zone);
                    require(expected.equals(value("SELECT warestat_date('2026-08-31 23:30:00')")),"UTC text date differs in "+zone);
                }
            } finally {TimeZone.setDefault(previous);}
        });
        test("startup refuses an empty existing database without replacing it",()->{
            DatabaseManager manager=DatabaseManager.getInstance();manager.closeConnection();Path current=AppConstants.getDatabasePath();Path original=OUT.resolve("before-empty-startup.db");Files.move(current,original);
            try {
                Files.createFile(current);boolean blocked=false;try{manager.initDatabase();}catch(RuntimeException e){blocked=true;}
                require(blocked && Files.size(current)==0,"Empty existing database was silently initialized");
            } finally {manager.closeConnection();Files.deleteIfExists(current);Files.move(original,current);manager.initDatabase();db=manager.getConnection();}
        });
    }
}
