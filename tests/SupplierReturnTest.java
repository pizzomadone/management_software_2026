import java.util.List;
import java.util.Date;

/** End-to-end supplier return invariants on real SQLite FIFO tables. */
public final class SupplierReturnTest extends RegressionTest {
    private static void receipt() throws Exception {
        fresh();
        seedOpeningLots();
        sql("INSERT INTO supplier_orders(id,supplier_id,number,order_date,received_date,status,total) "
            +"VALUES(80,1,'PO-80',1000,2000,'Completed',124)");
        sql("INSERT INTO supplier_order_details(id,order_id,product_id,quantity,unit_price,total) "
            +"VALUES(81,80,1,8,12,96),(82,80,2,4,7,28)");
        InventoryLotManager.receiveSupplierOrder(db,80,new Date(2000),"PO-80");
    }
    private static SupplierReturnManager.Request q(int detailId,int units) {
        return new SupplierReturnManager.Request(detailId,units);
    }
    private static void reject(Check action) throws Exception {
        boolean failed=false;
        try {action.run();} catch(java.sql.SQLException expected){failed=true;}
        require(failed,"An invalid supplier return was accepted");
    }
    public static void supplierReturns() throws Exception {
        test("supplier return migration creates auditable relational tables",()->{
            fresh();
            require("2".equals(value("SELECT COUNT(*) FROM sqlite_master WHERE type='table' "
                +"AND name IN ('supplier_returns','supplier_return_items')")),
                "Supplier return schema is missing");
        });
        test("partial supplier returns accumulate without rewriting orders",()->{
            receipt();
            int first=SupplierReturnManager.create(80,List.of(q(81,3),q(82,1)),"damaged goods");
            require(first>0,"Return ID was not generated");
            require(qty()==15 && "13".equals(value("SELECT quantity FROM products WHERE id=2")),
                "Wrong stock after first partial return");
            require("5".equals(value("SELECT remaining_quantity FROM inventory_lots WHERE supplier_order_detail_id=81")),
                "Original FIFO stock was not debited");
            require(SupplierReturnManager.returnState(db,80)==1,"First return was not partial");
            require("Completed|124.0".equals(value("SELECT status||'|'||total FROM supplier_orders WHERE id=80")),
                "Original purchase order was mutated");
            require("48.0".equals(value("SELECT SUM(quantity*unit_price) FROM supplier_return_items WHERE return_id="+first)),
                "Historical return value was incorrect");
            int second=SupplierReturnManager.create(80,List.of(q(81,5),q(82,3)),"remaining goods");
            require(SupplierReturnManager.returnState(db,80)==2,"Fully returned order not detected");
            require(qty()==10 && "10".equals(value("SELECT quantity FROM products WHERE id=2")),
                "Wrong physical stock after full return");
            require("0".equals(value("SELECT remaining_quantity FROM inventory_lots WHERE supplier_order_detail_id=81")),
                "Returned FIFO lot was not depleted");
            require(SupplierReturnManager.history(db,80).size()==2,"Multiple returns were not retained");
            require("5.0".equals(value("SELECT acquisition_cost FROM products WHERE id=1")),
                "Valid opening-lot acquisition cost was lost");
            require(second>first,"Return documents are not independent");
        });
        test("supplier returns reject excess and duplicated quantities atomically",()->{
            receipt();int initial=qty();
            reject(()->SupplierReturnManager.create(80,List.of(q(81,3),q(81,2)),"duplicate"));
            reject(()->SupplierReturnManager.create(80,List.of(q(81,3),q(82,5)),"too many"));
            require(qty()==initial && "0".equals(value("SELECT COUNT(*) FROM supplier_returns")),
                "Rejected return changed stock or created documents");
            require("0".equals(value("SELECT COUNT(*) FROM inventory_lot_consumptions")),
                "Rejected return changed FIFO history");
        });
        test("reserved products cannot be returned to supplier",()->{
            receipt();
            sql("UPDATE products SET reserved_quantity=17 WHERE id=1");
            reject(()->SupplierReturnManager.create(80,List.of(q(81,2)),"reserved"));
            require(qty()==18 && "8".equals(value("SELECT remaining_quantity FROM inventory_lots WHERE supplier_order_detail_id=81")),
                "Reserved stock was removed");
            SupplierReturnManager.create(80,List.of(q(81,1)),"one available");
            require(qty()==17,"Available unreserved stock did not decrement");
        });
        test("sold units cannot be returned from a different FIFO lot",()->{
            fresh();
            sql("UPDATE products SET quantity=0 WHERE id=1");
            sql("INSERT INTO supplier_orders(id,supplier_id,number,order_date,received_date,status,total) "
                +"VALUES(80,1,'PO-80',1000,2000,'Completed',96)");
            sql("INSERT INTO supplier_order_details(id,order_id,product_id,quantity,unit_price,total) "
                +"VALUES(81,80,1,8,12,96)");
            InventoryLotManager.receiveSupplierOrder(db,80,new Date(2000),"PO-80");
            sql("INSERT INTO warehouse_movements(id,product_id,date,type,quantity,reason) "
                +"VALUES(55,1,3000,'OUTWARD',3,'SALE')");
            InventoryLotManager.applyManualMovement(db,55,1,"OUTWARD",3,new Date(3000));
            reject(()->SupplierReturnManager.create(80,List.of(q(81,6)),"used already"));
            SupplierReturnManager.create(80,List.of(q(81,5)),"remaining received goods");
            require(qty()==0,"Return of remaining FIFO stock was incorrect");
            require("3".equals(value("SELECT SUM(quantity) FROM inventory_lot_consumptions WHERE warehouse_movement_id=55")),
                "Previous consumption was changed");
            require(SupplierReturnManager.returnState(db,80)==1,"Sold quantity was mistaken for returned goods");
        });
        test("return storno restores exact lots and keeps outbound audit history",()->{
            receipt();int before=qty();
            int id=SupplierReturnManager.create(80,List.of(q(81,3)),"mistake");
            int movement=Integer.parseInt(value("SELECT warehouse_movement_id FROM supplier_return_items WHERE return_id="+id));
            reject(()->InventoryLotManager.reverseOutwardMovement(db,movement));
            reject(()->StockManager.requireManualMovement(db,movement));
            SupplierReturnManager.reverse(id);
            require(qty()==before && "8".equals(value("SELECT remaining_quantity FROM inventory_lots WHERE supplier_order_detail_id=81")),
                "Return reversal did not restore physical and FIFO stock");
            require(SupplierReturnManager.returnState(db,80)==0,"Reversed return is still counted");
            require("1".equals(value("SELECT COUNT(*) FROM inventory_lot_consumptions WHERE warehouse_movement_id="+movement)),
                "Reversal destroyed historical return consumption");
            require("1".equals(value("SELECT COUNT(*) FROM warehouse_movements WHERE reversal_of_movement_id="+movement)),
                "Reversal was not linked to the original movement");
            reject(()->SupplierReturnManager.reverse(id));
            reject(()->InventoryLotManager.reverseSupplierOrder(db,80,"PO-80"));
        });
        test("legacy Returned orders cannot be returned a second time",()->{
            receipt();sql("UPDATE supplier_orders SET status='Returned' WHERE id=80");
            reject(()->SupplierReturnManager.create(80,List.of(q(81,1)),"invalid legacy order"));
            require("0".equals(value("SELECT COUNT(*) FROM supplier_returns")),
                "Legacy return duplicated the stock movement");
        });
    }
}
