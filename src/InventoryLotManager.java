import java.sql.*;
import java.util.Date;

/**
 * FIFO inventory valuation. Product quantity remains the fast physical-stock cache;
 * inventory_lots keeps the economic layers used for historical cost of sales.
 */
public final class InventoryLotManager {
    private InventoryLotManager() {}

    public static void receiveSupplierOrder(Connection c, int orderId, Date date, String number)
            throws SQLException {
        int supplierId;
        try (PreparedStatement p=c.prepareStatement("SELECT supplier_id FROM supplier_orders WHERE id=?")) {
            p.setInt(1,orderId);
            try (ResultSet r=p.executeQuery()) {
                if(!r.next()) throw new SQLException("Supplier order no longer exists");
                supplierId=r.getInt(1);
            }
        }

        String q="""
            SELECT id,product_id,quantity,unit_price
            FROM supplier_order_details WHERE order_id=? ORDER BY id
            """;
        try (PreparedStatement p=c.prepareStatement(q)) {
            p.setInt(1,orderId);
            try (ResultSet r=p.executeQuery()) {
                while(r.next()) {
                    int detailId=r.getInt("id");
                    if(hasSupplierLot(c,detailId))
                        throw new SQLException("Supplier order receipt has already been posted");

                    int productId=r.getInt("product_id");
                    int quantity=Numbers.positiveQuantity(r.getObject("quantity"));
                    double cost=Numbers.nonNegative(r.getObject("unit_price"));
                    int oldStock=physicalStock(c,productId);
                    int remaining=Math.max(0,Math.min(quantity,Math.addExact(oldStock,quantity)));

                    try(PreparedStatement u=c.prepareStatement(
                            "UPDATE products SET quantity=quantity+?,acquisition_cost=? WHERE id=?")) {
                        u.setInt(1,quantity);u.setDouble(2,cost);u.setInt(3,productId);
                        if(u.executeUpdate()!=1) throw new SQLException("Product no longer exists");
                    }

                    try(PreparedStatement lot=c.prepareStatement("""
                            INSERT INTO inventory_lots
                            (product_id,supplier_id,supplier_order_detail_id,received_date,
                             initial_quantity,remaining_quantity,unit_cost,source_type)
                            VALUES(?,?,?,?,?,?,?,'SUPPLIER_ORDER')
                            """)) {
                        lot.setInt(1,productId);lot.setInt(2,supplierId);lot.setInt(3,detailId);
                        lot.setTimestamp(4,DateUtils.toSqlTimestamp(date));
                        lot.setInt(5,quantity);lot.setInt(6,remaining);lot.setDouble(7,cost);
                        lot.executeUpdate();
                    }

                    insertMovement(c,productId,date,"INWARD",quantity,"PURCHASE",number,
                        "SUPPLIER_ORDER","Supplier order "+number);
                }
            }
        }
    }

    public static void reverseSupplierOrder(Connection c, int orderId, String number)
            throws SQLException {
        try(PreparedStatement p=c.prepareStatement("""
                SELECT l.id,l.product_id,l.initial_quantity,l.remaining_quantity
                FROM inventory_lots l
                JOIN supplier_order_details d ON d.id=l.supplier_order_detail_id
                WHERE d.order_id=? ORDER BY l.id
                """)) {
            p.setInt(1,orderId);
            try(ResultSet r=p.executeQuery()) {
                while(r.next()) {
                    if(r.getInt("remaining_quantity")!=r.getInt("initial_quantity"))
                        throw new SQLException("This supplier receipt cannot be reversed because part of its FIFO lot has already been consumed");
                }
            }
        }

        java.util.LinkedHashSet<Integer> products=new java.util.LinkedHashSet<>();
        try(PreparedStatement p=c.prepareStatement("""
                SELECT l.id,l.product_id,l.initial_quantity
                FROM inventory_lots l
                JOIN supplier_order_details d ON d.id=l.supplier_order_detail_id
                WHERE d.order_id=? ORDER BY l.id
                """)) {
            p.setInt(1,orderId);
            try(ResultSet r=p.executeQuery()) {
                while(r.next()) {
                    int productId=r.getInt("product_id");
                    products.add(productId);
                    try(PreparedStatement u=c.prepareStatement(
                            "UPDATE products SET quantity=quantity-? WHERE id=?")) {
                        u.setInt(1,r.getInt("initial_quantity"));u.setInt(2,productId);u.executeUpdate();
                    }
                }
            }
        }
        try(PreparedStatement d=c.prepareStatement("""
                DELETE FROM inventory_lots
                WHERE supplier_order_detail_id IN
                    (SELECT id FROM supplier_order_details WHERE order_id=?)
                """)) {
            d.setInt(1,orderId);d.executeUpdate();
        }
        StockManager.deleteWarehouseMovements(c,"SUPPLIER_ORDER",number);
        for(int productId:products) refreshLastPurchaseCost(c,productId,orderId);
    }

