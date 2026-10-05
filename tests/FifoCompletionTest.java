import java.sql.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Synthetic end-to-end cases for immutable FIFO history and incomplete costs. */
public class FifoCompletionTest extends RegressionTest {
    static void sale(int id,int quantity) throws Exception {
        sql("INSERT INTO orders(id,customer_id,order_date,status,total_ex_vat,total_inc_vat) VALUES("+id+",1,"+System.currentTimeMillis()+",'Completed',"+(quantity*15)+","+(quantity*15)+")");
        sql("INSERT INTO order_details(id,order_id,product_id,quantity,sale_unit_price_ex_vat,sale_unit_price_inc_vat,vat_rate) VALUES("+id+","+id+",1,"+quantity+",15,15,0)");
        StockManager.createOrUpdateReservation(db,1,"ORDER",id,quantity,"");
        StockManager.completeReservationAndDecrementStock(db,"ORDER",id,new java.util.Date(),String.valueOf(id));
    }
    static void twoLayers() throws Exception {
        fresh();
        sql("INSERT INTO inventory_lots(id,product_id,received_date,initial_quantity,remaining_quantity,unit_cost,source_type) VALUES(1,1,1,5,5,5,'MANUAL_INWARD'),(2,1,2,5,5,10,'MANUAL_INWARD')");
    }
    static void mustReject(Check check) throws Exception {
        boolean blocked=false;
        try { check.run(); } catch (SQLException expected) { blocked=true; }
        require(blocked,"Unsafe operation accepted");
    }
    static String balances() throws Exception {
        return value("SELECT GROUP_CONCAT(remaining_quantity) FROM (SELECT remaining_quantity FROM inventory_lots WHERE product_id=1 ORDER BY id)");
    }
    static void completion() throws Exception {
        test("storno preserves later sale cost and exact original FIFO layers",()->{
            twoLayers();sale(20,7);sale(21,2);
            require("45.0".equals(value("SELECT cost_total FROM order_details WHERE id=20")),"Wrong first sale cost");
            require("20.0".equals(value("SELECT cost_total FROM order_details WHERE id=21")),"Wrong later sale cost");
            String later=value("SELECT lot_id||'|'||quantity||'|'||unit_cost FROM inventory_lot_consumptions WHERE order_detail_id=21");
            StockManager.reverseCompletedOrder(db,20);
            require(qty()==8 && "5,3".equals(balances()),"Original layers not restored exactly");
            require("45.0".equals(value("SELECT cost_total FROM order_details WHERE id=20")),"Original historical cost erased");
            require("20.0".equals(value("SELECT cost_total FROM order_details WHERE id=21")),"Later sale repriced");
            require(later.equals(value("SELECT lot_id||'|'||quantity||'|'||unit_cost FROM inventory_lot_consumptions WHERE order_detail_id=21")),"Later snapshot changed");
            mustReject(()->StockManager.reverseCompletedOrder(db,20));
            require(qty()==8 && "5,3".equals(balances()),"Second storno changed stock");
            int reversal=Integer.parseInt(value("SELECT id FROM warehouse_movements WHERE document_type='ORDER_REVERSAL'"));
            mustReject(()->StockManager.requireManualMovement(db,reversal));
        });
        test("sale reversal rolls back all lots if restoring physical quantity overflows",()->{
            twoLayers();sale(20,7);sql("UPDATE products SET quantity=2147483647 WHERE id=1");
            mustReject(()->StockManager.reverseCompletedOrder(db,20));
            require("0,3".equals(balances()),"Failed reversal partially restored lots");
            require("Completed".equals(value("SELECT status FROM orders WHERE id=20")),"Failed reversal changed sale status");
            require(db.getAutoCommit(),"Transaction state leaked");
        });
        test("manual reversal preserves history even after later FIFO sales",()->{
            twoLayers();
            sql("INSERT INTO warehouse_movements(id,product_id,date,type,quantity,reason) VALUES(50,1,1,'OUTWARD',7,'OTHER')");
            InventoryLotManager.applyManualMovement(db,50,1,"OUTWARD",7,new java.util.Date());
            sale(21,2);
            InventoryLotManager.reverseOutwardMovement(db,50);
            require(qty()==8 && "5,3".equals(balances()),"Manual storno restored wrong layers");
            require("2".equals(value("SELECT COUNT(*) FROM inventory_lot_consumptions WHERE warehouse_movement_id=50")),"Original manual history lost");
            require("20.0".equals(value("SELECT cost_total FROM order_details WHERE id=21")),"Later sale cost changed");
            mustReject(()->InventoryLotManager.reverseOutwardMovement(db,50));
            mustReject(()->InventoryLotManager.reverseManualMovement(db,50,1,"OUTWARD",7));
            require("1".equals(value("SELECT COUNT(*) FROM warehouse_movements WHERE reversal_of_movement_id=50")),"Duplicate compensating entry");
            int reversal=Integer.parseInt(value("SELECT id FROM warehouse_movements WHERE reversal_of_movement_id=50"));
            mustReject(()->StockManager.requireManualMovement(db,reversal));
        });
        test("incomplete and overflowed manual reversals leave all balances intact",()->{
            twoLayers();
            sql("INSERT INTO warehouse_movements(id,product_id,date,type,quantity,reason) VALUES(50,1,1,'OUTWARD',7,'OTHER')");
            InventoryLotManager.applyManualMovement(db,50,1,"OUTWARD",7,new java.util.Date());
            sql("UPDATE products SET quantity=2147483647 WHERE id=1");
            mustReject(()->InventoryLotManager.reverseOutwardMovement(db,50));
            require("0,3".equals(balances()),"Failed manual reversal changed lots");
            sql("UPDATE inventory_lot_consumptions SET quantity=1 WHERE warehouse_movement_id=50 AND lot_id=1");
            mustReject(()->InventoryLotManager.reverseOutwardMovement(db,50));
            require("0".equals(value("SELECT COUNT(*) FROM warehouse_movements WHERE reversal_of_movement_id=50")),"Failed reversal persisted");
        });
        test("manual inbound layer with reversed historical sales remains protected",()->{
            fresh();sql("UPDATE products SET quantity=0 WHERE id=1");
            sql("INSERT INTO warehouse_movements(id,product_id,date,type,quantity,reason) VALUES(50,1,1,'INWARD',5,'OTHER')");
            InventoryLotManager.applyManualMovement(db,50,1,"INWARD",5,new java.util.Date(1),6);
            sale(20,3);StockManager.reverseCompletedOrder(db,20);
            mustReject(()->InventoryLotManager.reverseManualMovement(db,50,1,"INWARD",5));
            require(qty()==5 && "5".equals(balances()),"Protected inward layer changed");
        });
        test("unknown opening snapshots stay unknown after today's purchase cost correction",()->{
            fresh();sql("UPDATE products SET acquisition_cost=0 WHERE id=1");
            seedOpeningLots();
            // Evidence from an older version that posted zero-cost opening units.
            sql("INSERT INTO orders(id,customer_id,order_date,status,total_ex_vat,total_inc_vat) VALUES(20,1,"+System.currentTimeMillis()+",'Completed',30,30)");
            sql("INSERT INTO order_details(id,order_id,product_id,quantity,sale_unit_price_ex_vat,sale_unit_price_inc_vat,vat_rate,cost_total) VALUES(20,20,1,2,15,15,0,0)");
            sql("INSERT INTO inventory_lot_consumptions(lot_id,order_detail_id,quantity,unit_cost) SELECT id,20,2,0 FROM inventory_lots WHERE product_id=1");
            sql("UPDATE inventory_lots SET remaining_quantity=8 WHERE product_id=1");
            sql("UPDATE products SET quantity=8,acquisition_cost=9 WHERE id=1");
            CostIntegrity.updateUnpricedOpeningLots(db,1,0,9);
            sql("DELETE FROM schema_migrations WHERE version='014'");DatabaseMigrationManager.migrate(db);
            require(value("SELECT cost_total FROM order_details WHERE id=20")==null,"Migration retained invented zero historical cost");
            require(value("SELECT "+CostIntegrity.historicalCostSql("d")+" FROM order_details d WHERE id=20")==null,"Report repriced an old sale");
            require(CostIntegrity.audit(db).salesWithoutHistoricalCost()==1,"Incomplete sale not counted");
            sale(21,1);
            require("9.0".equals(value("SELECT cost_total FROM order_details WHERE id=21")),"Future sale did not use corrected opening cost");
        });
        test("demo lots cannot invent historical cost in reports or hide revenue",()->{
            fresh();
            sql("INSERT INTO inventory_lots(product_id,received_date,initial_quantity,remaining_quantity,unit_cost,source_type) VALUES(2,1,1,1,8,'DEMO_OPENING')");
            sql("INSERT INTO orders(id,customer_id,order_date,status,total_ex_vat,total_inc_vat) VALUES(20,1,"+System.currentTimeMillis()+",'Completed',30,30)");
            sql("INSERT INTO order_details(id,order_id,product_id,quantity,sale_unit_price_ex_vat,sale_unit_price_inc_vat,vat_rate) VALUES(20,20,1,2,15,15,0)");
            SalesReportPanel report=new SalesReportPanel();call(report,"loadReportData");
            DefaultTableModel rows=(DefaultTableModel)get(report,"tableModel");
            require(rows.getRowCount()==1 && "30.00".equals(rows.getValueAt(0,4)),"Known revenue lost");
            require(I18n.tr("Historical cost unavailable").equals(rows.getValueAt(0,5)),"Unknown cost displayed as a number");
            require(((JLabel)get(report,"totalCostLabel")).getText().contains(" *"),"Incomplete aggregate lacks marker");
            require(!((JLabel)get(report,"totalSalesLabel")).getText().contains("*"),"Known revenue marked incomplete");
        });
        test("unpriced migration layers remain unavailable even after a supplier receipt",()->{
            fresh();sql("INSERT INTO inventory_lots(product_id,received_date,initial_quantity,remaining_quantity,unit_cost,source_type) VALUES(1,1,10,10,0,'MIGRATION')");
            sale(20,2);
            require(value("SELECT cost_total FROM order_details WHERE id=20")==null,"Unknown migration cost treated as free");
            require(CostIntegrity.audit(db).salesWithoutHistoricalCost()==1,"Unknown consumption omitted");
        });
        test("cost requirement blocks both purchase and sale saves before data changes",()->{
            fresh();sql("UPDATE products SET acquisition_cost=99 WHERE id=1");sql("UPDATE product_suppliers SET purchase_price=0 WHERE product_id=1 AND is_preferred=1");
            require(!CostIntegrity.hasPurchaseCost(db,1),"Missing preferred supplier cost accepted");
            rejectedSave(order(false,2,"New"),"saveOrder","orderSaved");
            rejectedSave(supplier(false,new HashMap<>()),"saveOrder","orderSaved");
            require("0".equals(value("SELECT COUNT(*) FROM orders")) && "0".equals(value("SELECT COUNT(*) FROM supplier_orders")),"Unpriced product used by a document");
            sql("UPDATE product_suppliers SET purchase_price=5 WHERE product_id=1 AND is_preferred=1");
            require(CostIntegrity.hasPurchaseCost(db,1),"Corrected preferred supplier cost remains blocked");
        });
        test("supplier receipt is linked to its exact warehouse movement",()->{
            fresh();call(supplier(false,new HashMap<>()),"saveOrder");
            require("1".equals(value("SELECT COUNT(*) FROM inventory_lots l JOIN warehouse_movements m ON m.id=l.warehouse_movement_id WHERE l.source_type='SUPPLIER_ORDER' AND m.product_id=l.product_id AND m.quantity=l.initial_quantity AND m.document_type='SUPPLIER_ORDER'")),"Receipt/lot link missing");
        });
        test("received supplier price becomes both FIFO lot cost and current supplier cost",()->{
            fresh();
            sql("UPDATE product_suppliers SET purchase_price=5 WHERE product_id=1 AND supplier_id=1");
            sql("INSERT INTO supplier_orders(id,supplier_id,number,order_date,received_date,status,total) VALUES(90,1,'SYNC',1000,2000,'Completed',16)");
            sql("INSERT INTO supplier_order_details(id,order_id,product_id,quantity,unit_price,total) VALUES(90,90,1,2,8,16)");
            InventoryLotManager.receiveSupplierOrder(db,90,new java.util.Date(2000),"SYNC");
            require("8.0".equals(value("SELECT unit_cost FROM inventory_lots WHERE supplier_order_detail_id=90")),
                "Received unit price did not become FIFO lot cost");
            require("8.0".equals(value("SELECT purchase_price FROM product_suppliers WHERE product_id=1 AND supplier_id=1")),
                "Received unit price did not update supplier current cost");
            require("8.0".equals(value("SELECT acquisition_cost FROM products WHERE id=1")),
                "Received unit price did not update last purchase cost");
        });
        test("replacement supplier uses lowest price then oldest relationship without repricing stock",()->{
            fresh();seedOpeningLots();
            sql("INSERT INTO suppliers(id,company_name,vat_number) VALUES(3,'Third','TEST3'),(4,'Fourth','TEST4')");
            sql("INSERT INTO product_suppliers(product_id,supplier_id,purchase_price,is_preferred) VALUES(1,2,3,0),(1,3,2,0),(1,4,2,0)");
            sql("DELETE FROM product_suppliers WHERE product_id=1 AND supplier_id=1");
            var chosen=SupplierPricing.preferCheapestRemaining(db,1);
            require(chosen!=null && chosen.name().equals("Third"),"Cheapest/stable tie criterion failed");
            require("3".equals(value("SELECT supplier_id FROM products WHERE id=1")),"Legacy supplier cache stale");
            require("5.0".equals(value("SELECT acquisition_cost FROM products WHERE id=1")),"Supplier choice repriced actual purchase cost");
            require("5.0".equals(value("SELECT unit_cost FROM inventory_lots WHERE product_id=1")),"Supplier choice repriced FIFO lot");
            sql("DELETE FROM product_suppliers WHERE product_id=1");
            require(SupplierPricing.preferCheapestRemaining(db,1)==null && value("SELECT supplier_id FROM products WHERE id=1")==null,"No-alternative case kept a stale preferred supplier");
        });
        test("stock reductions retain FIFO history and reject silent zero-cost additions",()->{
            twoLayers();sql("INSERT INTO warehouse_movements(id,product_id,date,type,quantity,reason,document_type) VALUES(50,1,1,'OUTWARD',7,'INVENTORY','INVENTORY_ADJUSTMENT')");
            InventoryLotManager.adjustProductStock(db,50,1,-7,new java.util.Date(),5);
            sale(21,2);InventoryLotManager.reverseOutwardMovement(db,50);
            require("5,3".equals(balances()) && qty()==8,"Stock correction reversal was not exact");
            mustReject(()->InventoryLotManager.adjustProductStock(db,51,1,2,new java.util.Date(),0));
            require(qty()==8,"Unpriced adjustment changed physical stock");
        });
        test("supplier FIFO follows actual receipt date rather than order date",()->{
            fresh();sql("UPDATE products SET quantity=0 WHERE id=1");
            sql("INSERT INTO supplier_orders(id,supplier_id,number,order_date,received_date,status,total) VALUES" +
                "(30,1,'REC-LATE',1000,3000,'Completed',5)," +
                "(31,1,'REC-EARLY',2000,2000,'Completed',10)");
            sql("INSERT INTO supplier_order_details(order_id,product_id,quantity,unit_price,total) VALUES" +
                "(30,1,1,5,5),(31,1,1,10,10)");
            InventoryLotManager.receiveSupplierOrder(db,30,new java.util.Date(3000),"REC-LATE");
            InventoryLotManager.receiveSupplierOrder(db,31,new java.util.Date(2000),"REC-EARLY");
            sale(40,1);
            require("10.0".equals(value("SELECT cost_total FROM order_details WHERE id=40")),
                "FIFO followed supplier order date instead of receipt date");
        });
        test("supplier receipt backend rejects zero purchase price atomically",()->{
            fresh();int before=qty();
            sql("INSERT INTO supplier_orders(id,supplier_id,number,order_date,received_date,status,total) VALUES(30,1,'ZERO',1000,2000,'Completed',0)");
            sql("INSERT INTO supplier_order_details(order_id,product_id,quantity,unit_price,total) VALUES(30,1,2,0,0)");
            mustReject(()->InventoryLotManager.receiveSupplierOrder(db,30,new java.util.Date(2000),"ZERO"));
            require(qty()==before,"Rejected zero-price receipt changed physical stock");
            require("0".equals(value("SELECT COUNT(*) FROM inventory_lots WHERE supplier_order_detail_id IS NOT NULL")),
                "Rejected zero-price receipt created a FIFO lot");
            require("0".equals(value("SELECT COUNT(*) FROM warehouse_movements")),
                "Rejected zero-price receipt created a warehouse movement");
        });
        test("new FIFO messages are translated in every supported language",()->{
            String[] keys={"Historical cost unavailable","Reverse Movement","MOVEMENT_REVERSAL","Movement reversed successfully.",
                "Posted outward movements cannot be edited or deleted. Use Reverse Movement.",
                "This movement has already been reversed.","No alternative supplier remains. This product now has no preferred supplier.",
                "The last actual purchase cost, historical costs and existing FIFO lots will not change.","Database Updated",
                "Purchase price must be greater than zero","Supplier receipt date is required",
                "Every product must have a supplier, a positive quantity and a purchase price greater than zero. Check: {0}",
                "Preferred Supplier Cost","Click to edit the preferred supplier cost.",
                "Preferred supplier cost is the current purchase cost saved for the preferred supplier. Click the value to edit it.",
                "When a supplier order is received, its actual unit purchase price becomes the FIFO lot cost and updates that supplier's current cost.",
                "Last purchase cost is the unit cost of the most recently received supplier purchase. It is updated automatically and cannot be edited here.",
                "Purchase Cost Help"};
            try {
                for(String language:List.of("it","es","fr","de","pl","pt_BR")) {
                    I18n.setLanguage(language);
                    for(String key:keys) require(!key.equals(I18n.tr(key)),"Missing "+language+": "+key);
                }
            } finally { I18n.setLanguage("en"); }
        });
    }
}
