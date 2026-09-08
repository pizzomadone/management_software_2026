import java.sql.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;

public class OperationalRegressionTest extends RegressionTest {
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
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");require(((JLabel)get(dash,"monthRevenueLabel")).getText().equals(SettingsPanel.formatCurrency(10)),"Wrong monthly invoice boundary/status");
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
