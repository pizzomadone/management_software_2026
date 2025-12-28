import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WarehousePanel extends JPanel {
    private JTabbedPane tabbedPane;
    private JTable stockTable;
    private JTable movementsTable;
    private JTable notificationsTable;
    private DefaultTableModel stockModel;
    private DefaultTableModel movementsModel;
    private DefaultTableModel notificationsModel;
    private SimpleDateFormat dateFormat;

    public WarehousePanel() {
        dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        setupPanel();
        initComponents();
        loadData();
    }

    private void setupPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void initComponents() {
        tabbedPane = new JTabbedPane();

        // Tab Stock Status
        JPanel stockPanel = createStockPanel();
        tabbedPane.addTab("Stock Status", stockPanel);

        // Tab Movements
        JPanel movementsPanel = createMovementsPanel();
        tabbedPane.addTab("Movements", movementsPanel);

        // Tab Notifications
        JPanel notificationsPanel = createNotificationsPanel();
        tabbedPane.addTab("Notifications", notificationsPanel);

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createStockPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Stock table
        String[] columns = {"Code", "Product", "Physical", "Reserved", "Available", "Min Stock", "Status", "Preferred Supplier"};
        stockModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        stockTable = new JTable(stockModel);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton newMovementButton = new JButton("New Movement");
        JButton setMinStockButton = new JButton("Set Minimum Stock");
        JButton refreshButton = new JButton("Refresh");

        newMovementButton.addActionListener(e -> showMovementDialog(null));
        setMinStockButton.addActionListener(e -> showMinStockDialog());
        refreshButton.addActionListener(e -> loadStockData());

        buttonPanel.add(newMovementButton);
        buttonPanel.add(setMinStockButton);
        buttonPanel.add(refreshButton);

        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(stockTable), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createMovementsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Filters
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField searchField = new JTextField(20);
        JButton searchButton = new JButton("Search");

        filterPanel.add(new JLabel("Search:"));
        filterPanel.add(searchField);
        filterPanel.add(searchButton);

        // Movements table (ID hidden in column 0)
        String[] columns = {"ID", "Date", "Product", "Type", "Quantity", "Reason", "Document", "Notes"};
        movementsModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        movementsTable = new JTable(movementsModel);

        // Hide ID column
        movementsTable.getColumnModel().getColumn(0).setMinWidth(0);
        movementsTable.getColumnModel().getColumn(0).setMaxWidth(0);
        movementsTable.getColumnModel().getColumn(0).setWidth(0);

        // Double click to edit
        movementsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(evt)) {
                    editSelectedMovement();
                }
            }
        });

        searchButton.addActionListener(e -> searchMovements(searchField.getText()));

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton newButton = new JButton("New Movement");
        JButton editButton = new JButton("Edit");
        JButton deleteButton = new JButton("Delete");
        JButton refreshButton = new JButton("Refresh");

        newButton.addActionListener(e -> showMovementDialog(null));
        editButton.addActionListener(e -> editSelectedMovement());
        deleteButton.addActionListener(e -> deleteSelectedMovement());
        refreshButton.addActionListener(e -> loadMovementsData());

        buttonPanel.add(newButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(refreshButton);

        panel.add(filterPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(movementsTable), BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createNotificationsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Notifications table
        String[] columns = {"Date", "Product", "Type", "Message", "Status"};
        notificationsModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        notificationsTable = new JTable(notificationsModel);

        // Notification buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton markReadButton = new JButton("Mark as Read");
        JButton markHandledButton = new JButton("Mark as Handled");
        JButton refreshButton = new JButton("Refresh");

        markReadButton.addActionListener(e -> markSelectedNotifications("READ"));
        markHandledButton.addActionListener(e -> markSelectedNotifications("HANDLED"));
        refreshButton.addActionListener(e -> loadNotificationsData());

        buttonPanel.add(markReadButton);
        buttonPanel.add(markHandledButton);
        buttonPanel.add(refreshButton);

        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(notificationsTable), BorderLayout.CENTER);

        return panel;
    }

    private void loadData() {
        loadStockData();
        loadMovementsData();
        loadNotificationsData();
        checkLowStock();
    }

    private void loadStockData() {
        stockModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT p.*, sm.minimum_quantity, sm.reorder_quantity,
                        s.company_name as supplier_name
                FROM products p
                LEFT JOIN minimum_stock sm ON p.id = sm.product_id
                LEFT JOIN suppliers s ON sm.preferred_supplier_id = s.id
                ORDER BY p.name
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getString("code"));
                    row.add(rs.getString("name"));

                    int physicalStock = rs.getInt("quantity");
                    int reservedStock = rs.getInt("reserved_quantity");
                    int availableStock = physicalStock - reservedStock;

                    row.add(physicalStock);
                    row.add(reservedStock);
                    row.add(availableStock);

                    int minQuantity = rs.getInt("minimum_quantity");
                    row.add(minQuantity > 0 ? minQuantity : "-");

                    // Determine stock status based on available stock
                    String status;
                    if (minQuantity > 0) {
                        if (availableStock <= 0) {
                            status = "OUT OF STOCK";
                        } else if (availableStock < minQuantity) {
                            status = "LOW STOCK";
                        } else {
                            status = "OK";
                        }
                    } else {
                        status = availableStock <= 0 ? "OUT OF STOCK" : "OK";
                    }
                    row.add(status);

                    row.add(rs.getString("supplier_name"));
                    stockModel.addRow(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading warehouse data: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadMovementsData() {
        movementsModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT m.*, p.name as product_name
                FROM warehouse_movements m
                JOIN products p ON m.product_id = p.id
                ORDER BY m.date DESC
                LIMIT 100
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();

                    // ID (hidden column)
                    row.add(rs.getInt("id"));

                    Date movementDate = DateUtils.parseDate(rs, "date");
                    if (movementDate != null) {
                        row.add(DateUtils.formatDate(movementDate, dateFormat));
                    } else {
                        row.add("");
                    }

                    row.add(rs.getString("product_name"));
                    row.add(rs.getString("type"));
                    row.add(rs.getInt("quantity"));
                    row.add(rs.getString("reason"));

                    String document = rs.getString("document_type");
                    if (document != null && !document.isEmpty()) {
                        document += " " + rs.getString("document_number");
                    }
                    row.add(document);

                    row.add(rs.getString("notes"));
                    movementsModel.addRow(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading movements: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadNotificationsData() {
        notificationsModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT n.*, p.name as product_name
                FROM warehouse_notifications n
                JOIN products p ON n.product_id = p.id
                WHERE n.status != 'HANDLED'
                ORDER BY n.date DESC
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();

                    Date notificationDate = DateUtils.parseDate(rs, "date");
                    if (notificationDate != null) {
                        row.add(DateUtils.formatDate(notificationDate, dateFormat));
                    } else {
                        row.add("");
                    }

                    row.add(rs.getString("product_name"));
                    row.add(rs.getString("type"));
                    row.add(rs.getString("message"));
                    row.add(rs.getString("status"));
                    notificationsModel.addRow(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading notifications: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void checkLowStock() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT p.id, p.name, p.quantity, sm.minimum_quantity, sm.reorder_quantity
                FROM products p
                JOIN minimum_stock sm ON p.id = sm.product_id
                WHERE p.quantity <= sm.minimum_quantity
                AND NOT EXISTS (
                    SELECT 1 FROM warehouse_notifications n
                    WHERE n.product_id = p.id
                    AND n.type = 'MIN_STOCK'
                    AND n.status != 'HANDLED'
                    AND DATE(n.date) = DATE('now')
                )
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    int productId = rs.getInt("id");
                    String productName = rs.getString("name");
                    int quantity = rs.getInt("quantity");
                    int minQuantity = rs.getInt("minimum_quantity");

                    // Create notification
                    String message = String.format(
                        "Stock is below minimum (%d). Current quantity: %d",
                        minQuantity, quantity
                    );

                    createNotification(productId, "MIN_STOCK", message);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error checking minimum stock: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createNotification(int productId, String type, String message) {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                INSERT INTO warehouse_notifications
                (product_id, date, type, message, status)
                VALUES (?, CURRENT_TIMESTAMP, ?, ?, 'NEW')
            """;

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, productId);
                pstmt.setString(2, type);
                pstmt.setString(3, message);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void searchMovements(String searchTerm) {
        if (searchTerm.trim().isEmpty()) {
            loadMovementsData();
            return;
        }

        movementsModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT m.*, p.name as product_name
                FROM warehouse_movements m
                JOIN products p ON m.product_id = p.id
                WHERE p.name LIKE ?
                    OR m.reason LIKE ?
                    OR m.document_number LIKE ?
                ORDER BY m.date DESC
            """;

            String searchPattern = "%" + searchTerm + "%";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, searchPattern);
                pstmt.setString(2, searchPattern);
                pstmt.setString(3, searchPattern);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Vector<Object> row = new Vector<>();

                        Date movementDate = DateUtils.parseDate(rs, "date");
                        if (movementDate != null) {
                            row.add(DateUtils.formatDate(movementDate, dateFormat));
                        } else {
                            row.add("");
                        }

                        row.add(rs.getString("product_name"));
                        row.add(rs.getString("type"));
                        row.add(rs.getInt("quantity"));
                        row.add(rs.getString("reason"));

                        String document = rs.getString("document_type");
                        if (document != null && !document.isEmpty()) {
                            document += " " + rs.getString("document_number");
                        }
                        row.add(document);

                        row.add(rs.getString("notes"));
                        movementsModel.addRow(row);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error searching for movements: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void markSelectedNotifications(String newStatus) {
        int[] selectedRows = notificationsTable.getSelectedRows();
        if (selectedRows.length == 0) return;

        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false);

            try {
                String updateQuery = "UPDATE warehouse_notifications SET status = ? WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                    for (int row : selectedRows) {
                        // Find notification ID by matching data
                        String dateStr = (String)notificationsModel.getValueAt(row, 0);
                        String product = (String)notificationsModel.getValueAt(row, 1);
                        String type = (String)notificationsModel.getValueAt(row, 2);
                        String message = (String)notificationsModel.getValueAt(row, 3);

                        int notificationId = findNotificationId(dateStr, product, type, message);
                        if (notificationId > 0) {
                            pstmt.setString(1, newStatus);
                            pstmt.setInt(2, notificationId);
                            pstmt.executeUpdate();
                        }
                    }
                }

                conn.commit();
                loadNotificationsData();

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error updating notifications: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int findNotificationId(String dateStr, String productName, String type, String message) {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();

            // Try to parse the date string to match against database
            SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            Date parsedDate = null;
            try {
                parsedDate = displayFormat.parse(dateStr);
            } catch (Exception e) {
                // If parsing fails, we'll search without date
            }

            String query;
            if (parsedDate != null) {
                // Include date in search for more precision
                query = """
                    SELECT n.id
                    FROM warehouse_notifications n
                    JOIN products p ON n.product_id = p.id
                    WHERE p.name = ? AND n.type = ? AND n.message = ?
                    AND datetime(n.date) = datetime(?)
                    LIMIT 1
                """;
            } else {
                // Fallback to original query without date
                query = """
                    SELECT n.id
                    FROM warehouse_notifications n
                    JOIN products p ON n.product_id = p.id
                    WHERE p.name = ? AND n.type = ? AND n.message = ?
                    ORDER BY n.date DESC
                    LIMIT 1
                """;
            }

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, productName);
                pstmt.setString(2, type);
                pstmt.setString(3, message);
                if (parsedDate != null) {
                    pstmt.setTimestamp(4, new java.sql.Timestamp(parsedDate.getTime()));
                }

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void showMovementDialog(WarehouseMovement movement) {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);

        WarehouseMovementDialog dialog;
        if (parentWindow instanceof JFrame) {
            dialog = new WarehouseMovementDialog((JFrame) parentWindow, movement);
        } else {
            dialog = new WarehouseMovementDialog((JDialog) parentWindow, movement);
        }

        dialog.setVisible(true);
        if (dialog.isMovementSaved()) {
            loadData();
        }
    }

    private void showMinStockDialog() {
        int selectedRow = stockTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                "Select a product to set the minimum stock",
                "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String code = (String)stockModel.getValueAt(selectedRow, 0);
        try {
            MinimumStock minStock = loadMinimumStock(code);
            Window parentWindow = SwingUtilities.getWindowAncestor(this);

            MinimumStockDialog dialog;
            if (parentWindow instanceof JFrame) {
                dialog = new MinimumStockDialog((JFrame) parentWindow, minStock);
            } else {
                dialog = new MinimumStockDialog((JDialog) parentWindow, minStock);
            }

            dialog.setVisible(true);
            if (dialog.isStockSaved()) {
                loadData();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading minimum stock data: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private MinimumStock loadMinimumStock(String code) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        String query = """
            SELECT p.id, p.name, sm.minimum_quantity, sm.reorder_quantity,
                    sm.lead_time_days, sm.preferred_supplier_id,
                    s.company_name as supplier_name, sm.notes
            FROM products p
            LEFT JOIN minimum_stock sm ON p.id = sm.product_id
            LEFT JOIN suppliers s ON sm.preferred_supplier_id = s.id
            WHERE p.code = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, code);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new MinimumStock(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("minimum_quantity"),
                        rs.getInt("reorder_quantity"),
                        rs.getInt("lead_time_days"),
                        rs.getObject("preferred_supplier_id") != null ?
                            rs.getInt("preferred_supplier_id") : null,
                        rs.getString("supplier_name"),
                        rs.getString("notes")
                    );
            }
        }
        // Return a default object if the product has no minimum stock settings
        return new MinimumStock(
            0, "", 0, 0, 0, null, null, ""
        );
    }

    private void editSelectedMovement() {
        int selectedRow = movementsTable.getSelectedRow();
        if (selectedRow != -1) {
            int movementId = (int)movementsModel.getValueAt(selectedRow, 0);

            try {
                Connection conn = DatabaseManager.getInstance().getConnection();
                String query = """
                    SELECT m.*, p.name as product_name
                    FROM warehouse_movements m
                    JOIN products p ON m.product_id = p.id
                    WHERE m.id = ?
                """;

                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, movementId);
                    ResultSet rs = pstmt.executeQuery();

                    if (rs.next()) {
                        WarehouseMovement movement = new WarehouseMovement(
                            rs.getInt("id"),
                            rs.getInt("product_id"),
                            rs.getString("product_name"),
                            DateUtils.parseDate(rs, "date"),
                            rs.getString("type"),
                            rs.getInt("quantity"),
                            rs.getString("reason"),
                            rs.getString("document_number"),
                            rs.getString("document_type"),
                            rs.getString("notes")
                        );

                        showMovementDialog(movement);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error loading movement: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteSelectedMovement() {
        int selectedRow = movementsTable.getSelectedRow();
        if (selectedRow != -1) {
            int movementId = (int)movementsModel.getValueAt(selectedRow, 0);
            String product = (String)movementsModel.getValueAt(selectedRow, 2);
            String type = (String)movementsModel.getValueAt(selectedRow, 3);
            int quantity = (int)movementsModel.getValueAt(selectedRow, 4);

            int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete this movement?\n\n" +
                "Product: " + product + "\n" +
                "Type: " + type + "\n" +
                "Quantity: " + quantity + "\n\n" +
                "Stock will be adjusted accordingly.",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                try {
                    Connection conn = DatabaseManager.getInstance().getConnection();
                    conn.setAutoCommit(false);

                    try {
                        // Get movement details to restore stock
                        int productId = 0;
                        String movementType = "";
                        int movementQuantity = 0;

                        String getMovementQuery = "SELECT product_id, type, quantity FROM warehouse_movements WHERE id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(getMovementQuery)) {
                            pstmt.setInt(1, movementId);
                            ResultSet rs = pstmt.executeQuery();
                            if (rs.next()) {
                                productId = rs.getInt("product_id");
                                movementType = rs.getString("type");
                                movementQuantity = rs.getInt("quantity");
                            }
                        }

                        // Reverse the stock movement
                        if (productId > 0) {
                            String updateStockQuery = "UPDATE products SET quantity = quantity + ? WHERE id = ?";
                            try (PreparedStatement pstmt = conn.prepareStatement(updateStockQuery)) {
                                int quantityDelta = "INWARD".equals(movementType) ? -movementQuantity : movementQuantity;
                                pstmt.setInt(1, quantityDelta);
                                pstmt.setInt(2, productId);
                                pstmt.executeUpdate();
                            }
                        }

                        // Delete the movement
                        String deleteQuery = "DELETE FROM warehouse_movements WHERE id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(deleteQuery)) {
                            pstmt.setInt(1, movementId);
                            pstmt.executeUpdate();
                        }

                        conn.commit();
                        loadMovementsData();
                        loadStockData();

                        JOptionPane.showMessageDialog(this,
                            "Movement deleted successfully!\nStock has been adjusted.",
                            "Success", JOptionPane.INFORMATION_MESSAGE);

                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(this,
                        "Error deleting movement: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
}
