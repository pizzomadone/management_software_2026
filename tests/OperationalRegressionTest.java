import java.sql.*;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
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
        test("startup reconciles active reservation cache",()->{
            fresh();StockManager.createOrUpdateReservation(db,1,"ORDER",77,8,"");sql("UPDATE products SET reserved_quantity=0 WHERE id=1");call(DatabaseManager.getInstance(),"createTables");
            require("8".equals(value("SELECT reserved_quantity FROM products WHERE id=1")),"Active reservations were lost from availability after startup");
        });
        test("migration classifies legacy new orders by full availability",()->{
            fresh();
            sql("INSERT INTO orders(id,customer_id,order_date,status,total_ex_vat,total_inc_vat) VALUES" +
                "(77,1,1000,'New',80,80),(78,1,2000,'New',50,50)");
            sql("INSERT INTO order_details(order_id,product_id,quantity,sale_unit_price_ex_vat,sale_unit_price_inc_vat,vat_rate) VALUES" +
                "(77,1,8,10,10,0),(78,1,5,10,10,0)");
            StockManager.createOrUpdateReservation(db,1,"ORDER",77,8,"legacy new order");
            StockManager.createOrUpdateReservation(db,1,"ORDER",78,5,"legacy overbooked order");
            require("13".equals(value("SELECT reserved_quantity FROM products WHERE id=1")),
                "Legacy overbooked fixture was not created");
            sql("DELETE FROM schema_migrations WHERE version IN ('018','019')");
            DatabaseMigrationManager.migrate(db);
            StockManager.reconcileReservedStock(db);
            require("New".equals(value("SELECT status FROM orders WHERE id=77")),
                "Fully covered legacy New order was not preserved");
            require("Reservation".equals(value("SELECT status FROM orders WHERE id=78")),
                "Uncovered legacy New order was not converted to Reservation");
            require("8".equals(value("SELECT reserved_quantity FROM products WHERE id=1")),
                "Migration did not rebuild only the fully covered reservation");
            require("ACTIVE".equals(value(
                "SELECT status FROM stock_reservations WHERE document_type='ORDER' AND document_id=77")),
                "Covered legacy New order has no active reservation");
            require("0".equals(value(
                "SELECT COUNT(*) FROM stock_reservations WHERE document_type='ORDER' AND document_id=78 AND status='ACTIVE'")),
                "Reservation order still blocks warehouse stock");
        });
        test("customer order product selector shows unreserved availability",()->{
            fresh();
            StockManager.createOrUpdateReservation(db,1,"ORDER",77,8,"other order");

            ProductSelectionDialog orderSelector=empty(ProductSelectionDialog.class);
            put(orderSelector,"activeOnly",true);
            put(orderSelector,"useAvailableStock",true);
            DefaultTableModel orderModel=new DefaultTableModel(
                new String[]{"ID","Code","Name","Description","Sale Price incl. VAT","Available","Status"},0);
            put(orderSelector,"tableModel",orderModel);
            call(orderSelector,"loadAllProducts");

            int orderRow=-1;
            for(int row=0;row<orderModel.getRowCount();row++)
                if(((Number)orderModel.getValueAt(row,0)).intValue()==1){orderRow=row;break;}
            require(orderRow>=0,"Reserved product missing from customer order selector");
            require(((Number)orderModel.getValueAt(orderRow,5)).intValue()==2,
                "Customer order selector shows physical stock instead of physical minus reserved");

            ProductSelectionDialog normalSelector=empty(ProductSelectionDialog.class);
            put(normalSelector,"activeOnly",true);
            put(normalSelector,"useAvailableStock",false);
            DefaultTableModel normalModel=new DefaultTableModel(
                new String[]{"ID","Code","Name","Description","Sale Price incl. VAT","Stock","Status"},0);
            put(normalSelector,"tableModel",normalModel);
            call(normalSelector,"loadAllProducts");

            int normalRow=-1;
            for(int row=0;row<normalModel.getRowCount();row++)
                if(((Number)normalModel.getValueAt(row,0)).intValue()==1){normalRow=row;break;}
            require(normalRow>=0 && ((Number)normalModel.getValueAt(normalRow,5)).intValue()==10,
                "Non-order product selector no longer shows physical stock");
        });
        test("manual outward availability honors reservations and edit reversal",()->{
            fresh();StockManager.createOrUpdateReservation(db,1,"ORDER",77,8,"");WarehouseMovementDialog d=empty(WarehouseMovementDialog.class);put(d,"movement",null);
            require(((Number)call(d,"availableForOutward",new Class<?>[]{Connection.class,int.class},db,1)).intValue()==2,"Manual issue ignored reservations");
            sql("UPDATE products SET quantity=15 WHERE id=1");put(d,"movement",new WarehouseMovement(1,1,"A",new java.util.Date(),"INWARD",5,"OTHER",null,null,""));
            require(((Number)call(d,"availableForOutward",new Class<?>[]{Connection.class,int.class},db,1)).intValue()==2,"Edited inward movement availability is wrong");
            sql("UPDATE products SET quantity=5 WHERE id=1");put(d,"movement",new WarehouseMovement(1,1,"A",new java.util.Date(),"OUTWARD",5,"OTHER",null,null,""));
            require(((Number)call(d,"availableForOutward",new Class<?>[]{Connection.class,int.class},db,1)).intValue()==2,"Edited outward movement availability is wrong");
        });
        test("linked invoice locks non-completed order immediately",()->{
            fresh();
            call(order(false,3,"New"),"saveOrder");
            sql("INSERT INTO invoices(number,date,customer_id,taxable_amount,vat,total,status,order_id,currency_code) VALUES('LOCKED-NEW',0,1,30,0,30,'Issued',1,'EUR')");

            OD d=order(true,3,"New");
            put(d,"selectCustomerButton",new JButton());
            put(d,"newCustomerButton",new JButton());
            put(d,"addItemButton",new JButton());
            put(d,"removeItemButton",new JButton());
            put(d,"paidAmountLabel",new JLabel());

            call(d,"initializeLinkedInvoiceLock");
            call(d,"configureEditability");

            require((Boolean)get(d,"linkedInvoiceLocked"),"Linked invoice was not detected when opening the order");
            require(!((JButton)get(d,"selectCustomerButton")).isEnabled(),"Linked order customer remains editable");
            require(!((JComboBox<?>)get(d,"statusCombo")).isEnabled(),"Linked order status remains editable");
            require(!((JButton)get(d,"addItemButton")).isEnabled(),"Linked order products remain editable");
            require(((JComboBox<?>)get(d,"paymentStatusCombo")).isEnabled(),"Linked order payment is not editable");
        });
        test("linked invoice freezes its source order",()->{
            fresh();completed();sql("INSERT INTO invoices(number,date,customer_id,taxable_amount,vat,total,status,order_id,currency_code) VALUES('LOCKED',0,1,30,0,30,'Issued',1,'EUR')");
            OD d=order(true,5,"Completed");call(d,"saveOrder");
            require(qty()==7 && "30.0|3".equals(value("SELECT total_ex_vat||'|'||(SELECT quantity FROM order_details WHERE order_id=1) FROM orders WHERE id=1")),"Invoiced order diverged from invoice");
            CustomersPanel customers=new CustomersPanel();require((Boolean)call(customers,"hasFinalizedCustomerInvoices",new Class<?>[]{Connection.class,int.class},db,1),"Finalized customer history was not detected");
        });
        test("only completed customer orders are invoice eligible",()->{
            require(OrdersPanel.canGenerateInvoice("Completed"),"Completed order cannot be invoiced");
            for(String status:java.util.List.of("New","In Progress","Cancelled"))
                require(!OrdersPanel.canGenerateInvoice(status),"Non-completed order is invoice eligible: "+status);
            require(!OrdersPanel.canGenerateInvoice(null),"Missing status is invoice eligible");
        });
        test("canceled linked invoice allows only completed sale reversal",()->{
            require(!OrdersPanel.linkedInvoiceBlocksOrderAction("Completed","Canceled"),
                "Canceled linked invoice still blocks reversal of a completed sale");
            require(OrdersPanel.linkedInvoiceBlocksOrderAction("Completed","Draft"),
                "Draft linked invoice must be deleted before reversing the sale");
            require(OrdersPanel.linkedInvoiceBlocksOrderAction("Completed","Issued"),
                "Issued linked invoice must be canceled before reversing the sale");
            require(OrdersPanel.linkedInvoiceBlocksOrderAction("New","Canceled"),
                "Canceled invoice must not make a non-completed order deletable");
        });
        test("canceled linked invoice can be restored only before sale reversal",()->{
            require(InvoiceDialog.canRestoreCanceledLinkedInvoice("Canceled","Issued","Completed"),
                "Accidentally canceled invoice cannot be restored while the sale is still completed");
            require(!InvoiceDialog.canRestoreCanceledLinkedInvoice("Canceled","Issued","Reversed"),
                "Canceled invoice can be reissued after the linked sale was reversed");
            require(InvoiceDialog.canRestoreCanceledLinkedInvoice("Issued","Canceled","Completed"),
                "Normal invoice cancellation was blocked");
        });
        test("proforma is pending-order only and does not persist an invoice",()->{
            for(String status:java.util.List.of("Reservation","New","In Progress"))
                require(OrdersPanel.canGenerateProforma(status),"Pending order cannot generate proforma: "+status);
            for(String status:java.util.List.of("Completed","Cancelled","Reversed"))
                require(!OrdersPanel.canGenerateProforma(status),"Historical order can generate proforma: "+status);
            require(!OrdersPanel.canGenerateProforma(null),"Missing status can generate proforma");

            fresh();
            call(order(false,3,"New"),"saveOrder");
            String before=value("SELECT COUNT(*) FROM invoices");
            String stockBefore=value("SELECT quantity||'|'||reserved_quantity FROM products WHERE id=1");
            Path pdf=OUT.resolve("customer-order-proforma.pdf");
            Files.deleteIfExists(pdf);

            ProformaPDFGenerator.generatePDF(1,pdf.toFile());

            require(Files.isRegularFile(pdf) && Files.size(pdf)>0,"Proforma PDF was not created");
            require(before.equals(value("SELECT COUNT(*) FROM invoices")),
                "Generating a proforma persisted an invoice");
            require("New".equals(value("SELECT status FROM orders WHERE id=1")),
                "Generating a proforma changed the order status");
            require(stockBefore.equals(value("SELECT quantity||'|'||reserved_quantity FROM products WHERE id=1")),
                "Generating a proforma changed stock or reservations");
        });
        test("inventory correction records only the physical delta",()->{
            fresh();call(productEditor(15),"saveProduct");require(qty()==15,"Stock count not saved");
            require("5".equals(value("SELECT quantity FROM warehouse_movements")) && "INWARD".equals(value("SELECT type FROM warehouse_movements")),"Wrong inventory delta");
            call(productEditor(15),"saveProduct");require("1".equals(value("SELECT COUNT(*) FROM warehouse_movements")),"Unchanged save duplicated adjustment");
            call(productEditor(8),"saveProduct");require("7".equals(value("SELECT quantity FROM warehouse_movements ORDER BY id DESC LIMIT 1")),"Updated count used stale product quantity");
        });
        test("product stock reductions respect reserved units and preserve FIFO",()->{
            fresh();
            seedOpeningLots();
            StockManager.createOrUpdateReservation(db,1,"ORDER",77,8,"");
            require(qty()==10 && "8".equals(value("SELECT reserved_quantity FROM products WHERE id=1")),
                "Invalid starting stock or reservation");

            PD rejected=productEditor(2);
            rejectedSave(rejected,"saveProduct","productSaved");
            require(qty()==10,"Rejected stock edit changed physical quantity");
            require("8".equals(value("SELECT reserved_quantity FROM products WHERE id=1")),
                "Rejected stock edit changed existing reservations");
            require("0".equals(value("SELECT COUNT(*) FROM warehouse_movements")),
                "Rejected stock edit created a warehouse movement");
            require("10".equals(value("SELECT remaining_quantity FROM inventory_lots WHERE product_id=1")),
                "Rejected stock edit consumed FIFO stock");
            require("0".equals(value("SELECT COUNT(*) FROM minimum_stock")),
                "Rejected stock edit saved unrelated product settings");

            PD accepted=productEditor(8);
            call(accepted,"saveProduct");
            require(Boolean.TRUE.equals(get(accepted,"productSaved")),
                "Valid stock adjustment was not saved");
            require(qty()==8 && StockManager.getAvailableStock(db,1)==0,
                "Valid adjustment failed to respect reservations");
            require("8".equals(value("SELECT remaining_quantity FROM inventory_lots WHERE product_id=1")),
                "Valid adjustment did not consume two FIFO units");
            require("2".equals(value("SELECT quantity FROM warehouse_movements WHERE product_id=1")),
                "Valid adjustment recorded an incorrect movement");
        });
        test("inventory layer rejects direct reductions of reserved stock",()->{
            fresh();
            seedOpeningLots();
            StockManager.createOrUpdateReservation(db,1,"ORDER",77,8,"");
            sql("INSERT INTO warehouse_movements(id,product_id,date,type,quantity,reason) " +
                "VALUES(91,1,CURRENT_TIMESTAMP,'OUTWARD',9,'TEST')");
            boolean blocked=false;
            try {
                InventoryLotManager.adjustProductStock(db,91,1,-9,new java.util.Date(),5);
            } catch(SQLException expected) {
                blocked=true;
            }
            require(blocked && qty()==10,"Direct stock reduction ignored reservations");
            require("10".equals(value("SELECT remaining_quantity FROM inventory_lots WHERE product_id=1")),
                "Rejected direct reduction altered FIFO layers");
            require("0".equals(value("SELECT COUNT(*) FROM inventory_lot_consumptions WHERE warehouse_movement_id=91")),
                "Rejected direct reduction inserted FIFO consumption history");
        });
        test("reserved stock blocks reversal of a manual inward movement",()->{
            fresh();
            sql("UPDATE products SET quantity=0 WHERE id=1");
            sql("INSERT INTO warehouse_movements(id,product_id,date,type,quantity,reason) " +
                "VALUES(92,1,CURRENT_TIMESTAMP,'INWARD',10,'TEST')");
            InventoryLotManager.applyManualMovement(db,92,1,"INWARD",10,new java.util.Date(),5);
            StockManager.createOrUpdateReservation(db,1,"ORDER",77,8,"");

            boolean blocked=false;
            try {
                InventoryLotManager.reverseManualMovement(db,92,1,"INWARD",10);
            } catch(SQLException expected) {
                blocked=true;
            }
            require(blocked && qty()==10 && StockManager.getAvailableStock(db,1)==2,
                "Reserved goods were removed by reversing an inward movement");
            require("10".equals(value("SELECT remaining_quantity FROM inventory_lots WHERE warehouse_movement_id=92")),
                "Rejected reversal deleted its FIFO lot");

            StockManager.cancelReservation(db,"ORDER",77);
            InventoryLotManager.reverseManualMovement(db,92,1,"INWARD",10);
            require(qty()==0 && "0".equals(value("SELECT COUNT(*) FROM inventory_lots WHERE warehouse_movement_id=92")),
                "Valid inward reversal did not restore stock");
        });
        test("edited product selection can be restored after table refresh",()->{
            fresh();
            ProductsPanel panel=new ProductsPanel();
            JTable table=(JTable)get(panel,"productsTable");
            call(panel,"selectProductById",new Class<?>[]{int.class},1);
            int viewRow=table.getSelectedRow();
            require(viewRow>=0,"Edited product was not reselected");
            int modelRow=table.convertRowIndexToModel(viewRow);
            DefaultTableModel model=(DefaultTableModel)table.getModel();
            require(((Number)model.getValueAt(modelRow,0)).intValue()==1,
                "Wrong product remained selected after refresh");
        });
        test("new customer order remains highlighted after returning to Orders panel",()->{
            fresh();
            sql("INSERT INTO orders(id,customer_id,order_date,status,total_ex_vat,total_inc_vat) " +
                "VALUES(42,1,'2020-01-01','Reservation',0,0)");
            OD created=order(false,2,"New");
            call(created,"saveOrder");
            Integer savedId=created.getSavedOrderId();
            require(created.isOrderSaved() && savedId!=null && savedId>42,
                "Created customer order did not expose the saved database ID");
            require(savedId.toString().equals(value("SELECT id FROM orders WHERE status='New'")),
                "The saved ID does not identify the newly created order");

            OrdersPanel panel=new OrdersPanel();
            JTable table=(JTable)get(panel,"ordersTable");
            table.getRowSorter().toggleSortOrder(0);
            panel.selectOrderById(savedId);
            int selected=table.getSelectedRow();
            require(selected>=0 && table.isRowSelected(selected),
                "New customer order is not visually selected after refresh");
            int modelRow=table.convertRowIndexToModel(selected);
            DefaultTableModel model=(DefaultTableModel)table.getModel();
            require(savedId.equals(model.getValueAt(modelRow,0)),
                "Selection points to a different customer order after sorting");
        });
        test("edited supplier order selection can be restored after refresh",()->{
            fresh();
            sql("INSERT INTO supplier_orders(id,supplier_id,number,order_date,status,total,notes) VALUES(41,1,'PO-41','2026-10-03','Draft',10,'')");
            sql("INSERT INTO supplier_orders(id,supplier_id,number,order_date,status,total,notes) VALUES(42,1,'PO-42','2026-10-04','Draft',20,'')");
            SupplierOrdersPanel panel=new SupplierOrdersPanel();
            JTable table=(JTable)get(panel,"table");
            ((javax.swing.table.TableRowSorter<?>)table.getRowSorter()).toggleSortOrder(1);
            call(panel,"loadOrders");
            call(panel,"selectOrderById",new Class<?>[]{int.class},41);
            int viewRow=table.getSelectedRow();
            require(viewRow>=0,"Edited supplier order was not reselected");
            int modelRow=table.convertRowIndexToModel(viewRow);
            DefaultTableModel model=(DefaultTableModel)table.getModel();
            require(((Number)model.getValueAt(modelRow,0)).intValue()==41,
                "Wrong supplier order remained selected after refresh");
        });
        test("product pricing UI shows currency and product save preserves supplier reference price",()->{
            PD ui=empty(PD.class);
            JPanel priceWithCurrency=(JPanel)call(ui,"withCurrencyUnit",
                new Class<?>[]{JComponent.class},new JTextField("10"));
            require(labels(priceWithCurrency).stream().anyMatch(l->SettingsPanel.getCurrencyCode().equals(l.getText())),
                "Current currency code missing beside product price field");
            require(labels(priceWithCurrency).stream().noneMatch(l->"?".equals(l.getText())),
                "Pricing helper still shows a question-mark help icon");

            fresh();
            sql("UPDATE product_suppliers SET purchase_price=7,is_preferred=1 WHERE product_id=1 AND supplier_id=1");
            PD editor=productEditor(10);
            ((JTextField)get(editor,"acquisitionCostField")).setText("99");
            put(editor,"selectedSupplier",new Supplier(1,"Supplier A","","","","","","","",""));
            call(editor,"saveProduct");
            require("7.0".equals(value("SELECT purchase_price FROM product_suppliers WHERE product_id=1 AND supplier_id=1")),
                "Saving the product overwrote the supplier cost");
            require("5.0".equals(value("SELECT acquisition_cost FROM products WHERE id=1")),
                "Editing the product changed the informational last purchase cost");
        });
        test("product details show purchase costs read only",()->{
            fresh();
            sql("UPDATE product_suppliers SET purchase_price=7,is_preferred=1 WHERE product_id=1 AND supplier_id=1");
            ProductDetailsDialog details=empty(ProductDetailsDialog.class);
            Object cost=call(details,"loadPreferredSupplierCost",new Class<?>[]{int.class},1);
            require(cost instanceof Double && ((Double)cost)==7.0,
                "Product details did not load the preferred supplier cost");

            JPanel row=new JPanel(new GridBagLayout());
            GridBagConstraints g=new GridBagConstraints();g.gridy=0;
            Product p=new Product(1,"A","Product A","",10,10);
            call(details,"addPreferredSupplierCostLine",
                new Class<?>[]{JPanel.class,GridBagConstraints.class,Product.class,Double.class},
                row,g,p,7.0);
            require(Arrays.stream(row.getComponents()).noneMatch(c->c instanceof JButton),
                "Product Details still uses a button for purchase-cost help");
            require(Arrays.stream(row.getComponents()).noneMatch(c ->
                    c instanceof JLabel label && "?".equals(label.getText())),
                "Product Details general cost guide is still attached to a field");
            JLabel costHelp=(JLabel)call(details,"purchaseCostHelpButton");
            require("?".equals(costHelp.getText()),
                "Product Details top-right cost guide is missing");
            String costHelpText=(String)call(details,"purchaseCostHelpTooltip");
            require(!costHelpText.contains("read-only") && !costHelpText.contains("sola lettura"),
                "Product Details cost guide still says the view is read-only");
            require(Arrays.stream(row.getComponents()).anyMatch(c->c instanceof JTextArea),
                "Preferred supplier cost is not displayed as text");
        });
        test("notification actions target selected database IDs",()->{
            fresh();sql("INSERT INTO warehouse_notifications(id,product_id,type,message) VALUES(1,1,'LOW_STOCK','Same text'),(2,1,'LOW_STOCK','Same text')");
            WarehousePanel p=new WarehousePanel();JTable table=(JTable)get(p,"notificationsTable");DefaultTableModel model=(DefaultTableModel)get(p,"notificationsModel");
            int wanted=((Number)model.getValueAt(0,5)).intValue();table.setRowSelectionInterval(0,0);call(p,"markSelectedNotifications",new Class<?>[]{String.class},"READ");
            require("READ".equals(value("SELECT status FROM warehouse_notifications WHERE id="+wanted)),"Wrong notification updated");
            require("1".equals(value("SELECT COUNT(*) FROM warehouse_notifications WHERE status='NEW'")),"Duplicate display text changed another notification");
        });
        test("inactive physical stock remains in warehouse valuation",()->{
            fresh();
            sql("UPDATE products SET sale_price_ex_vat=10,sale_price_inc_vat=10,vat_rate=0,acquisition_cost=99,quantity=1 WHERE id=1");
            sql("UPDATE products SET acquisition_cost=99,active=0,quantity=2 WHERE id=2");
            sql("UPDATE product_suppliers SET purchase_price=5 WHERE product_id=1 AND is_preferred=1");
            WarehouseReportPanel report=new WarehouseReportPanel();
            DefaultTableModel model=(DefaultTableModel)get(report,"productsModel");
            require(model.getRowCount()==2,"Inactive product with stock disappeared from warehouse report");
            require("Inactive".equals(model.getValueAt(1,7)),"Inactive stock is not identified in warehouse report");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(21)),
                "Inactive physical stock is missing from warehouse cost value");
            require(kpi(dash,"warehouseSaleValueLabel").equals(SettingsPanel.formatCurrency(50)),
                "Inactive physical stock is missing from warehouse sale value");
            sql("UPDATE product_suppliers SET purchase_price=0 WHERE product_id=1 AND is_preferred=1");
            call(dash,"loadKPIData");
            require(kpi(dash,"zeroCostCountLabel").equals("1") && kpi(dash,"avgMarginLabel").endsWith(" *"),
                "Missing preferred cost on active stock is not marked when inactive stock is also present");
        });
        test("migration backfills historical customer completion dates",()->{
            fresh();
            sql("INSERT INTO orders(id,customer_id,order_date,completed_date,status,total_ex_vat,total_inc_vat) VALUES(77,1,1000,NULL,'Completed',10,10)");
            sql("DELETE FROM schema_migrations WHERE version='020'");
            DatabaseMigrationManager.migrate(db);
            require("1".equals(value("SELECT completed_date=order_date FROM orders WHERE id=77")),
                "Historical completed order did not inherit its order date as migration fallback");
        });
        test("completed sales use completion date for stock and monthly revenue",()->{
            fresh();seedOpeningLots();
            OD d=order(false,3,"Completed");
            put(d,"dateField",new DatePickerField("01/01/2000"));
            call(d,"saveOrder");
            String today=new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
            require("2000-01-01".equals(value("SELECT warestat_date(order_date) FROM orders WHERE id=1")),
                "Order creation date changed when the sale completed");
            require(today.equals(value("SELECT warestat_date(completed_date) FROM orders WHERE id=1")),
                "Completed sale did not store its actual completion date");
            require(today.equals(value("SELECT warestat_date(date) FROM warehouse_movements WHERE document_type='ORDER' AND document_number='1'")),
                "Sale warehouse movement still uses the original order date");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
            require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(30)),
                "Monthly revenue still follows order date instead of completion date");
            SalesReportPanel report=new SalesReportPanel();call(report,"loadReportData");
            DefaultTableModel rows=(DefaultTableModel)get(report,"tableModel");
            require(rows.getRowCount()==1 && rows.getValueAt(0,0).equals(DateUtils.formatDate(new java.util.Date(),new java.text.SimpleDateFormat("dd/MM/yyyy"))),
                "Sales report does not display the completion date");
        });
        test("pending-order age handles numeric stored dates",()->{
            fresh();
            Calendar old=Calendar.getInstance();
            old.set(Calendar.HOUR_OF_DAY,12);old.set(Calendar.MINUTE,0);old.set(Calendar.SECOND,0);old.set(Calendar.MILLISECOND,0);
            old.add(Calendar.DAY_OF_MONTH,-10);
            sql("INSERT INTO orders(id,customer_id,order_date,status,total_ex_vat,total_inc_vat) VALUES(77,1,"+
                old.getTimeInMillis()+",'New',10,12.2)");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadPendingOrders");
            List<JLabel> pendingLabels=labels((JPanel)get(dash,"pendingOrdersPanel"));
            String expectedDate=DateUtils.formatDate(old.getTime(),new java.text.SimpleDateFormat("dd/MM/yyyy"));
            require(pendingLabels.stream().anyMatch(l->l.getText().contains(expectedDate)),
                "Pending order shows raw or invalid numeric date");
            require(pendingLabels.stream().anyMatch(l->l.getText().contains("10 "+I18n.tr("days old"))),
                "Pending order age is wrong for numeric stored dates");
        });
        test("dashboard tooltip formulas are localized in every supported language",()->{
            String previous=I18n.getLanguage();
            try {
                for(String language:List.of("en","it","es","fr","de","pl","pt_BR")) {
                    I18n.setLanguage(language);
                    for(String key:List.of(
                            "tooltip.kpi.warehouseValue","tooltip.kpi.warehouseSaleValue",
                            "tooltip.kpi.lowStock","tooltip.kpi.zeroStock",
                            "tooltip.kpi.monthRevenue","tooltip.kpi.monthInvoiced",
                            "tooltip.kpi.pendingOrders","tooltip.kpi.avgMargin",
                            "tooltip.kpi.missingCost","tooltip.kpi.negativeStock",
                            "tooltip.dashboard.topProducts","tooltip.dashboard.abcAnalysis")) {
                        String explanation=I18n.tr(key);
                        require(explanation.startsWith("<html>") && explanation.endsWith("</html>"),
                            "Missing translated dashboard tooltip: "+language+" "+key);
                    }
                }
                I18n.setLanguage("it");
                require(I18n.tr("tooltip.kpi.monthRevenue").contains("data di completamento"),
                    "Sales KPI tooltip does not explain its completion-date basis");
                require(I18n.tr("tooltip.kpi.monthInvoiced").contains("valute diverse"),
                    "Invoiced KPI tooltip does not explain separate currencies");
                require(I18n.tr("tooltip.kpi.avgMargin").contains("ponderato"),
                    "Stock margin tooltip does not explain weighted calculation");
            } finally {
                I18n.setLanguage(previous);
            }
        });
        test("monthly invoiced total includes the exact first millisecond",()->{
            fresh();Calendar month=Calendar.getInstance();month.set(Calendar.DAY_OF_MONTH,1);month.set(Calendar.HOUR_OF_DAY,0);month.set(Calendar.MINUTE,0);month.set(Calendar.SECOND,0);month.set(Calendar.MILLISECOND,0);
            sql("INSERT INTO invoices(number,date,customer_id,taxable_amount,vat,total,status,currency_code) VALUES('START',"+month.getTimeInMillis()+",1,10,0,10,'Issued','EUR'),('CANCELLED',"+month.getTimeInMillis()+",1,900,0,900,'Cancelled','EUR')");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");require(kpi(dash,"monthInvoicedLabel").equals(SettingsPanel.formatCurrency(10)),"Wrong monthly invoice boundary/status");
        });
        test("stock at zero cost is distinct from sale value and an empty warehouse",()->{
            fresh();sql("UPDATE products SET quantity=10,acquisition_cost=99,sale_price_ex_vat=12.50,sale_price_inc_vat=12.50,vat_rate=0 WHERE id=1");
            sql("UPDATE product_suppliers SET purchase_price=0 WHERE product_id=1 AND is_preferred=1");
            sql("UPDATE products SET active=0,quantity=100,acquisition_cost=99 WHERE id=2");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(800)+" *"),"Inactive stock cost was omitted or a missing cost was invented");
            require(kpi(dash,"warehouseSaleValueLabel").equals(SettingsPanel.formatCurrency(2125)),"Inactive stock sale value was omitted");
            require(kpi(dash,"zeroCostCountLabel").equals("1"),"Zero cost is not explained");
            sql("UPDATE product_suppliers SET purchase_price=5 WHERE product_id=1 AND is_preferred=1");call(dash,"loadKPIData");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(850)) && kpi(dash,"zeroCostCountLabel").equals("0"),"Preferred supplier cost edit not reflected");
            sql("DELETE FROM products");call(dash,"loadKPIData");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(0)) && kpi(dash,"warehouseSaleValueLabel").equals(SettingsPanel.formatCurrency(0)),"Empty warehouse retained old values");
        });
        test("negative stock cannot cancel positive assets and stays visible after restocking",()->{
            fresh();sql("UPDATE products SET quantity=3,acquisition_cost=99,sale_price_ex_vat=2.335,sale_price_inc_vat=2.335,vat_rate=0 WHERE id=1");
            sql("UPDATE products SET quantity=-100,acquisition_cost=99 WHERE id=2");
            sql("UPDATE product_suppliers SET purchase_price=0.335 WHERE product_id=1 AND is_preferred=1");
            sql("UPDATE product_suppliers SET purchase_price=80 WHERE product_id=2 AND is_preferred=1");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");call(dash,"loadAlerts");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(1.01)),"Deficit cancelled another product's cost value");
            require(kpi(dash,"warehouseSaleValueLabel").equals(SettingsPanel.formatCurrency(7.01)),"Deficit cancelled another product's sale value");
            require(kpi(dash,"negativeStockCountLabel").equals("1") && kpi(dash,"lowStockCountLabel").equals("0"),"Negative stock without a minimum was hidden");
            require(labels((JPanel)get(dash,"alertsPanel")).stream().anyMatch(l->l.getText().contains("-100")),"No deficit alert");
            WarehouseReportPanel report=new WarehouseReportPanel();DefaultTableModel rows=(DefaultTableModel)get(report,"productsModel");
            require(rows.getValueAt(0,6).equals("1.01") && rows.getValueAt(1,6).equals("0.00"),"Report valuation differs from dashboard");
            require(rows.getValueAt(1,2).equals(-100) && value("SELECT quantity FROM products WHERE id=2").equals("-100"),"Valuation overwrote the actual deficit");
            sql("UPDATE products SET quantity=2 WHERE id=2");call(dash,"loadKPIData");call(report,"loadProductsData");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(161.01)) && kpi(dash,"negativeStockCountLabel").equals("0"),"Restock did not restore asset value");
            require(rows.getValueAt(1,6).equals("160.00"),"Report did not refresh after restock");
            sql("UPDATE products SET quantity=-2147483648 WHERE id=2");
            sql("INSERT INTO minimum_stock(product_id,minimum_quantity,reorder_quantity) VALUES(2,2147483647,0)");call(dash,"loadKPIData");call(dash,"loadAlerts");
            require(labels((JPanel)get(dash,"alertsPanel")).stream().anyMatch(l->l.getText().contains("4294967295")),"Deficit alert overflowed");
            sql("UPDATE products SET quantity=0 WHERE id=1");call(dash,"loadKPIData");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(0)) && kpi(dash,"warehouseSaleValueLabel").equals(SettingsPanel.formatCurrency(0)),"All depleted stock produced a negative value");
        });
        test("monthly revenue excludes reversals and retains the original sale history",()->{
            fresh();seedOpeningLots();call(order(false,3,"Completed"),"saveOrder");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
            require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(30)),"Uninvoiced sale disappeared");
            require(kpi(dash,"monthInvoicedLabel").equals(SettingsPanel.formatCurrency(0)),"Phantom invoice");
            call(order(true,5,"Completed"),"saveOrder");
            SalesReportPanel report=new SalesReportPanel();call(report,"loadReportData");
            DefaultTableModel rows=(DefaultTableModel)get(report,"tableModel");
            require(rows.getRowCount()==1 && rows.getValueAt(0,4).equals("30.00"),"Payment-only edit changed revenue");
            StockManager.reverseCompletedOrder(db,1);call(dash,"loadKPIData");call(report,"loadReportData");
            require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(0)) && qty()==10,"Reversed sale remained in net revenue");
            require(rows.getRowCount()==0 && "1".equals(value("SELECT COUNT(*) FROM orders")),"Historical order lost");
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
                    try(PreparedStatement order=db.prepareStatement("INSERT INTO orders(customer_id,order_date,status,total_ex_vat,total_inc_vat) VALUES(1,?,'Completed',10,10)");
                        PreparedStatement invoice=db.prepareStatement("INSERT INTO invoices(number,date,customer_id,taxable_amount,vat,total,status,currency_code) VALUES(?,?,1,10,0,10,'Issued','EUR')")) {
                        for(int i=0;i<dates.length;i++) {order.setObject(1,dates[i]);order.executeUpdate();invoice.setString(1,"BOUNDARY-"+i);invoice.setObject(2,dates[i]);invoice.executeUpdate();}
                    }
                    for(String status:List.of("New","In Progress","Cancelled"))
                        sql("INSERT INTO orders(customer_id,order_date,status,total_ex_vat,total_inc_vat) VALUES(1,"+start+",'"+status+"',900,900)");
                    for(String status:List.of("Draft","Canceled"))
                        sql("INSERT INTO invoices(number,date,customer_id,taxable_amount,vat,total,status,currency_code) VALUES('"+status+"',"+start+",1,900,0,900,'"+status+"','EUR')");
                    sql("INSERT INTO invoices(number,date,customer_id,taxable_amount,vat,total,status,payment_status,paid_amount,currency_code) VALUES('PAID',"+start+",1,20,0,20,'Issued','PAID',20,'EUR')");
                    DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
                    require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(40)),"Wrong monthly orders in "+zone);
                    require(kpi(dash,"monthInvoicedLabel").equals(SettingsPanel.formatCurrency(60)),"Wrong monthly invoices in "+zone);
                }
            } finally {TimeZone.setDefault(previous);}
        });
        test("invoicing a sale keeps net orders and net invoiced revenue separate",()->{
            fresh();completed();ID invoice=invoice();call(invoice,"saveInvoice");sql("UPDATE invoices SET order_id=1");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
            require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(30)),"Invoice double-counted a sale or included VAT in orders");
            require(kpi(dash,"monthInvoicedLabel").equals(SettingsPanel.formatCurrency(30)),"VAT was included in net invoiced revenue");
            OrdersPanel.updateCompletedOrderPayment(db,1,"PAID",0);call(dash,"loadKPIData");
            require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(30)) && kpi(dash,"monthInvoicedLabel").equals(SettingsPanel.formatCurrency(30)),"Payment counted a second sale");
            sql("UPDATE invoices SET status='Canceled'");call(dash,"loadKPIData");
            require(kpi(dash,"monthRevenueLabel").equals(SettingsPanel.formatCurrency(30)) && kpi(dash,"monthInvoicedLabel").equals(SettingsPanel.formatCurrency(0)),"Invoice cancellation incorrectly cancelled the independent order");
        });
        test("potential stock margin is value weighted and handles loss free cost and no denominator",()->{
            fresh();sql("UPDATE products SET quantity=1,sale_price_ex_vat=100,sale_price_inc_vat=100,vat_rate=0,acquisition_cost=999 WHERE id=1");
            sql("UPDATE products SET quantity=9,sale_price_ex_vat=10,sale_price_inc_vat=10,vat_rate=0,acquisition_cost=999 WHERE id=2");
            sql("UPDATE product_suppliers SET purchase_price=90 WHERE product_id=1 AND is_preferred=1");
            sql("UPDATE product_suppliers SET purchase_price=1 WHERE product_id=2 AND is_preferred=1");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(99)) && kpi(dash,"warehouseSaleValueLabel").equals(SettingsPanel.formatCurrency(190)),"Wrong basis for margin");
            require(kpi(dash,"avgMarginLabel").equals("47.9%"),"Used an unweighted product margin average");
            sql("UPDATE products SET quantity=-9 WHERE id=2");call(dash,"loadKPIData");
            require(kpi(dash,"avgMarginLabel").equals("10.0%"),"Negative stock changed potential margin");
            sql("UPDATE products SET quantity=9,active=0 WHERE id=2");call(dash,"loadKPIData");
            require(kpi(dash,"avgMarginLabel").equals("47.9%"),"Inactive physical stock disappeared from potential margin");
            sql("UPDATE products SET quantity=0 WHERE id=2");
            sql("UPDATE product_suppliers SET purchase_price=0 WHERE product_id=1 AND is_preferred=1");call(dash,"loadKPIData");
            require(kpi(dash,"avgMarginLabel").equals("100.0% *"),"Missing preferred supplier cost must mark the margin incomplete");
            sql("UPDATE product_suppliers SET purchase_price=150 WHERE product_id=1 AND is_preferred=1");call(dash,"loadKPIData");
            require(kpi(dash,"avgMarginLabel").equals("-50.0%"),"Loss was hidden or confused with markup");
            sql("UPDATE products SET sale_price_ex_vat=0,sale_price_inc_vat=0,vat_rate=0 WHERE id=1");call(dash,"loadKPIData");
            require(kpi(dash,"avgMarginLabel").equals("—"),"Zero denominator produced a fictitious margin");
            sql("UPDATE products SET quantity=0");call(dash,"loadKPIData");
            require(kpi(dash,"avgMarginLabel").equals("—"),"An empty warehouse showed a profit percentage");
        });
        test("stock and pending order counts match the documented physical stock rules",()->{
            fresh();call(order(false,9,"New"),"saveOrder");
            sql("UPDATE products SET quantity=-2 WHERE id=2");
            sql("INSERT INTO products(code,name,quantity,sale_price_ex_vat,sale_price_inc_vat,vat_rate,active) VALUES('ZERO','Empty',0,10,10,0,1),('LOW','Low',5,10,10,0,1),('HIDDEN','Hidden',0,10,10,0,0)");
            sql("INSERT INTO minimum_stock(product_id,minimum_quantity,reorder_quantity) VALUES(1,3,0),(4,10,0),(5,10,0)");
            sql("INSERT INTO orders(customer_id,order_date,status,total_ex_vat,total_inc_vat) VALUES(1,'2000-01-01','Reservation',0,0),(1,'2000-01-01','In Progress',0,0),(1,'2000-01-01','Completed',0,0),(1,'2000-01-01','Cancelled',0,0)");
            DashboardPanel dash=new DashboardPanel(null);call(dash,"loadKPIData");
            require(kpi(dash,"lowStockCountLabel").equals("2"),"New order did not reserve stock or low-stock rules changed");
            require(kpi(dash,"zeroStockLabel").equals("2"),"Out of stock omitted a negative balance or included inactive products");
            require(kpi(dash,"pendingOrdersLabel").equals("3"),"Reservation/New/In Progress pending orders were not counted correctly");
            require(kpi(dash,"warehouseValueLabel").equals(SettingsPanel.formatCurrency(50)+" *") && kpi(dash,"warehouseSaleValueLabel").equals(SettingsPanel.formatCurrency(150)),"Reservations reduced physical stock value");
            WarehouseReportPanel report=new WarehouseReportPanel();DefaultTableModel rows=(DefaultTableModel)get(report,"productsModel");
            int outOfStock=0;for(int row=0;row<rows.getRowCount();row++)if(rows.getValueAt(row,7).equals("OUT OF STOCK"))outOfStock++;
            require(outOfStock==2,"Warehouse report and dashboard disagree on out of stock");
        });
        test("KPI titles notes and explanations translate in every supported language",()->{
            fresh();DashboardPanel dash=new DashboardPanel(null);JPanel panel=(JPanel)call(dash,"createKPIPanel");
            String[] titles={"Stock value at preferred supplier cost","Stock value at net sale price","Monthly completed sales (excl. VAT)","Monthly invoiced (excl. VAT)","Potential stock margin",
                "In-stock products with missing preferred supplier cost","Negative-stock products (excluded from valuation)"};
            Map<String,String> fields=Map.of("warehouseValue","warehouseValueLabel","warehouseSaleValue","warehouseSaleValueLabel",
                "lowStock","lowStockCountLabel","zeroStock","zeroStockLabel","monthRevenue","monthRevenueLabel",
                "monthInvoiced","monthInvoicedLabel","pendingOrders","pendingOrdersLabel","avgMargin","avgMarginLabel");
            I18n.setLanguage("en");Map<String,String> englishHelp=new HashMap<>();
            for(String key:fields.keySet())englishHelp.put(key,I18n.tr("tooltip.kpi."+key));
            try {
                for(String language:List.of("it","es","pt_BR","fr","de","pl","en")) {
                    I18n.setLanguage(language);I18n.localize(panel);call(dash,"loadKPIData");
                    List<JLabel> labels=labels(panel);
                    for(String title:titles) {
                        String translated=I18n.tr(title);
                        require(language.equals("en") || !translated.equals(title),"Missing "+language+" translation: "+title);
                        require(labels.stream().anyMatch(l->translated.equals(l.getText())),"Visible title not localized after switching to "+language);
                    }
                    panel.setSize(1200,panel.getPreferredSize().height);layoutTree(panel);
                    for(JLabel label:labels) require(label.getWidth()>=label.getPreferredSize().width,"Clipped KPI text in "+language+": "+label.getText());
                    require(labels.stream().filter(l->"?".equals(l.getText())).count()==8,"Not every KPI has a visible question mark");
                    for(var entry:fields.entrySet()) {
                        JLabel value=(JLabel)get(dash,entry.getValue());JPanel card=(JPanel)value.getParent();
                        JPanel footer=(JPanel)((java.awt.BorderLayout)card.getLayout()).getLayoutComponent(java.awt.BorderLayout.SOUTH);
                        require(footer!=null && footer.getY()>=value.getY()+value.getHeight(),"KPI help is not below its value");
                        List<JLabel> helpLabels=labels(footer);require(helpLabels.size()==1 && helpLabels.get(0).getText().equals("?"),"Missing help in the KPI footer");
                        JLabel help=helpLabels.get(0);String tooltip=help.getToolTipText();
                        require(tooltip.equals(I18n.tr("tooltip.kpi."+entry.getKey())) && tooltip.equals(value.getToolTipText()),"KPI help and value explanations disagree");
                        require(tooltip.startsWith("<html>") && (language.equals("en") || !tooltip.equals(englishHelp.get(entry.getKey()))),"Untranslated KPI formula in "+language);
                        require(Arrays.stream(help.getMouseListeners()).anyMatch(listener->listener instanceof ToolTipManager),"Question mark is not registered for hover tooltips");
                        JToolTip tip=help.createToolTip();tip.setTipText(tooltip);tip.setSize(tip.getPreferredSize());
                        require(tip.getWidth()<=380 && tip.getHeight()>40 && tip.getHeight()<380,
                            "KPI tooltip does not wrap to a readable size: "+language+" "+entry.getKey()+" "+tip.getSize());
                        require(tip.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey)!=null,"Tooltip HTML was not rendered");
                        if(language.equals("it") && entry.getKey().equals("monthInvoiced") || language.equals("de") && entry.getKey().equals("avgMargin")) {
                            var image=new java.awt.image.BufferedImage(tip.getWidth(),tip.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
                            var graphics=image.createGraphics();try{tip.printAll(graphics);}finally{graphics.dispose();}
                            javax.imageio.ImageIO.write(image,"png",OUT.resolve("kpi-help-"+language+".png").toFile());
                        }
                    }
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
                sql("UPDATE products SET quantity=100 WHERE id=1");sql("ALTER TABLE invoices RENAME COLUMN taxable_amount TO unavailable_taxable_amount");
                try{call(dash,"loadKPIData");}catch(java.awt.HeadlessException expectedErrorDialog){}
                for(String field:List.of("warehouseValueLabel","warehouseSaleValueLabel","monthRevenueLabel","monthInvoicedLabel","negativeStockCountLabel"))
                    require(kpi(dash,field).equals("—"),"Stale or partial figure after read failure: "+field);
                sql("ALTER TABLE product_suppliers RENAME COLUMN purchase_price TO unavailable_purchase_price");
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
            require("10.0".equals(value("SELECT sale_price_inc_vat FROM products WHERE id=1")),"Currency label changed numeric price");
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

