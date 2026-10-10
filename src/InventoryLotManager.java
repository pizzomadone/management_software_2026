import java.sql.*;
import java.util.Date;

/**
 * FIFO inventory valuation. Product quantity remains the fast physical-stock cache;
 * inventory_lots keeps the economic layers used for historical cost of sales.
 */
public final class InventoryLotManager {

    public static String sourceDisplayName(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) return "";
        return switch (sourceType.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "SUPPLIER_ORDER" -> I18n.tr("Supplier order");
            case "LEGACY_SUPPLIER_ORDER" -> I18n.tr("Historical supplier order");
            case "MIGRATION" -> I18n.tr("Opening stock");
            case "DEMO_OPENING" -> I18n.tr("Demo opening stock");
            case "DEMO_SALE_HISTORY" -> I18n.tr("Demo sales history");
            case "MANUAL_INWARD" -> I18n.tr("Manual inward");
            case "INVENTORY_ADJUSTMENT" -> I18n.tr("Inventory adjustment");
            default -> humanizeSourceType(sourceType);
        };
    }

    private static String humanizeSourceType(String sourceType) {
        String normalized = sourceType.trim().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) return "";
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private InventoryLotManager() {}

    public static void receiveSupplierOrder(Connection c, int orderId, Date date, String number)
            throws SQLException {
        if (date == null) throw new SQLException(I18n.tr("Supplier receipt date is required"));
        try (PreparedStatement p=c.prepareStatement("""
                SELECT 1
                FROM supplier_order_details
                WHERE order_id=? AND (unit_price IS NULL OR unit_price<=0)
                LIMIT 1
                """)) {
            p.setInt(1,orderId);
            try (ResultSet r=p.executeQuery()) {
                if(r.next()) throw new SQLException(I18n.tr("Purchase price must be greater than zero"));
            }
        }

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
                    if(cost<=0) throw new SQLException(I18n.tr("Purchase price must be greater than zero"));

                    // The received unit price is the real purchase cost of this receipt:
                    // it becomes both the FIFO lot cost and the supplier's current
                    // product cost used as the default for future supplier orders.
                    try(PreparedStatement u=c.prepareStatement("""
                            UPDATE product_suppliers SET purchase_price=?
                            WHERE product_id=? AND supplier_id=?
                            """)) {
                        u.setDouble(1,cost);u.setInt(2,productId);u.setInt(3,supplierId);
                        u.executeUpdate();
                    }

                    int oldStock=physicalStock(c,productId);
                    int remaining=Math.max(0,Math.min(quantity,Math.addExact(oldStock,quantity)));

                    try(PreparedStatement u=c.prepareStatement(
                            "UPDATE products SET quantity=quantity+?,acquisition_cost=? WHERE id=?")) {
                        u.setInt(1,quantity);u.setDouble(2,cost);u.setInt(3,productId);
                        if(u.executeUpdate()!=1) throw new SQLException("Product no longer exists");
                    }

                    int movementId = insertMovement(c,productId,date,"INWARD",quantity,"PURCHASE",number,
                        "SUPPLIER_ORDER","Supplier order "+number);
                    insertLot(c,productId,supplierId,detailId,movementId,date,quantity,remaining,cost,"SUPPLIER_ORDER");
                }
            }
        }
    }

    public static boolean hasSupplierOrderConsumption(Connection c, int orderId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT 1 FROM inventory_lot_consumptions x "
                + "JOIN inventory_lots l ON l.id=x.lot_id "
                + "JOIN supplier_order_details d ON d.id=l.supplier_order_detail_id "
                + "WHERE d.order_id=? LIMIT 1")) {
            p.setInt(1, orderId);
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        }
    }

    /** Returns every received unit, retaining the original receipt and FIFO audit trail. */
    public static void returnSupplierOrder(Connection c, int orderId, String number) throws SQLException {
        java.util.List<int[]> lots = new java.util.ArrayList<>();
        try (PreparedStatement p=c.prepareStatement("""
                SELECT l.id,l.product_id,l.initial_quantity,l.remaining_quantity
                FROM inventory_lots l
                JOIN supplier_order_details d ON d.id=l.supplier_order_detail_id
                WHERE d.order_id=? ORDER BY l.id
                """)) {
            p.setInt(1,orderId);
            try (ResultSet r=p.executeQuery()) {
                while (r.next()) {
                    int q=r.getInt("initial_quantity");
                    if (q<=0 || r.getInt("remaining_quantity")!=q)
                        throw new SQLException(I18n.tr("Supplier return requires all received goods to be available in their original FIFO lots."));
                    lots.add(new int[]{r.getInt("id"),r.getInt("product_id"),q});
                }
            }
        }
        int expected;
        try (PreparedStatement p=c.prepareStatement(
                "SELECT COUNT(*) FROM supplier_order_details WHERE order_id=?")) {
            p.setInt(1,orderId);
            try (ResultSet r=p.executeQuery()) { r.next();expected=r.getInt(1); }
        }
        if (lots.isEmpty() || lots.size()!=expected)
            throw new SQLException(I18n.tr("Supplier return requires a complete FIFO receipt history."));
        java.util.Map<Integer,Integer> totals=new java.util.LinkedHashMap<>();
        for(int[] lot:lots) totals.merge(lot[1],lot[2],Integer::sum);
        for(var e:totals.entrySet())
            if(StockManager.getAvailableStock(c,e.getKey())<e.getValue())
                throw new SQLException(I18n.tr("Supplier return requires all received goods to be available in their original FIFO lots."));
        Date now=new Date();
        for(int[] lot:lots) {
            int id=lot[0],productId=lot[1],quantity=lot[2];
            int movementId=insertMovement(c,productId,now,"OUTWARD",quantity,
                "SUPPLIER_RETURN",number,"SUPPLIER_RETURN",I18n.tr("Returned to supplier"));
            double unitCost;
            try(PreparedStatement p=c.prepareStatement("SELECT unit_cost FROM inventory_lots WHERE id=?")) {
                p.setInt(1,id);
                try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("FIFO lot no longer exists");unitCost=r.getDouble(1);}
            }
            insertConsumption(c,id,null,movementId,quantity,unitCost);
            updateLotRemaining(c,id,-quantity);
            try(PreparedStatement p=c.prepareStatement("UPDATE products SET quantity=quantity-? WHERE id=?")) {
                p.setInt(1,quantity);p.setInt(2,productId);
                if(p.executeUpdate()!=1)throw new SQLException("Product no longer exists");
            }
        }
        for(Integer id:totals.keySet()) refreshLastPurchaseCost(c,id,orderId);
    }

    /**
     * Reversing a received supplier order is atomic even for direct callers.
     * Callers deleting/editing an order or deleting a supplier already have
     * a surrounding transaction; in that case the savepoint preserves it.
     */
    public static void reverseSupplierOrder(Connection c, int orderId, String number)
            throws SQLException {
        boolean ownTransaction=c.getAutoCommit();
        if (ownTransaction) DatabaseManager.beginTransaction(c);
        Savepoint checkpoint=null;
        try {
            checkpoint=c.setSavepoint();
            reverseSupplierOrderInTransaction(c,orderId,number);
            c.releaseSavepoint(checkpoint);
            checkpoint=null;
            if (ownTransaction) c.commit();
        } catch (SQLException | RuntimeException error) {
            if (checkpoint!=null) {
                c.rollback(checkpoint);
                c.releaseSavepoint(checkpoint);
            }
            if (ownTransaction) c.rollback();
            throw error;
        } finally {
            if (ownTransaction) c.setAutoCommit(true);
        }
    }

    private static void reverseSupplierOrderInTransaction(Connection c, int orderId, String number)
            throws SQLException {
        try(PreparedStatement p=c.prepareStatement("""
                SELECT d.id
                FROM supplier_order_details d
                LEFT JOIN inventory_lots l ON l.supplier_order_detail_id=d.id
                WHERE d.order_id=? AND l.id IS NULL
                LIMIT 1
                """)) {
            p.setInt(1,orderId);
            try(ResultSet r=p.executeQuery()) {
                if(r.next())
                    throw new SQLException("This supplier receipt has no FIFO lot history and cannot be reversed safely");
            }
        }
        try(PreparedStatement p=c.prepareStatement("""
                SELECT 1
                FROM inventory_lot_consumptions x
                JOIN inventory_lots l ON l.id=x.lot_id
                JOIN supplier_order_details d ON d.id=l.supplier_order_detail_id
                WHERE d.order_id=?
                LIMIT 1
                """)) {
            p.setInt(1,orderId);
            try(ResultSet r=p.executeQuery()) {
                if(r.next())
                    throw new SQLException("This supplier receipt cannot be reversed because its FIFO lot has historical consumption records");
            }
        }
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

        // Preflight ALL purchase lines together. A receipt cannot be canceled if
        // any of its physical units are already reserved by customer orders.
        try(PreparedStatement p=c.prepareStatement("""
                SELECT l.product_id, SUM(l.initial_quantity) AS units_to_remove
                FROM inventory_lots l
                JOIN supplier_order_details d ON d.id=l.supplier_order_detail_id
                WHERE d.order_id=? GROUP BY l.product_id
                """)) {
            p.setInt(1,orderId);
            try(ResultSet r=p.executeQuery()) {
                while(r.next()) {
                    long amount=r.getLong("units_to_remove");
                    int available=StockManager.getAvailableStock(c,r.getInt("product_id"));
                    if(amount>available)
                        throw new SQLException(I18n.format(
                            "Insufficient quantity. Current availability: {0}",available));
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
                    try(PreparedStatement u=c.prepareStatement("""
                            UPDATE products SET quantity=quantity-?
                            WHERE id=? AND quantity-reserved_quantity>=?
                            """)) {
                        int quantity=r.getInt("initial_quantity");
                        u.setInt(1,quantity);u.setInt(2,productId);u.setInt(3,quantity);
                        if(u.executeUpdate()!=1)
                            throw new SQLException("Stock availability changed while reversing supplier receipt.");
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
                while(r.next()) {
                    CostIntegrity.requirePurchaseCost(c, java.util.List.of(r.getInt("product_id")));
                    consumeOrderDetail(c,r.getInt("id"),r.getInt("product_id"),
                        Numbers.positiveQuantity(r.getObject("quantity")));
                }
            }
        }
    }

    private static void consumeOrderDetail(Connection c,int detailId,int productId,int quantity)
            throws SQLException {
        int needed=quantity;
        double costTotal=0;
        boolean costKnown=true;
        try(PreparedStatement lots=c.prepareStatement("""
                SELECT id,remaining_quantity,unit_cost,source_type
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
                    if (cost == 0 && "MIGRATION".equals(r.getString("source_type"))) costKnown=false;
                    updateLotRemaining(c,r.getInt("id"),-take);
                    insertConsumption(c,r.getInt("id"),detailId,null,take,cost);
                    costTotal=Numbers.add(costTotal,Numbers.lineTotal(take,cost));
                    needed-=take;
                }
            }
        }
        if(needed>0) {
            // The sale is allowed to drive physical stock negative, but there is no
            // real FIFO layer for these units. Keep a zero-cost marker only so a later
            // rollback is distinguishable from a legacy sale with no FIFO history.
            // Historical cost/profit stays unavailable instead of being invented from
            // the product's current acquisition cost.
            insertConsumption(c,null,detailId,null,needed,0.0);
            costKnown=false;
        }
        try(PreparedStatement u=c.prepareStatement("UPDATE order_details SET cost_total=? WHERE id=?")) {
            if(costKnown) u.setDouble(1,Numbers.money(java.math.BigDecimal.valueOf(costTotal)));
            else u.setNull(1,Types.REAL);
            u.setInt(2,detailId);u.executeUpdate();
        }
    }

    public static void restoreOrder(Connection c,int orderId) throws SQLException {
        // Preflight the entire order before changing any lot. This also makes the
        // method safe for callers that have not opened an explicit transaction.
        try(PreparedStatement check=c.prepareStatement("""
                SELECT d.id
                FROM order_details d
                WHERE d.order_id=?
                  AND (
                    NOT EXISTS (
                        SELECT 1 FROM inventory_lot_consumptions x
                        WHERE x.order_detail_id=d.id
                    )
                    OR COALESCE((
                        SELECT SUM(x.quantity)
                        FROM inventory_lot_consumptions x
                        WHERE x.order_detail_id=d.id
                    ),0)<>d.quantity
                    OR EXISTS (
                        SELECT 1
                        FROM inventory_lot_consumptions x
                        LEFT JOIN inventory_lots l ON l.id=x.lot_id
                        WHERE x.order_detail_id=d.id
                          AND (l.id IS NULL OR l.product_id<>d.product_id)
                    )
                  )
                LIMIT 1
                """)) {
            check.setInt(1,orderId);
            try(ResultSet r=check.executeQuery()) {
                if(r.next())
                    throw new SQLException("This completed sale has incomplete FIFO history and cannot be reopened safely");
            }
        }

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

                    if(restored==0)
                        throw new SQLException("FIFO history is incomplete for this completed sale");

                    try(PreparedStatement u=c.prepareStatement(
                            "UPDATE order_details SET cost_total=NULL WHERE id=?")) {
                        u.setInt(1,detailId);u.executeUpdate();
                    }
                }
            }
        }
    }

    /**
     * Restore the FIFO layers of a completed sale while keeping its original
     * consumption history and frozen cost. Used by a storno/reversal: the old
     * sale remains historical and a new compensating stock event is recorded.
     */
    public static void reverseOrderKeepingHistory(Connection c,int orderId) throws SQLException {
        try(PreparedStatement check=c.prepareStatement("""
                SELECT d.id
                FROM order_details d
                WHERE d.order_id=?
                  AND (
                    NOT EXISTS (
                        SELECT 1 FROM inventory_lot_consumptions x
                        WHERE x.order_detail_id=d.id
                    )
                    OR COALESCE((SELECT SUM(x.quantity) FROM inventory_lot_consumptions x
                        WHERE x.order_detail_id=d.id),0)<>d.quantity
                    OR EXISTS (
                        SELECT 1 FROM inventory_lot_consumptions x
                        LEFT JOIN inventory_lots l ON l.id=x.lot_id
                        WHERE x.order_detail_id=d.id AND (l.id IS NULL OR l.product_id<>d.product_id)
                    )
                  )
                LIMIT 1
                """)) {
            check.setInt(1,orderId);
            try(ResultSet r=check.executeQuery()) {
                if(r.next())
                    throw new SQLException("This completed sale has incomplete FIFO history and cannot be reversed safely");
            }
        }

        try(PreparedStatement details=c.prepareStatement(
                "SELECT id FROM order_details WHERE order_id=? ORDER BY id")) {
            details.setInt(1,orderId);
            try(ResultSet d=details.executeQuery()) {
                while(d.next()) {
                    int detailId=d.getInt(1);
                    try(PreparedStatement p=c.prepareStatement("""
                            SELECT lot_id,quantity
                            FROM inventory_lot_consumptions
                            WHERE order_detail_id=?
                            ORDER BY id
                            """)) {
                        p.setInt(1,detailId);
                        try(ResultSet r=p.executeQuery()) {
                            while(r.next()) {
                                updateLotRemaining(c,r.getInt("lot_id"),r.getInt("quantity"));
                            }
                        }
                    }
                }
            }
        }
    }

    public static void applyManualMovement(Connection c,int movementId,int productId,String type,
                                           int quantity,Date date,double inboundUnitCost)
            throws SQLException {
        quantity=Numbers.positiveQuantity(quantity);
        int oldStock=physicalStock(c,productId);
        if("INWARD".equals(type)) {
            double cost=Numbers.nonNegative(inboundUnitCost);
            int remaining=Math.max(0,Math.min(quantity,Math.addExact(oldStock,quantity)));
            try(PreparedStatement u=c.prepareStatement("UPDATE products SET quantity=quantity+? WHERE id=?")) {
                u.setInt(1,quantity);u.setInt(2,productId);u.executeUpdate();
            }
            insertLot(c,productId,null,null,movementId,date,quantity,remaining,cost,"MANUAL_INWARD");
        } else if("OUTWARD".equals(type)) {
            int available=StockManager.getAvailableStock(c,productId);
            if(quantity>available)
                throw new SQLException(I18n.format(
                    "Insufficient quantity. Current availability: {0}", available));
            consumeForMovement(c,movementId,productId,quantity);
            try(PreparedStatement u=c.prepareStatement("UPDATE products SET quantity=quantity-? WHERE id=?")) {
                u.setInt(1,quantity);u.setInt(2,productId);u.executeUpdate();
            }
        } else throw new SQLException("Unsupported movement type: "+type);
    }

    public static void applyManualMovement(Connection c,int movementId,int productId,String type,
                                           int quantity,Date date) throws SQLException {
        applyManualMovement(c,movementId,productId,type,quantity,date,currentAcquisitionCost(c,productId));
    }

    public static void reverseManualMovement(Connection c,int movementId,int productId,
                                             String type,int quantity) throws SQLException {
        if("INWARD".equals(type)) {
            int available=StockManager.getAvailableStock(c,productId);
            if(quantity>available)
                throw new SQLException(I18n.format(
                    "Insufficient quantity. Current availability: {0}", available));
            try(PreparedStatement p=c.prepareStatement("""
                    SELECT id,initial_quantity,remaining_quantity FROM inventory_lots
                    WHERE warehouse_movement_id=?
                    """)) {
                p.setInt(1,movementId);
                try(ResultSet r=p.executeQuery()) {
                    if(!r.next())
                        throw new SQLException("This inward movement has no FIFO lot history and cannot be reversed safely");
                    if(r.getInt("remaining_quantity")!=r.getInt("initial_quantity"))
                        throw new SQLException("This inward movement cannot be changed because part of its FIFO lot has already been consumed");
                    try (PreparedStatement history=c.prepareStatement(
                            "SELECT 1 FROM inventory_lot_consumptions WHERE lot_id=? LIMIT 1")) {
                        history.setInt(1,r.getInt("id"));
                        try (ResultSet used=history.executeQuery()) {
                            if (used.next()) throw new SQLException(
                                "This inward movement has historical FIFO consumption records and cannot be changed.");
                        }
                    }
                    try(PreparedStatement d=c.prepareStatement("DELETE FROM inventory_lots WHERE id=?")) {
                        d.setInt(1,r.getInt("id"));d.executeUpdate();
                    }
                }
            }
            try(PreparedStatement u=c.prepareStatement("UPDATE products SET quantity=quantity-? WHERE id=?")) {
                u.setInt(1,quantity);u.setInt(2,productId);u.executeUpdate();
            }
        } else if("OUTWARD".equals(type)) {
            throw new SQLException("Posted outward movements cannot be edited or deleted. Use Reverse Movement.");
        } else throw new SQLException("Unsupported movement type: "+type);
    }

    /** Append a compensating entry; the original movement and consumption snapshots remain intact. */
    public static void reverseOutwardMovement(Connection c, int movementId) throws SQLException {
        boolean ownTransaction=c.getAutoCommit();
        if (ownTransaction) DatabaseManager.beginTransaction(c);
        Savepoint checkpoint=c.setSavepoint();
        try {
            int productId,quantity;
            try (PreparedStatement p=c.prepareStatement("""
                    SELECT m.product_id,m.quantity,m.type,m.document_type,
                           EXISTS(SELECT 1 FROM warehouse_movements r WHERE r.reversal_of_movement_id=m.id) AS reversed
                    FROM warehouse_movements m WHERE m.id=?
                    """)) {
                p.setInt(1,movementId);
                try (ResultSet r=p.executeQuery()) {
                    if (!r.next()) throw new SQLException("Movement no longer exists");
                    String document=r.getString("document_type");
                    if (!"OUTWARD".equals(r.getString("type")) || "ORDER".equals(document)
                            || "SUPPLIER_ORDER".equals(document) || "SUPPLIER_RETURN".equals(document)
                            || "SUPPLIER_RETURN_REVERSAL".equals(document) || "ORDER_REVERSAL".equals(document)
                            || "MOVEMENT_REVERSAL".equals(document))
                        throw new SQLException("Only manual outward movements or stock reductions can be reversed here.");
                    if (r.getBoolean("reversed")) throw new SQLException("This movement has already been reversed.");
                    productId=r.getInt("product_id"); quantity=r.getInt("quantity");
                }
            }
            try (PreparedStatement p=c.prepareStatement("""
                    SELECT SUM(x.quantity) AS quantity,
                           SUM(CASE WHEN l.id IS NULL OR l.product_id<>? THEN 1 ELSE 0 END) AS invalid
                    FROM inventory_lot_consumptions x LEFT JOIN inventory_lots l ON l.id=x.lot_id
                    WHERE x.warehouse_movement_id=?
                    """)) {
                p.setInt(1,productId);p.setInt(2,movementId);
                try (ResultSet r=p.executeQuery()) {
                    if (!r.next() || r.getLong("quantity")!=quantity || r.getInt("invalid")!=0)
                        throw new SQLException("This outward movement has incomplete FIFO history and cannot be reversed safely.");
                }
            }
            try (PreparedStatement p=c.prepareStatement(
                    "SELECT lot_id,quantity FROM inventory_lot_consumptions WHERE warehouse_movement_id=?")) {
                p.setInt(1,movementId);
                try (ResultSet r=p.executeQuery()) {
                    while(r.next()) updateLotRemaining(c,r.getInt(1),r.getInt(2));
                }
            }
            try (PreparedStatement p=c.prepareStatement("UPDATE products SET quantity=quantity+? WHERE id=?")) {
                p.setInt(1,quantity);p.setInt(2,productId);p.executeUpdate();
            }
            int reversalId=insertMovement(c,productId,new Date(),"INWARD",quantity,
                "MOVEMENT_REVERSAL",String.valueOf(movementId),"MOVEMENT_REVERSAL",null);
            try (PreparedStatement p=c.prepareStatement(
                    "UPDATE warehouse_movements SET reversal_of_movement_id=? WHERE id=?")) {
                p.setInt(1,movementId);p.setInt(2,reversalId);p.executeUpdate();
            }
            c.releaseSavepoint(checkpoint);
            if (ownTransaction) c.commit();
        } catch (SQLException | RuntimeException e) {
            c.rollback(checkpoint);
            c.releaseSavepoint(checkpoint);
            if (ownTransaction) c.rollback();
            throw e;
        } finally {
            if (ownTransaction) c.setAutoCommit(true);
        }
    }

    public static void adjustProductStock(Connection c,int movementId,int productId,int delta,
                                          Date date,double cost) throws SQLException {
        if(delta==0)return;
        int oldStock=physicalStock(c,productId);
        if(delta>0) {
            if (Numbers.nonNegative(cost) <= 0) throw new SQLException(I18n.tr("Purchase cost must be greater than zero."));
            int remaining=Math.max(0,Math.min(delta,Math.addExact(oldStock,delta)));
            try(PreparedStatement u=c.prepareStatement("UPDATE products SET quantity=quantity+? WHERE id=?")) {
                u.setInt(1,delta);u.setInt(2,productId);u.executeUpdate();
            }
            insertLot(c,productId,null,null,movementId,date,delta,remaining,
                Numbers.nonNegative(cost),"INVENTORY_ADJUSTMENT");
        } else {
            int qty=Math.negateExact(delta);
            int available=StockManager.getAvailableStock(c,productId);
            if(qty>available)
                throw new SQLException(I18n.format(
                    "Insufficient quantity. Current availability: {0}", available));
            consumeForMovement(c,movementId,productId,qty);
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
        if(needed>0) insertConsumption(c,null,null,movementId,needed,0.0);
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

    static void refreshLastPurchaseCost(Connection c,int productId,int excludedOrderId)
            throws SQLException {
        Double cost=null;
        try(PreparedStatement p=c.prepareStatement("""
                SELECT d.unit_price
                FROM supplier_order_details d
                JOIN supplier_orders o ON o.id=d.order_id
                WHERE d.product_id=? AND o.status='Completed' AND o.id<>?
                  AND COALESCE((SELECT SUM(ri.quantity) FROM supplier_return_items ri
                      JOIN supplier_returns sr ON sr.id=ri.return_id
                      WHERE ri.supplier_order_detail_id=d.id AND sr.reversed_at IS NULL),0)<d.quantity
                ORDER BY o.received_date DESC,o.id DESC,d.id DESC LIMIT 1
                """)) {
            p.setInt(1,productId);p.setInt(2,excludedOrderId);
            try(ResultSet r=p.executeQuery()){if(r.next())cost=Numbers.nonNegative(r.getObject(1));}
        }
        if(cost==null) {
            // Fall back to the newest available FIFO lot, including manual receipts.
            // Exclude returned or otherwise non-completed supplier receipts.
            try(PreparedStatement p=c.prepareStatement("""
                    SELECT l.unit_cost FROM inventory_lots l
                    LEFT JOIN supplier_order_details d ON d.id=l.supplier_order_detail_id
                    LEFT JOIN supplier_orders o ON o.id=d.order_id
                    WHERE l.product_id=? AND l.remaining_quantity>0
                      AND (l.supplier_order_detail_id IS NULL
                           OR (o.status='Completed' AND o.id<>?))
                    ORDER BY l.received_date DESC,l.id DESC LIMIT 1
                    """)) {
                p.setInt(1,productId);p.setInt(2,excludedOrderId);try(ResultSet r=p.executeQuery()){
                    if(r.next())cost=Numbers.nonNegative(r.getObject(1));
                }
            }
        }
        if(cost==null) cost=0.0;
        try(PreparedStatement p=c.prepareStatement(
                "UPDATE products SET acquisition_cost=? WHERE id=?")) {
            p.setDouble(1,cost);p.setInt(2,productId);p.executeUpdate();
        }
    }

    private static int insertMovement(Connection c,int productId,Date date,String type,int quantity,
                                       String reason,String number,String documentType,String notes)
            throws SQLException {
        try(PreparedStatement p=c.prepareStatement("""
                INSERT INTO warehouse_movements
                (product_id,date,type,quantity,reason,document_number,document_type,notes)
                VALUES(?,?,?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            p.setInt(1,productId);p.setTimestamp(2,DateUtils.toSqlTimestamp(date));p.setString(3,type);
            p.setInt(4,quantity);p.setString(5,reason);p.setString(6,number);
            p.setString(7,documentType);p.setString(8,notes);p.executeUpdate();
            try (ResultSet keys=p.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Unable to create warehouse movement");
                return keys.getInt(1);
            }
        }
    }
}