    public static void consumeOrder(Connection c,int orderId) throws SQLException {
        try(PreparedStatement check=c.prepareStatement("""
                SELECT 1 FROM inventory_lot_consumptions x
                JOIN order_details d ON d.id=x.order_detail_id
                WHERE d.order_id=? LIMIT 1
                """)) {
            check.setInt(1,orderId);
            try(ResultSet r=check.executeQuery()) {
                if(r.next()) throw new SQLException("FIFO cost has already been posted for this order");
            }
        }

        try(PreparedStatement p=c.prepareStatement(
                "SELECT id,product_id,quantity FROM order_details WHERE order_id=? ORDER BY id")) {
            p.setInt(1,orderId);
            try(ResultSet r=p.executeQuery()) {
                while(r.next()) consumeOrderDetail(c,r.getInt("id"),r.getInt("product_id"),
                    Numbers.positiveQuantity(r.getObject("quantity")));
            }
        }
    }

    private static void consumeOrderDetail(Connection c,int detailId,int productId,int quantity)
            throws SQLException {
        int needed=quantity;
        double costTotal=0;
        try(PreparedStatement lots=c.prepareStatement("""
                SELECT id,remaining_quantity,unit_cost
                FROM inventory_lots
                WHERE product_id=? AND remaining_quantity>0
                ORDER BY received_date,id
                """)) {
            lots.setInt(1,productId);
            try(ResultSet r=lots.executeQuery()) {
                while(needed>0 && r.next()) {
                    int available=r.getInt("remaining_quantity");
                    int take=Math.min(needed,available);
                    double cost=Numbers.nonNegative(r.getObject("unit_cost"));
                    updateLotRemaining(c,r.getInt("id"),-take);
                    insertConsumption(c,r.getInt("id"),detailId,null,take,cost);
                    costTotal=Numbers.add(costTotal,Numbers.lineTotal(take,cost));
                    needed-=take;
                }
            }
        }
        if(needed>0) {
            double fallback=currentAcquisitionCost(c,productId);
            insertConsumption(c,null,detailId,null,needed,fallback);
            costTotal=Numbers.add(costTotal,Numbers.lineTotal(needed,fallback));
        }
        try(PreparedStatement u=c.prepareStatement("UPDATE order_details SET cost_total=? WHERE id=?")) {
            u.setDouble(1,Numbers.money(java.math.BigDecimal.valueOf(costTotal)));
            u.setInt(2,detailId);u.executeUpdate();
        }
    }

