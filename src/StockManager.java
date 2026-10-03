import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * Centralized stock management class
 * Handles stock availability checks, reservations, and movements
 */
public class StockManager {

    public static String displayWarehouseText(String value) {
        if (value == null || value.isBlank()) return "";
        String translated = I18n.tr(value);
        if (!translated.equals(value)) return translated;
        if (value.matches("[A-Z0-9_]+")) {
            String human = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            return Character.toUpperCase(human.charAt(0)) + human.substring(1);
        }
        return value;
    }


    /**
     * Check if there is sufficient stock for products in an order/invoice
     *
     * @param items List of items to check (product ID, quantity pairs)
     * @param existingDocumentId If editing, ID of existing document to exclude from check
     * @param documentType Type of document ("ORDER" or "INVOICE")
     * @return Map of product names to insufficient quantities, empty if all OK
     */
    public static Map<String, StockAvailability> checkStockAvailability(
            Connection conn,
            List<StockItem> items,
            Integer existingDocumentId,
            String documentType) throws SQLException {

        Map<String, StockAvailability> insufficientProducts = new HashMap<>();

        for (StockItem item : items) {
            int productId = item.getProductId();
            String productName = item.getProductName();
            int requestedQty = item.getQuantity();

            // Get current stock and reserved quantity from database
            String query = """
                SELECT code, quantity, reserved_quantity
                FROM products
                WHERE id = ?
            """;

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, productId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    int currentStock = rs.getInt("quantity");
                    int reservedStock = rs.getInt("reserved_quantity");
                    int availableStock = Math.subtractExact(currentStock, reservedStock);

                    // If editing existing document, add back the old quantity for this product
                    if (existingDocumentId != null) {
                        int oldQty = getStockEffectQuantity(conn, existingDocumentId, productId, documentType);
                        availableStock = Math.addExact(availableStock, oldQty);
                    }

                    if (requestedQty > availableStock) {
                        insufficientProducts.put(productName + " [" + rs.getString("code") + "]", new StockAvailability(
                            currentStock, reservedStock, availableStock, requestedQty
                        ));
                    }
                }
            }
        }

        return insufficientProducts;
    }

    private static int getStockEffectQuantity(Connection conn, int documentId, int productId,
                                              String documentType) throws SQLException {
        if (!"ORDER".equals(documentType)) return 0;
        try (PreparedStatement p = conn.prepareStatement("SELECT status FROM orders WHERE id = ?")) {
            p.setInt(1, documentId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Order no longer exists");
                if ("Completed".equals(r.getString(1)))
                    return getOldProductQuantity(conn, documentId, productId, documentType);
            }
        }
        try (PreparedStatement p = conn.prepareStatement("SELECT COALESCE(SUM(reserved_quantity),0) " +
                "FROM stock_reservations WHERE document_type='ORDER' AND document_id=? AND product_id=? AND status='ACTIVE'")) {
            p.setInt(1, documentId); p.setInt(2, productId);
            try (ResultSet r = p.executeQuery()) { return r.next() ? Math.toIntExact(r.getLong(1)) : 0; }
        }
    }

    /**
     * Get the old quantity of a product in an existing document
     */
    private static int getOldProductQuantity(Connection conn, int documentId, int productId, String documentType) throws SQLException {
        String tableName = documentType.equals("ORDER") ? "order_details" : "invoice_details";
        String columnName = documentType.equals("ORDER") ? "order_id" : "invoice_id";

        String query = String.format(
            "SELECT COALESCE(SUM(quantity),0) AS quantity FROM %s WHERE %s = ? AND product_id = ?",
            tableName, columnName
        );

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, documentId);
            pstmt.setInt(2, productId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Math.toIntExact(rs.getLong("quantity"));
            }
        }
        return 0;
    }

    /**
     * Create or update a stock reservation
     */
    public static void createOrUpdateReservation(
            Connection conn,
            int productId,
            String documentType,
            int documentId,
            int quantity,
            String note) throws SQLException {

        // Check if reservation already exists
        String checkQuery = """
            SELECT id, reserved_quantity, status
            FROM stock_reservations
            WHERE product_id = ? AND document_type = ? AND document_id = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(checkQuery)) {
            pstmt.setInt(1, productId);
            pstmt.setString(2, documentType);
            pstmt.setInt(3, documentId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Update existing reservation
                int reservationId = rs.getInt("id");
                String updateQuery = """
                    UPDATE stock_reservations
                    SET reserved_quantity = ?, status = 'ACTIVE', notes = ?
                    WHERE id = ?
                """;

                try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                    updateStmt.setInt(1, quantity);
                    updateStmt.setString(2, note);
                    updateStmt.setInt(3, reservationId);
                    updateStmt.executeUpdate();
                }
            } else {
                // Create new reservation
                String insertQuery = """
                    INSERT INTO stock_reservations
                    (product_id, document_type, document_id, reserved_quantity, status, notes)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?)
                """;

                try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                    insertStmt.setInt(1, productId);
                    insertStmt.setString(2, documentType);
                    insertStmt.setInt(3, documentId);
                    insertStmt.setInt(4, quantity);
                    insertStmt.setString(5, note);
                    insertStmt.executeUpdate();
                }
            }
        }
    }

    /**
     * Cancel a stock reservation (mark as CANCELLED)
     */
    public static void cancelReservation(
            Connection conn,
            String documentType,
            int documentId) throws SQLException {

        String updateQuery = """
            UPDATE stock_reservations
            SET status = 'CANCELLED'
            WHERE document_type = ? AND document_id = ? AND status = 'ACTIVE'
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
            pstmt.setString(1, documentType);
            pstmt.setInt(2, documentId);
            pstmt.executeUpdate();
        }
    }

    private static void requireAvailableStockForCompletion(
            Connection conn, String documentType, int documentId) throws SQLException {
        String query = """
            SELECT p.id, p.code, p.name, p.quantity,
                   SUM(r.reserved_quantity) AS requested_quantity,
                   COALESCE((
                       SELECT SUM(other.reserved_quantity)
                       FROM stock_reservations other
                       WHERE other.product_id = p.id
                         AND other.status = 'ACTIVE'
                         AND NOT (other.document_type = ? AND other.document_id = ?)
                   ), 0) AS other_reserved
            FROM stock_reservations r
            JOIN products p ON p.id = r.product_id
            WHERE r.document_type = ? AND r.document_id = ? AND r.status = 'ACTIVE'
            GROUP BY p.id, p.code, p.name, p.quantity
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, documentType);
            pstmt.setInt(2, documentId);
            pstmt.setString(3, documentType);
            pstmt.setInt(4, documentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long requested = rs.getLong("requested_quantity");
                    long available = (long)rs.getInt("quantity") - rs.getLong("other_reserved");
                    if (requested > available) {
                        throw new SQLException(
                            I18n.tr("Insufficient Stock") + ": "
                            + rs.getString("name") + " [" + rs.getString("code") + "]. "
                            + I18n.format("Insufficient quantity. Current availability: {0}", available)
                        );
                    }
                }
            }
        }
    }

    /**
     * Complete a stock reservation and decrement actual stock
     * This converts reserved stock into actual stock decrease
     */
    public static void completeReservationAndDecrementStock(
            Connection conn,
            String documentType,
            int documentId,
            Date documentDate,
            String documentNumber) throws SQLException {

        // Never post FIFO consumption or warehouse movements when the document
        // would consume stock already unavailable because of physical stock or
        // other active reservations.
        requireAvailableStockForCompletion(conn, documentType, documentId);

        // Freeze the economic cost of a completed customer sale before changing
        // physical stock. A line may consume more than one FIFO lot.
        if ("ORDER".equals(documentType)) {
            InventoryLotManager.consumeOrder(conn, documentId);
        }

        // Get all active reservations for this document
        String getReservationsQuery = """
            SELECT product_id, reserved_quantity
            FROM stock_reservations
            WHERE document_type = ? AND document_id = ? AND status = 'ACTIVE'
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(getReservationsQuery)) {
            pstmt.setString(1, documentType);
            pstmt.setInt(2, documentId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                int productId = rs.getInt("product_id");
                int quantity = rs.getInt("reserved_quantity");

                // Decrement actual stock
                decrementStock(conn, productId, quantity);

                // Create warehouse movement
                createWarehouseMovement(conn, productId, documentDate, quantity,
                    "SALE", documentNumber, documentType,
                    documentType + " " + documentNumber);
            }
        }

        // Mark reservations as COMPLETED
        String updateQuery = """
            UPDATE stock_reservations
            SET status = 'COMPLETED'
            WHERE document_type = ? AND document_id = ? AND status = 'ACTIVE'
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
            pstmt.setString(1, documentType);
            pstmt.setInt(2, documentId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Restore stock from a previous document (when editing/deleting)
     */
    public static void restoreStockFromDocument(
            Connection conn,
            int documentId,
            String documentType) throws SQLException {

        String tableName = documentType.equals("ORDER") ? "order_details" : "invoice_details";
        String columnName = documentType.equals("ORDER") ? "order_id" : "invoice_id";

        String query = String.format(
            "SELECT product_id, quantity FROM %s WHERE %s = ?",
            tableName, columnName
        );

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, documentId);
            ResultSet rs = pstmt.executeQuery();

            String updateQuery = "UPDATE products SET quantity = quantity + ? WHERE id = ?";
            try (PreparedStatement updatePstmt = conn.prepareStatement(updateQuery)) {
                while (rs.next()) {
                    int productId = rs.getInt("product_id");
                    int quantity = rs.getInt("quantity");

                    updatePstmt.setInt(1, quantity);
                    updatePstmt.setInt(2, productId);
                    updatePstmt.executeUpdate();
                }
            }
        }
    }

    /**
     * Helper: Decrement stock quantity
     */
    private static void decrementStock(Connection conn, int productId, int quantity) throws SQLException {
        String updateQuery = "UPDATE products SET quantity = quantity - ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
            pstmt.setInt(1, quantity);
            pstmt.setInt(2, productId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Helper: Create warehouse movement record
     */
    private static void createWarehouseMovement(
            Connection conn,
            int productId,
            Date documentDate,
            int quantity,
            String reason,
            String documentNumber,
            String documentType,
            String note) throws SQLException {

        String movementQuery = """
            INSERT INTO warehouse_movements
            (product_id, date, type, quantity, reason, document_number, document_type, notes)
            VALUES (?, ?, 'OUTWARD', ?, ?, ?, ?, ?)
        """;

        // INWARD for purchases, OUTWARD for sales
        String movementType = reason.equals("PURCHASE") ? "INWARD" : "OUTWARD";

        movementQuery = """
            INSERT INTO warehouse_movements
            (product_id, date, type, quantity, reason, document_number, document_type, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(movementQuery)) {
            pstmt.setInt(1, productId);
            pstmt.setTimestamp(2, DateUtils.toSqlTimestamp(documentDate));
            pstmt.setString(3, movementType);
            pstmt.setInt(4, quantity);
            pstmt.setString(5, reason);
            pstmt.setString(6, documentNumber);
            pstmt.setString(7, documentType);
            pstmt.setString(8, note);
            pstmt.executeUpdate();
        }
    }

    /**
     * Get available stock for a product (physical - reserved)
     */
    public static int getAvailableStock(Connection conn, int productId) throws SQLException {
        String query = """
            SELECT quantity, reserved_quantity
            FROM products
            WHERE id = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int stock = rs.getInt("quantity");
                int reserved = rs.getInt("reserved_quantity");
                return Math.subtractExact(stock, reserved);
            }
        }
        return 0;
    }

    /**
     * Reverse a completed customer sale without deleting or rewriting its history.
     * Original FIFO consumptions and cost_total remain frozen; stock is returned
     * to the exact original layers and a compensating inward movement is posted.
     */
    public static void reverseCompletedOrder(Connection conn,int orderId) throws SQLException {
        boolean ownTransaction=conn.getAutoCommit();
        if (ownTransaction) DatabaseManager.beginTransaction(conn);
        Savepoint checkpoint=conn.setSavepoint();
        try {
            reverseCompletedOrderInTransaction(conn,orderId);
            conn.releaseSavepoint(checkpoint);
            if (ownTransaction) conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback(checkpoint);
            conn.releaseSavepoint(checkpoint);
            if (ownTransaction) conn.rollback();
            throw e;
        } finally {
            if (ownTransaction) conn.setAutoCommit(true);
        }
    }

    private static void reverseCompletedOrderInTransaction(Connection conn,int orderId) throws SQLException {
        String status;
        try(PreparedStatement p=conn.prepareStatement("SELECT status FROM orders WHERE id=?")) {
            p.setInt(1,orderId);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw new SQLException("Order no longer exists");
                status=r.getString(1);
            }
        }
        if("Reversed".equals(status))
            throw new SQLException("This completed sale has already been reversed");
        if(!"Completed".equals(status))
            throw new SQLException("Only completed customer orders can be reversed");

        InventoryLotManager.reverseOrderKeepingHistory(conn,orderId);
        restoreStockFromDocument(conn,orderId,"ORDER");

        try(PreparedStatement p=conn.prepareStatement("""
                SELECT product_id,SUM(quantity) AS quantity
                FROM order_details
                WHERE order_id=?
                GROUP BY product_id
                """)) {
            p.setInt(1,orderId);
            try(ResultSet r=p.executeQuery()) {
                while(r.next()) {
                    try(PreparedStatement m=conn.prepareStatement("""
                            INSERT INTO warehouse_movements
                            (product_id,date,type,quantity,reason,document_number,document_type,notes)
                            VALUES(?,CURRENT_TIMESTAMP,'INWARD',?,'SALE_REVERSAL',?,'ORDER_REVERSAL',?)
                            """)) {
                        m.setInt(1,r.getInt("product_id"));
                        m.setInt(2,r.getInt("quantity"));
                        m.setString(3,String.valueOf(orderId));
                        m.setString(4,"Reversal of customer order #"+orderId);
                        m.executeUpdate();
                    }
                }
            }
        }

        try(PreparedStatement p=conn.prepareStatement(
                "UPDATE orders SET status='Reversed' WHERE id=?")) {
            p.setInt(1,orderId);
            if(p.executeUpdate()!=1) throw new SQLException("Order no longer exists");
        }
    }

    /**
     * Delete an order and restore stock based on its status
     * Handles: restoring stock if Completed, cancelling reservations if New or In Progress
     */
    public static void deleteOrder(Connection conn, int orderId, String orderStatus) throws SQLException {
        try (PreparedStatement p = conn.prepareStatement("SELECT status FROM orders WHERE id=?")) {
            p.setInt(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Order no longer exists");
                orderStatus = r.getString(1);
            }
        }
        if ("Completed".equals(orderStatus) || "Reversed".equals(orderStatus)) {
            throw new SQLException(
                "Completed or reversed customer sales cannot be deleted. Reverse completed sales instead.");
        }

        // If order was New or In Progress, cancel active reservations
        if ("New".equals(orderStatus) || "In Progress".equals(orderStatus)) {
            cancelReservation(conn, "ORDER", orderId);
        }

        // Delete warehouse movements related to this order
        deleteWarehouseMovements(conn, "ORDER", String.valueOf(orderId));

        try (PreparedStatement pstmt = conn.prepareStatement(
                "DELETE FROM stock_reservations WHERE document_type = 'ORDER' AND document_id = ?")) {
            pstmt.setInt(1, orderId);
            pstmt.executeUpdate();
        }
    }

    public static void reverseCompletedSupplierOrders(Connection conn, int supplierId)
            throws SQLException {
        String ordersQuery = "SELECT id, number FROM supplier_orders WHERE supplier_id = ? AND status = 'Completed'";
        try (PreparedStatement ordersStmt = conn.prepareStatement(ordersQuery)) {
            ordersStmt.setInt(1, supplierId);
            try (ResultSet orders = ordersStmt.executeQuery()) {
                while (orders.next()) {
                    InventoryLotManager.reverseSupplierOrder(
                        conn, orders.getInt("id"), orders.getString("number"));
                }
            }
        }
    }

    /**
     * Delete an invoice and restore stock based on its status
     */
    public static void deleteInvoice(Connection conn, int invoiceId, String invoiceNumber, String invoiceStatus) throws SQLException {
        // If invoice was Issued or Paid, restore stock
        if ("Issued".equals(invoiceStatus) || "Paid".equals(invoiceStatus)) {
            restoreStockFromDocument(conn, invoiceId, "INVOICE");
        }

        // Delete warehouse movements related to this invoice
        deleteWarehouseMovements(conn, "INVOICE", invoiceNumber);
    }

    /**
     * Delete warehouse movements for a specific document
     */
    public static void deleteWarehouseMovements(Connection conn, String documentType, String documentNumber) throws SQLException {
        String deleteQuery = """
            DELETE FROM warehouse_movements
            WHERE document_type = ? AND document_number = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(deleteQuery)) {
            pstmt.setString(1, documentType);
            pstmt.setString(2, documentNumber);
            pstmt.executeUpdate();
        }
    }

    public static void requireManualMovement(Connection c, int movementId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT document_type FROM warehouse_movements WHERE id=?")) {
            p.setInt(1, movementId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Movement no longer exists");
                if ("ORDER".equals(r.getString(1)) || "SUPPLIER_ORDER".equals(r.getString(1)) || "INVENTORY_ADJUSTMENT".equals(r.getString(1))
                        || "ORDER_REVERSAL".equals(r.getString(1)) || "MOVEMENT_REVERSAL".equals(r.getString(1)))
                    throw new SQLException("Edit the source document to change this automatic movement");
            }
        }
    }

    /** Rebuild the cached reserved quantity from the active reservation ledger. */
    public static void reconcileReservedStock(Connection conn) throws SQLException {
        try (Statement s=conn.createStatement(); ResultSet r=s.executeQuery("""
                SELECT product_id, SUM(reserved_quantity) AS reserved
                FROM stock_reservations WHERE status='ACTIVE'
                GROUP BY product_id HAVING SUM(reserved_quantity)>2147483647
                """)) {
            if (r.next()) throw new SQLException("Active reservations exceed the supported quantity for product " + r.getInt("product_id"));
        }
        try (Statement s=conn.createStatement()) {
            s.executeUpdate("""
                UPDATE products SET reserved_quantity=COALESCE((
                    SELECT SUM(r.reserved_quantity) FROM stock_reservations r
                    WHERE r.product_id=products.id AND r.status='ACTIVE'
                ),0)
                """);
        }
    }

    // Helper classes

    public static class StockItem {
        private int productId;
        private String productName;
        private int quantity;

        public StockItem(int productId, String productName, int quantity) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
        }

        public int getProductId() { return productId; }
        public String getProductName() { return productName; }
        public int getQuantity() { return quantity; }
    }

    public static class StockAvailability {
        private int physicalStock;
        private int reservedStock;
        private int availableStock;
        private int requestedQuantity;

        public StockAvailability(int physicalStock, int reservedStock,
                                int availableStock, int requestedQuantity) {
            this.physicalStock = physicalStock;
            this.reservedStock = reservedStock;
            this.availableStock = availableStock;
            this.requestedQuantity = requestedQuantity;
        }

        public int getPhysicalStock() { return physicalStock; }
        public int getReservedStock() { return reservedStock; }
        public int getAvailableStock() { return availableStock; }
        public int getRequestedQuantity() { return requestedQuantity; }

        public String getFormattedMessage() {
            return String.format(
                "Physical: %d, Reserved: %d, Available: %d, Requested: %d",
                physicalStock, reservedStock, availableStock, requestedQuantity
            );
        }
    }
}

