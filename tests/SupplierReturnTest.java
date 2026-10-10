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
    private static void expectIntegrityFailure(String expectedMessage) throws Exception {
        try {
            DatabaseIntegrity.validate(db,false);
        } catch(java.sql.SQLException e) {
            require(e.getMessage()!=null && e.getMessage().contains(expectedMessage),
                "Wrong integrity error: "+e.getMessage());
            return;
        }
        throw new AssertionError("Supplier return corruption escaped integrity validation");
    }

    public static void supplierReturns() throws Exception {
        test("import of pre-return v020 database preserves purchases and enables partial returns",()->{
            receipt();
            java.nio.file.Path oldFile=OUT.resolve("supplier-before-returns-v020.db");
            java.nio.file.Files.deleteIfExists(oldFile);
            try(java.sql.PreparedStatement p=db.prepareStatement("VACUUM INTO ?")) {
                p.setString(1,oldFile.toString());
                p.execute();
            }
            try(java.sql.Connection old=DatabaseManager.openConnection("jdbc:sqlite:"+oldFile);
                java.sql.Statement s=old.createStatement()) {
                s.execute("DROP TABLE supplier_return_items");
                s.execute("DROP TABLE supplier_returns");
                s.execute("DELETE FROM schema_migrations WHERE version='021'");
            }
            BackupManager backups=BackupManager.getInstance();
            BackupManager.BackupRecord imported=backups.importBackup(oldFile);
            java.sql.Connection original=DatabaseManager.getInstance().getConnection();
            try {
                try(java.sql.Connection upgraded=DatabaseManager.openConnection(
                        "jdbc:sqlite:"+imported.absolutePath())) {
                    DatabaseIntegrity.validate(upgraded,false);
                    try(java.sql.Statement s=upgraded.createStatement();
                        java.sql.ResultSet rows=s.executeQuery("""
                            SELECT (SELECT COUNT(*) FROM supplier_returns) AS returns,
                                   (SELECT COUNT(*) FROM schema_migrations WHERE version='021') AS version,
                                   (SELECT quantity FROM products WHERE id=1) AS physical,
                                   (SELECT remaining_quantity FROM inventory_lots WHERE supplier_order_detail_id=81) AS fifo,
                                   (SELECT status FROM supplier_orders WHERE id=80) AS status
                            """)) {
                        require(rows.next() && rows.getInt("returns")==0 && rows.getInt("version")==1
                            && rows.getInt("physical")==18 && rows.getInt("fifo")==8
                            && "Completed".equals(rows.getString("status")),
                            "Upgrade modified legacy purchase or FIFO stock");
                    }
                    require(SupplierReturnManager.lines(upgraded,80).stream()
                            .anyMatch(line->line.detailId()==81 && line.returnable()==8),
                        "Upgraded purchase cannot return its unused original FIFO units");
                    put(DatabaseManager.getInstance(),"connection",upgraded);
                    try {
                        int id=SupplierReturnManager.create(80,List.of(q(81,2)),"after import");
                        require(SupplierReturnManager.returnState(upgraded,80)==1,
                            "Partial return failed after importing an old database");
                        DatabaseIntegrity.validate(upgraded,false);
                        SupplierReturnManager.reverse(id);
                        DatabaseIntegrity.validate(upgraded,false);
                        DatabaseManager.prepareDatabase(upgraded);
                        require(SupplierReturnManager.returnState(upgraded,80)==0,
                            "Reopening an upgraded database changed return reversal history");
                        try(java.sql.Statement s=upgraded.createStatement();
                            java.sql.ResultSet rows=s.executeQuery(
                                "SELECT COUNT(*) FROM supplier_return_items")) {
                            require(rows.next() && rows.getInt(1)==1,
                                "Reopening an upgraded database erased return history");
                        }
                    } finally {
                        put(DatabaseManager.getInstance(),"connection",original);
                    }
                }
            } finally {
                put(DatabaseManager.getInstance(),"connection",original);
                backups.deleteBackup(imported.id());
            }
            require(java.nio.file.Files.isRegularFile(oldFile),
                "Import deleted or modified the user's original database");
        });
        test("pre-FIFO legacy purchases migrate without inventing returnable lots",()->{
            fresh();
            sql("INSERT INTO supplier_orders(id,supplier_id,number,order_date,status,total) "
                +"VALUES(80,1,'PO-OLD',1000,'Completed',96)");
            sql("INSERT INTO supplier_order_details(id,order_id,product_id,quantity,unit_price,total) "
                +"VALUES(81,80,1,8,12,96)");
            sql("UPDATE products SET quantity=18 WHERE id=1");
            java.nio.file.Path source=OUT.resolve("supplier-legacy-pre-fifo.db");
            java.nio.file.Files.deleteIfExists(source);
            try(java.sql.PreparedStatement p=db.prepareStatement("VACUUM INTO ?")) {
                p.setString(1,source.toString());
                p.execute();
            }
            try(java.sql.Connection old=DatabaseManager.openConnection("jdbc:sqlite:"+source);
                java.sql.Statement s=old.createStatement()) {
                s.execute("DROP TABLE supplier_return_items");
                s.execute("DROP TABLE supplier_returns");
                s.execute("DROP TABLE inventory_lot_consumptions");
                s.execute("DROP TABLE inventory_lots");
                s.execute("DROP TABLE product_suppliers");
                s.execute("DELETE FROM schema_migrations WHERE version>='012'");
            }
            BackupManager backups=BackupManager.getInstance();
            BackupManager.BackupRecord migrated=backups.importBackup(source);
            try {
                try(java.sql.Connection upgraded=DatabaseManager.openConnection(
                        "jdbc:sqlite:"+migrated.absolutePath())) {
                    DatabaseIntegrity.validate(upgraded,false);
                    try(java.sql.Statement s=upgraded.createStatement();
                        java.sql.ResultSet rows=s.executeQuery("""
                            SELECT (SELECT quantity FROM products WHERE id=1) AS physical,
                                   (SELECT remaining_quantity FROM inventory_lots
                                     WHERE product_id=1 AND source_type='MIGRATION') AS opening,
                                   (SELECT remaining_quantity FROM inventory_lots
                                     WHERE supplier_order_detail_id=81 AND source_type='LEGACY_SUPPLIER_ORDER')
                                     AS historical,
                                   (SELECT COUNT(*) FROM schema_migrations WHERE version='021') AS version
                            """)) {
                        require(rows.next() && rows.getInt("physical")==18 && rows.getInt("opening")==18
                            && rows.getInt("historical")==0 && rows.getInt("version")==1,
                            "Pre-FIFO migration lost physical stock or invented a purchase balance");
                    }
                    require(SupplierReturnManager.lines(upgraded,80).stream()
                            .allMatch(line->line.returnable()==0),
                        "Historical purchase was incorrectly marked returnable without original FIFO provenance");
                    DatabaseManager.prepareDatabase(upgraded);
                    DatabaseIntegrity.validate(upgraded,false);
                }
            } finally {backups.deleteBackup(migrated.id());}
        });
        test("tampered supplier returns are rejected during import",()->{
            receipt();
            SupplierReturnManager.create(80,List.of(q(81,3)),"test");
            java.nio.file.Path bad=OUT.resolve("supplier-return-tampered.db");
            java.nio.file.Files.deleteIfExists(bad);
            try(java.sql.PreparedStatement p=db.prepareStatement("VACUUM INTO ?")) {
                p.setString(1,bad.toString());
                p.execute();
            }
            try(java.sql.Connection c=DatabaseManager.openConnection("jdbc:sqlite:"+bad);
                java.sql.Statement s=c.createStatement()) {
                s.execute("UPDATE warehouse_movements SET quantity=1 "
                    +"WHERE document_type='SUPPLIER_RETURN'");
            }
            boolean rejected=false;
            try {BackupManager.getInstance().importBackup(bad);}
            catch(IllegalArgumentException expected){rejected=true;}
            require(rejected,"Corrupted supplier return was accepted during import");
            require("1".equals(value("SELECT COUNT(*) FROM supplier_returns"))
                && "3".equals(value("SELECT quantity FROM warehouse_movements WHERE document_type='SUPPLIER_RETURN'")),
                "Failed import altered the live supplier return history");
        });
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
            require("43.0".equals(value("SELECT SUM(quantity*unit_price) FROM supplier_return_items WHERE return_id="+first)),
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
        test("supplier return integrity accepts valid partial and reversed returns",()->{
            receipt();
            int first=SupplierReturnManager.create(80,List.of(q(81,2),q(82,1)),"partial");
            DatabaseIntegrity.validate(db,false);
            SupplierReturnManager.create(80,List.of(q(81,1)),"later");
            DatabaseIntegrity.validate(db,false);
            SupplierReturnManager.reverse(first);
            DatabaseIntegrity.validate(db,false);
            require("2".equals(value("SELECT COUNT(*) FROM supplier_returns")),
                "Validation changed existing return history");
        });
        test("supplier return integrity detects wrong originating order detail",()->{
            receipt();
            int id=SupplierReturnManager.create(80,List.of(q(81,2)),"wrong lot");
            sql("UPDATE supplier_return_items SET supplier_order_detail_id=82 WHERE return_id="+id);
            expectIntegrityFailure("wrong purchase or FIFO lot");
        });
        test("supplier return integrity detects excess returned quantity",()->{
            receipt();
            int id=SupplierReturnManager.create(80,List.of(q(81,3)),"too many");
            sql("UPDATE supplier_return_items SET quantity=9 WHERE return_id="+id);
            expectIntegrityFailure("exceed purchased quantity");
        });
        test("supplier return integrity detects missing warehouse movement quantity",()->{
            receipt();
            int id=SupplierReturnManager.create(80,List.of(q(81,3)),"movement");
            sql("UPDATE warehouse_movements SET quantity=1 WHERE id="
                +"(SELECT warehouse_movement_id FROM supplier_return_items WHERE return_id="+id+")");
            expectIntegrityFailure("warehouse movement or FIFO consumption");
        });
        test("supplier return integrity detects wrong FIFO consumption quantity",()->{
            receipt();
            int id=SupplierReturnManager.create(80,List.of(q(81,3)),"fifo");
            sql("UPDATE inventory_lot_consumptions SET quantity=1 WHERE warehouse_movement_id="
                +"(SELECT warehouse_movement_id FROM supplier_return_items WHERE return_id="+id+")");
            expectIntegrityFailure("warehouse movement or FIFO consumption");
        });
        test("supplier return integrity detects original lot not decremented",()->{
            receipt();
            SupplierReturnManager.create(80,List.of(q(81,3)),"stock");
            sql("UPDATE inventory_lots SET remaining_quantity=initial_quantity "
                +"WHERE supplier_order_detail_id=81");
            expectIntegrityFailure("not deducted from FIFO lot");
        });
        test("supplier return integrity detects missing storno movement",()->{
            receipt();
            int id=SupplierReturnManager.create(80,List.of(q(81,3)),"storno");
            SupplierReturnManager.reverse(id);
            sql("DELETE FROM warehouse_movements WHERE document_type='SUPPLIER_RETURN_REVERSAL'");
            expectIntegrityFailure("reversal is missing");
        });
        test("supplier return integrity rejects premature compensating movements",()->{
            receipt();
            int id=SupplierReturnManager.create(80,List.of(q(81,3)),"premature");
            sql("INSERT INTO warehouse_movements "
                +"(product_id,date,type,quantity,reason,document_number,document_type,reversal_of_movement_id)"
                +" VALUES(1,CURRENT_TIMESTAMP,'INWARD',3,'SUPPLIER_RETURN_REVERSAL','R"+id+"',"
                +"'SUPPLIER_RETURN_REVERSAL',"
                +"(SELECT warehouse_movement_id FROM supplier_return_items WHERE return_id="+id+"))");
            expectIntegrityFailure("reversal is missing");
        });
        test("supplier return integrity rejects empty return documents",()->{
            receipt();
            sql("INSERT INTO supplier_returns(order_id,returned_at) VALUES (80,CURRENT_TIMESTAMP)");
            expectIntegrityFailure("has no items");
        });
        test("deletion preflight allows untouched supplier purchases",()->{
            receipt();
            require(SuppliersPanel.supplierDeletionBlockReason(db,1)==null,
                "An untouched supplier receipt should remain deletable");
        });
        test("deletion preflight blocks returns even after reversal",()->{
            receipt();
            int id=SupplierReturnManager.create(80,List.of(q(81,1)),"test");
            require(SuppliersPanel.supplierDeletionBlockReason(db,1)!=null,
                "Return history was not detected");
            SupplierReturnManager.reverse(id);
            require(SuppliersPanel.supplierDeletionBlockReason(db,1)!=null,
                "Reversed return history was not detected");
        });
        test("deletion preflight blocks consumed original FIFO goods",()->{
            receipt();
            sql("INSERT INTO warehouse_movements(id,product_id,date,type,quantity,reason) "
                +"VALUES(59,1,3000,'OUTWARD',11,'SALE')");
            InventoryLotManager.applyManualMovement(db,59,1,"OUTWARD",11,new Date(3000));
            require(SuppliersPanel.supplierDeletionBlockReason(db,1)!=null,
                "Historical supplier lot consumption was not detected");
        });
        test("deletion preflight detects missing receipt FIFO lot",()->{
            fresh();
            sql("INSERT INTO supplier_orders(id,supplier_id,number,order_date,status,total) "
                +"VALUES(80,1,'PO-80',1000,'Completed',12)");
            sql("INSERT INTO supplier_order_details(id,order_id,product_id,quantity,unit_price,total) "
                +"VALUES(81,80,1,1,12,12)");
            require(SuppliersPanel.supplierDeletionBlockReason(db,1)!=null,
                "Missing original FIFO lot was not detected");
        });
        test("deletion preflight blocks supplier FK on independent FIFO lots",()->{
            fresh();
            sql("INSERT INTO inventory_lots(product_id,supplier_id,received_date,"
                +"initial_quantity,remaining_quantity,unit_cost,source_type) "
                +"VALUES(1,1,1000,1,1,5,'MANUAL_INWARD')");
            require(SuppliersPanel.supplierDeletionBlockReason(db,1)!=null,
                "Supplier reference in a manual FIFO lot was not detected");
        });
        test("legacy Returned orders cannot be returned a second time",()->{
            receipt();sql("UPDATE supplier_orders SET status='Returned' WHERE id=80");
            reject(()->SupplierReturnManager.create(80,List.of(q(81,1)),"invalid legacy order"));
            require("0".equals(value("SELECT COUNT(*) FROM supplier_returns")),
                "Legacy return duplicated the stock movement");
        });
    }
}