    public static void restoreOrder(Connection c,int orderId) throws SQLException {
        try(PreparedStatement details=c.prepareStatement(
                "SELECT id,product_id,quantity,cost_total FROM order_details WHERE order_id=? ORDER BY id")) {
            details.setInt(1,orderId);
            try(ResultSet d=details.executeQuery()) {
                while(d.next()) {
                    int detailId=d.getInt("id");
                    int restored=0;
                    try(PreparedStatement p=c.prepareStatement("""
                            SELECT lot_id,quantity FROM inventory_lot_consumptions
                            WHERE order_detail_id=? ORDER BY id
                            """)) {
                        p.setInt(1,detailId);
                        try(ResultSet r=p.executeQuery()) {
                            while(r.next()) {
                                int q=r.getInt("quantity");restored+=q;
                                Object lot=r.getObject("lot_id");
                                if(lot!=null) updateLotRemaining(c,((Number)lot).intValue(),q);
                            }
                        }
                    }
                    try(PreparedStatement x=c.prepareStatement(
                            "DELETE FROM inventory_lot_consumptions WHERE order_detail_id=?")) {
                        x.setInt(1,detailId);x.executeUpdate();
                    }

                    // Legacy completed orders predate FIFO allocations. When such an order
                    // is reopened, create a layer for the stock being restored.
                    if(restored==0) {
                        int q=Numbers.positiveQuantity(d.getObject("quantity"));
                        double cost;
                        Object saved=d.getObject("cost_total");
                        if(saved!=null) cost=Numbers.nonNegative(((Number)saved).doubleValue()/q);
                        else cost=currentAcquisitionCost(c,d.getInt("product_id"));
                        insertLot(c,d.getInt("product_id"),null,null,null,new Date(),q,q,cost,"LEGACY_RESTORE");
                    }
                    try(PreparedStatement u=c.prepareStatement(
                            "UPDATE order_details SET cost_total=NULL WHERE id=?")) {
                        u.setInt(1,detailId);u.executeUpdate();
                    }
                }
            }
        }
    }

    public static void applyManualMovement(Connection c,int movementId,int productId,String type,
                                           int quantity,Date date) throws SQLException {
        quantity=Numbers.positiveQuantity(quantity);
        int oldStock=physicalStock(c,productId);
        if("INWARD".equals(type)) {
            double cost=currentAcquisitionCost(c,productId);
            int remaining=Math.max(0,Math.min(quantity,Math.addExact(oldStock,quantity)));
            try(PreparedStatement u=c.prepareStatement("UPDATE products SET quantity=quantity+? WHERE id=?")) {
                u.setInt(1,quantity);u.setInt(2,productId);u.executeUpdate();
            }
            insertLot(c,productId,null,null,movementId,date,quantity,remaining,cost,"MANUAL_INWARD");
        } else if("OUTWARD".equals(type)) {
            consumeForMovement(c,movementId,productId,quantity);
            try(PreparedStatement u=c.prepareStatement("UPDATE products SET quantity=quantity-? WHERE id=?")) {
                u.setInt(1,quantity);u.setInt(2,productId);u.executeUpdate();
            }
        } else throw new SQLException("Unsupported movement type: "+type);
    }

    public static void reverseManualMovement(Connection c,int movementId,int productId,
                                             String type,int quantity) throws SQLException {
        if("INWARD".equals(type)) {
            try(PreparedStatement p=c.prepareStatement("""
                    SELECT id,initial_quantity,remaining_quantity FROM inventory_lots
                    WHERE warehouse_movement_id=?
                    """)) {
                p.setInt(1,movementId);
                try(ResultSet r=p.executeQuery()) {
                    if(r.next()) {
                        if(r.getInt("remaining_quantity")!=r.getInt("initial_quantity"))
                            throw new SQLException("This inward movement cannot be changed because part of its FIFO lot has already been consumed");
                        try(PreparedStatement d=c.prepareStatement("DELETE FROM inventory_lots WHERE id=?")) {
                            d.setInt(1,r.getInt("id"));d.executeUpdate();
                        }
                    }
                }
            }
            try(PreparedStatement u=c.prepareStatement("UPDATE products SET quantity=quantity-? WHERE id=?")) {
                u.setInt(1,quantity);u.setInt(2,productId);u.executeUpdate();
            }
        } else if("OUTWARD".equals(type)) {
            restoreMovementConsumptions(c,movementId);
            try(PreparedStatement u=c.prepareStatement("UPDATE products SET quantity=quantity+? WHERE id=?")) {
                u.setInt(1,quantity);u.setInt(2,productId);u.executeUpdate();
            }
        } else throw new SQLException("Unsupported movement type: "+type);
    }

