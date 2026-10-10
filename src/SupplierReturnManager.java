import java.sql.*;
import java.util.*;
import java.math.BigDecimal;

/** Supplier returns are immutable purchase-linked documents with compensating FIFO movements. */
public final class SupplierReturnManager {
    private SupplierReturnManager() {}

    public record Request(int supplierOrderDetailId, int quantity) {}
    public record Line(int detailId, int productId, String code, String product,
                       int purchased, int remaining, int returnable, double purchasePrice,
                       int lotId, double fifoCost) {}
    public record History(int id, Date date, int quantity, double value,
                          boolean reversed, String notes) {}

    /** Migration 021, run after the FIFO and warehouse movement schemas exist. */
    public static void installSchema(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS supplier_returns (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id INTEGER NOT NULL REFERENCES supplier_orders(id),
                    returned_at DATETIME NOT NULL,
                    reversed_at DATETIME,
                    notes TEXT
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS supplier_return_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    return_id INTEGER NOT NULL REFERENCES supplier_returns(id),
                    supplier_order_detail_id INTEGER NOT NULL REFERENCES supplier_order_details(id),
                    lot_id INTEGER NOT NULL REFERENCES inventory_lots(id),
                    product_id INTEGER NOT NULL REFERENCES products(id),
                    quantity INTEGER NOT NULL CHECK(quantity > 0),
                    unit_cost REAL NOT NULL CHECK(unit_cost >= 0),
                    unit_price REAL NOT NULL CHECK(unit_price >= 0),
                    warehouse_movement_id INTEGER NOT NULL UNIQUE REFERENCES warehouse_movements(id)
                )
                """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_supplier_returns_order ON supplier_returns(order_id)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_supplier_return_items_detail ON supplier_return_items(supplier_order_detail_id)");
        }
    }

    /** Each detail corresponds to an original receipt lot. Show physical unreserved availability. */
    public static List<Line> lines(Connection c, int orderId) throws SQLException {
        List<Line> result = new ArrayList<>();
        String sql = """
            SELECT d.id, d.product_id, p.code, p.name, d.quantity, d.unit_price,
                   l.id AS lot_id, l.initial_quantity, l.remaining_quantity, l.unit_cost
            FROM supplier_order_details d
            JOIN products p ON p.id=d.product_id
            LEFT JOIN inventory_lots l ON l.supplier_order_detail_id=d.id
                                         AND l.source_type='SUPPLIER_ORDER'
            WHERE d.order_id=?
            ORDER BY d.id, l.id
            """;
        Set<Integer> seen = new HashSet<>();
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    int detailId = r.getInt("id");
                    if (!seen.add(detailId))
                        throw new SQLException("Supplier receipt has multiple FIFO lots for a single line.");
                    int productId = r.getInt("product_id");
                    int purchased = r.getInt("quantity");
                    int lotId = r.getInt("lot_id");
                    int remaining = Math.max(0, r.getInt("remaining_quantity"));
                    boolean complete = lotId>0 && r.getInt("initial_quantity")==purchased;
                    int available = Math.max(0, StockManager.getAvailableStock(c, productId));
                    result.add(new Line(detailId, productId, r.getString("code"),
                        r.getString("name"), purchased, remaining,
                        complete ? Math.min(remaining,available) : 0,
                        r.getDouble("unit_price"), lotId, r.getDouble("unit_cost")));
                }
            }
        }
        return result;
    }

    public static List<History> history(Connection c,int orderId) throws SQLException {
        List<History> result = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("""
            SELECT sr.id, sr.returned_at, sr.reversed_at, sr.notes,
                   COALESCE(SUM(ri.quantity),0) AS units,
                   COALESCE(SUM(ri.quantity * ri.unit_price),0) AS value
            FROM supplier_returns sr
            LEFT JOIN supplier_return_items ri ON ri.return_id=sr.id
            WHERE sr.order_id=?
            GROUP BY sr.id ORDER BY sr.id DESC
            """)) {
            p.setInt(1,orderId);
            try (ResultSet r=p.executeQuery()) {
                while (r.next()) {
                    Date date = DateUtils.parseDate(r,"returned_at");
                    result.add(new History(r.getInt("id"),date,r.getInt("units"),
                        r.getDouble("value"),r.getObject("reversed_at")!=null,r.getString("notes")));
                }
            }
        }
        return result;
    }

    /** 0 = none, 1 = partial, 2 = all original purchased units returned. */
    public static int returnState(Connection c,int orderId) throws SQLException {
        try (PreparedStatement p=c.prepareStatement("""
            SELECT COALESCE((SELECT SUM(quantity) FROM supplier_order_details WHERE order_id=?),0),
                   COALESCE((SELECT SUM(ri.quantity)
                     FROM supplier_return_items ri
                     JOIN supplier_returns sr ON sr.id=ri.return_id
                     WHERE sr.order_id=? AND sr.reversed_at IS NULL),0)
            """)) {
            p.setInt(1,orderId);p.setInt(2,orderId);
            try (ResultSet r=p.executeQuery()) {
                if (!r.next()) return 0;
                long bought=r.getLong(1),returned=r.getLong(2);
                return returned<=0 ? 0 : bought>0 && returned>=bought ? 2 : 1;
            }
        }
    }

    /** Validates all lines before any write; every write shares the same database transaction. */
    public static int create(int orderId,List<Request> requests,String notes) throws SQLException {
        if (requests==null || requests.isEmpty()) throw new SQLException("Select a quantity to return.");
        Connection c=DatabaseManager.getInstance().getConnection();
        DatabaseManager.beginTransaction(c);
        try {
            String number;
            try (PreparedStatement p=c.prepareStatement("SELECT number,status FROM supplier_orders WHERE id=?")) {
                p.setInt(1,orderId);
                try (ResultSet r=p.executeQuery()) {
                    if (!r.next() || !"Completed".equals(r.getString("status")))
                        throw new SQLException("Only completed supplier orders can have a new return.");
                    number=r.getString("number");
                }
            }
            Map<Integer,Line> byDetail=new HashMap<>();
            for (Line line:lines(c,orderId)) byDetail.put(line.detailId(),line);
            Map<Integer,Long> byProduct=new HashMap<>();
            Set<Integer> seen=new HashSet<>();
            for (Request q:requests) {
                if (q==null || q.quantity()<=0 || !seen.add(q.supplierOrderDetailId()))
                    throw new SQLException("Return lines must have distinct products and positive quantities.");
                Line line=byDetail.get(q.supplierOrderDetailId());
                if (line==null || line.lotId()==0 || q.quantity()>line.remaining()
                        || q.quantity()>line.returnable())
                    throw new SQLException("Supplier return quantity exceeds the original FIFO lot availability.");
                byProduct.merge(line.productId(),(long)q.quantity(),Long::sum);
            }
            for (var entry:byProduct.entrySet()) {
                if (entry.getValue()>Math.max(0,StockManager.getAvailableStock(c,entry.getKey())))
                    throw new SQLException("Not enough unreserved stock for this supplier return.");
            }
            int returnId;
            try (PreparedStatement p=c.prepareStatement(
                    "INSERT INTO supplier_returns(order_id,returned_at,notes) VALUES(?,CURRENT_TIMESTAMP,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setInt(1,orderId);p.setString(2,notes==null?"":notes.trim());
                p.executeUpdate();
                try (ResultSet r=p.getGeneratedKeys()) {
                    if (!r.next()) throw new SQLException("Unable to create supplier return.");
                    returnId=r.getInt(1);
                }
            }
            String reference=number+"/R"+returnId;
            for (Request q:requests) {
                Line line=byDetail.get(q.supplierOrderDetailId());
                int movementId;
                try (PreparedStatement p=c.prepareStatement("""
                    INSERT INTO warehouse_movements
                      (product_id,date,type,quantity,reason,document_number,document_type,notes)
                    VALUES (?,CURRENT_TIMESTAMP,'OUTWARD',?,'SUPPLIER_RETURN',?,'SUPPLIER_RETURN',?)
                    """,Statement.RETURN_GENERATED_KEYS)) {
                    p.setInt(1,line.productId());p.setInt(2,q.quantity());
                    p.setString(3,reference);p.setString(4,"Supplier return "+returnId);
                    p.executeUpdate();
                    try (ResultSet r=p.getGeneratedKeys()) {
                        if (!r.next()) throw new SQLException("Unable to create return movement.");
                        movementId=r.getInt(1);
                    }
                }
                try (PreparedStatement p=c.prepareStatement("""
                    UPDATE inventory_lots SET remaining_quantity=remaining_quantity-?
                    WHERE id=? AND remaining_quantity>=?
                    """)) {
                    p.setInt(1,q.quantity());p.setInt(2,line.lotId());p.setInt(3,q.quantity());
                    if (p.executeUpdate()!=1) throw new SQLException("Original FIFO lot has changed.");
                }
                try (PreparedStatement p=c.prepareStatement("""
                    UPDATE products SET quantity=quantity-?
                    WHERE id=? AND quantity-reserved_quantity>=?
                    """)) {
                    p.setInt(1,q.quantity());p.setInt(2,line.productId());p.setInt(3,q.quantity());
                    if (p.executeUpdate()!=1) throw new SQLException("Stock availability has changed.");
                }
                try (PreparedStatement p=c.prepareStatement("""
                    INSERT INTO inventory_lot_consumptions
                      (lot_id,warehouse_movement_id,quantity,unit_cost) VALUES(?,?,?,?)
                    """)) {
                    p.setInt(1,line.lotId());p.setInt(2,movementId);
                    p.setInt(3,q.quantity());p.setDouble(4,line.fifoCost());p.executeUpdate();
                }
                try (PreparedStatement p=c.prepareStatement("""
                    INSERT INTO supplier_return_items
                      (return_id,supplier_order_detail_id,lot_id,product_id,quantity,
                       unit_cost,unit_price,warehouse_movement_id)
                    VALUES(?,?,?,?,?,?,?,?)
                    """)) {
                    p.setInt(1,returnId);p.setInt(2,line.detailId());
                    p.setInt(3,line.lotId());p.setInt(4,line.productId());
                    p.setInt(5,q.quantity());p.setDouble(6,line.fifoCost());
                    p.setDouble(7,line.purchasePrice());p.setInt(8,movementId);p.executeUpdate();
                }
            }
            for (int id:byProduct.keySet()) InventoryLotManager.refreshLastPurchaseCost(c,id,0);
            c.commit();
            return returnId;
        } catch (SQLException | RuntimeException e) {
            c.rollback(); throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /** Full-document storno, preserving original FIFO consumption and a linked inward movement. */
    public static void reverse(int returnId) throws SQLException {
        Connection c=DatabaseManager.getInstance().getConnection();
        DatabaseManager.beginTransaction(c);
        try {
            int orderId;
            try (PreparedStatement p=c.prepareStatement(
                    "SELECT order_id,reversed_at FROM supplier_returns WHERE id=?")) {
                p.setInt(1,returnId);
                try (ResultSet r=p.executeQuery()) {
                    if (!r.next() || r.getObject("reversed_at")!=null)
                        throw new SQLException("This supplier return was already reversed or does not exist.");
                    orderId=r.getInt("order_id");
                }
            }
            Set<Integer> products=new HashSet<>();
            int count=0;
            try (PreparedStatement p=c.prepareStatement("""
                SELECT ri.lot_id,ri.product_id,ri.quantity,ri.warehouse_movement_id,
                       m.type,m.document_type,
                       (SELECT COALESCE(SUM(x.quantity),0)
                        FROM inventory_lot_consumptions x
                        WHERE x.warehouse_movement_id=ri.warehouse_movement_id
                              AND x.lot_id=ri.lot_id) AS consumed
                FROM supplier_return_items ri
                JOIN warehouse_movements m ON m.id=ri.warehouse_movement_id
                WHERE ri.return_id=? ORDER BY ri.id
                """)) {
                p.setInt(1,returnId);
                try (ResultSet r=p.executeQuery()) {
                    while (r.next()) {
                        count++;
                        int lotId=r.getInt("lot_id"),productId=r.getInt("product_id");
                        int quantity=r.getInt("quantity"),originalMovement=r.getInt("warehouse_movement_id");
                        if (!"OUTWARD".equals(r.getString("type"))
                                || !"SUPPLIER_RETURN".equals(r.getString("document_type"))
                                || r.getInt("consumed")!=quantity)
                            throw new SQLException("Supplier return FIFO history is incomplete.");
                        try (PreparedStatement u=c.prepareStatement("""
                            UPDATE inventory_lots SET remaining_quantity=remaining_quantity+?
                            WHERE id=? AND product_id=? AND remaining_quantity<=initial_quantity-?
                            """)) {
                            u.setInt(1,quantity);u.setInt(2,lotId);
                            u.setInt(3,productId);u.setInt(4,quantity);
                            if (u.executeUpdate()!=1) throw new SQLException("Cannot restore the original FIFO lot.");
                        }
                        try (PreparedStatement u=c.prepareStatement("""
                            UPDATE products SET quantity=quantity+?
                            WHERE id=? AND quantity<=2147483647-?
                            """)) {
                            u.setInt(1,quantity);u.setInt(2,productId);u.setInt(3,quantity);
                            if (u.executeUpdate()!=1) throw new SQLException("Cannot restore product stock.");
                        }
                        try (PreparedStatement u=c.prepareStatement("""
                            INSERT INTO warehouse_movements
                              (product_id,date,type,quantity,reason,document_number,document_type,
                               notes,reversal_of_movement_id)
                            VALUES (?,CURRENT_TIMESTAMP,'INWARD',?,'SUPPLIER_RETURN_REVERSAL',?,
                                    'SUPPLIER_RETURN_REVERSAL',?,?)
                            """)) {
                            u.setInt(1,productId);u.setInt(2,quantity);
                            u.setString(3,"R"+returnId);
                            u.setString(4,"Reversal of supplier return "+returnId);
                            u.setInt(5,originalMovement);u.executeUpdate();
                        }
                        products.add(productId);
                    }
                }
            }
            if (count==0) throw new SQLException("Supplier return has no lines.");
            try (PreparedStatement p=c.prepareStatement("""
                UPDATE supplier_returns SET reversed_at=CURRENT_TIMESTAMP
                WHERE id=? AND reversed_at IS NULL
                """)) {
                p.setInt(1,returnId);
                if (p.executeUpdate()!=1) throw new SQLException("Supplier return was already reversed.");
            }
            for (int id:products) InventoryLotManager.refreshLastPurchaseCost(c,id,0);
            c.commit();
        } catch(SQLException | RuntimeException e) {
            c.rollback();throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }
}