    public static void adjustProductStock(Connection c,int productId,int delta,Date date,double cost)
            throws SQLException {
        if(delta==0)return;
        int oldStock=physicalStock(c,productId);
        if(delta>0) {
            int remaining=Math.max(0,Math.min(delta,Math.addExact(oldStock,delta)));
            try(PreparedStatement u=c.prepareStatement("UPDATE products SET quantity=quantity+? WHERE id=?")) {
                u.setInt(1,delta);u.setInt(2,productId);u.executeUpdate();
            }
            insertLot(c,productId,null,null,null,date,delta,remaining,Numbers.nonNegative(cost),"INVENTORY_ADJUSTMENT");
        } else {
            int qty=Math.negateExact(delta);
            consumeWithoutHistory(c,productId,qty);
            try(PreparedStatement u=c.prepareStatement("UPDATE products SET quantity=quantity-? WHERE id=?")) {
                u.setInt(1,qty);u.setInt(2,productId);u.executeUpdate();
            }
        }
    }

    private static void consumeForMovement(Connection c,int movementId,int productId,int quantity)
            throws SQLException {
        int needed=quantity;
        try(PreparedStatement lots=c.prepareStatement("""
                SELECT id,remaining_quantity,unit_cost FROM inventory_lots
                WHERE product_id=? AND remaining_quantity>0 ORDER BY received_date,id
                """)) {
            lots.setInt(1,productId);
            try(ResultSet r=lots.executeQuery()) {
                while(needed>0 && r.next()) {
                    int take=Math.min(needed,r.getInt("remaining_quantity"));
                    double cost=Numbers.nonNegative(r.getObject("unit_cost"));
                    updateLotRemaining(c,r.getInt("id"),-take);
                    insertConsumption(c,r.getInt("id"),null,movementId,take,cost);
                    needed-=take;
                }
            }
        }
        if(needed>0) insertConsumption(c,null,null,movementId,needed,currentAcquisitionCost(c,productId));
    }

    private static void consumeWithoutHistory(Connection c,int productId,int quantity) throws SQLException {
        int needed=quantity;
        try(PreparedStatement lots=c.prepareStatement("""
                SELECT id,remaining_quantity FROM inventory_lots
                WHERE product_id=? AND remaining_quantity>0 ORDER BY received_date,id
                """)) {
            lots.setInt(1,productId);
            try(ResultSet r=lots.executeQuery()) {
                while(needed>0 && r.next()) {
                    int take=Math.min(needed,r.getInt("remaining_quantity"));
                    updateLotRemaining(c,r.getInt("id"),-take);needed-=take;
                }
            }
        }
    }

    private static void restoreMovementConsumptions(Connection c,int movementId) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(
                "SELECT lot_id,quantity FROM inventory_lot_consumptions WHERE warehouse_movement_id=?")) {
            p.setInt(1,movementId);
            try(ResultSet r=p.executeQuery()) {
                while(r.next()) {
                    Object lot=r.getObject("lot_id");
                    if(lot!=null) updateLotRemaining(c,((Number)lot).intValue(),r.getInt("quantity"));
                }
            }
        }
        try(PreparedStatement d=c.prepareStatement(
                "DELETE FROM inventory_lot_consumptions WHERE warehouse_movement_id=?")) {
            d.setInt(1,movementId);d.executeUpdate();
        }
    }

    private static void insertConsumption(Connection c,Integer lotId,Integer detailId,Integer movementId,
                                          int quantity,double unitCost) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("""
                INSERT INTO inventory_lot_consumptions
                (lot_id,order_detail_id,warehouse_movement_id,quantity,unit_cost)
                VALUES(?,?,?,?,?)
                """)) {
            if(lotId==null)p.setNull(1,Types.INTEGER);else p.setInt(1,lotId);
            if(detailId==null)p.setNull(2,Types.INTEGER);else p.setInt(2,detailId);
            if(movementId==null)p.setNull(3,Types.INTEGER);else p.setInt(3,movementId);
            p.setInt(4,quantity);p.setDouble(5,unitCost);p.executeUpdate();
        }
    }

    private static void insertLot(Connection c,int productId,Integer supplierId,Integer detailId,
                                  Integer movementId,Date date,int initial,int remaining,
                                  double cost,String source) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("""
                INSERT INTO inventory_lots
                (product_id,supplier_id,supplier_order_detail_id,warehouse_movement_id,received_date,
                 initial_quantity,remaining_quantity,unit_cost,source_type)
                VALUES(?,?,?,?,?,?,?,?,?)
                """)) {
            p.setInt(1,productId);
            if(supplierId==null)p.setNull(2,Types.INTEGER);else p.setInt(2,supplierId);
            if(detailId==null)p.setNull(3,Types.INTEGER);else p.setInt(3,detailId);
            if(movementId==null)p.setNull(4,Types.INTEGER);else p.setInt(4,movementId);
            p.setTimestamp(5,DateUtils.toSqlTimestamp(date));
            p.setInt(6,initial);p.setInt(7,remaining);p.setDouble(8,cost);p.setString(9,source);
            p.executeUpdate();
        }
    }

    private static void updateLotRemaining(Connection c,int lotId,int delta) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(
                "UPDATE inventory_lots SET remaining_quantity=remaining_quantity+? WHERE id=?")) {
            p.setInt(1,delta);p.setInt(2,lotId);
            if(p.executeUpdate()!=1)throw new SQLException("FIFO lot no longer exists");
        }
    }

    private static boolean hasSupplierLot(Connection c,int detailId) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(
                "SELECT 1 FROM inventory_lots WHERE supplier_order_detail_id=? LIMIT 1")) {
            p.setInt(1,detailId);try(ResultSet r=p.executeQuery()){return r.next();}
        }
    }

    private static int physicalStock(Connection c,int productId) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT quantity FROM products WHERE id=?")) {
            p.setInt(1,productId);try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new SQLException("Product no longer exists");return r.getInt(1);
            }
        }
    }

    private static double currentAcquisitionCost(Connection c,int productId) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT acquisition_cost FROM products WHERE id=?")) {
            p.setInt(1,productId);try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new SQLException("Product no longer exists");
                return Numbers.nonNegative(r.getObject(1));
            }
        }
    }

    private static void refreshLastPurchaseCost(Connection c,int productId,int excludedOrderId)
            throws SQLException {
        Double cost=null;
        try(PreparedStatement p=c.prepareStatement("""
                SELECT d.unit_price
                FROM supplier_order_details d
                JOIN supplier_orders o ON o.id=d.order_id
                WHERE d.product_id=? AND o.status='Completed' AND o.id<>?
                ORDER BY o.order_date DESC,o.id DESC,d.id DESC LIMIT 1
                """)) {
            p.setInt(1,productId);p.setInt(2,excludedOrderId);
            try(ResultSet r=p.executeQuery()){if(r.next())cost=Numbers.nonNegative(r.getObject(1));}
        }
        if(cost==null) {
            try(PreparedStatement p=c.prepareStatement("""
                    SELECT purchase_price FROM product_suppliers
                    WHERE product_id=? AND is_preferred=1 LIMIT 1
                    """)) {
                p.setInt(1,productId);try(ResultSet r=p.executeQuery()){
                    if(r.next())cost=Numbers.nonNegative(r.getObject(1));
                }
            }
        }
        if(cost!=null)try(PreparedStatement p=c.prepareStatement(
                "UPDATE products SET acquisition_cost=? WHERE id=?")) {
            p.setDouble(1,cost);p.setInt(2,productId);p.executeUpdate();
        }
    }

    private static void insertMovement(Connection c,int productId,Date date,String type,int quantity,
                                       String reason,String number,String documentType,String notes)
            throws SQLException {
        try(PreparedStatement p=c.prepareStatement("""
                INSERT INTO warehouse_movements
                (product_id,date,type,quantity,reason,document_number,document_type,notes)
                VALUES(?,?,?,?,?,?,?,?)
                """)) {
            p.setInt(1,productId);p.setTimestamp(2,DateUtils.toSqlTimestamp(date));p.setString(3,type);
            p.setInt(4,quantity);p.setString(5,reason);p.setString(6,number);
            p.setString(7,documentType);p.setString(8,notes);p.executeUpdate();
        }
    }
}
