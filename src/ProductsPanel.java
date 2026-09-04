import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.Vector;

public class ProductsPanel extends JPanel {
    private JTable productsTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JButton addButton;
    private JButton editButton;
    private JButton deleteButton;
    private JButton refreshButton;
    
    public ProductsPanel() {
        setupPanel();
        initComponents();
        loadProducts();
    }
    
    private void setupPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }
    
    private void initComponents() {
        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Products"));
        
        searchField = new JTextField(20);
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchProducts());
        
        searchPanel.add(new JLabel("Search: "));
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        
        // Products table
        String[] columns = {"ID", "Code", "Name", "Description", "Price", "Physical", "Reserved", "Available", "On Order", "Category", "Unit", "Min Qty", "Active", "Supplier", "Warehouse Pos", "VAT %"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        productsTable = new JTable(tableModel);
        productsTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        productsTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        // Enable column sorting
        TableSorterUtil.enableSorting(productsTable);

        // Add Delete key shortcut
        TableInteractionUtil.addDeleteKeyAction(productsTable, this::deleteSelectedProduct);

        // Add context menu
        TableInteractionUtil.addContextMenu(productsTable,
            new TableInteractionUtil.TableAction("Add Product", () -> showProductDialog(null), false),
            new TableInteractionUtil.TableAction("Edit", this::editSelectedProduct),
            new TableInteractionUtil.TableAction("Delete", this::deleteSelectedProduct),
            null, // Separator
            new TableInteractionUtil.TableAction("Refresh", this::loadProducts, false)
        );

        // Add mouse listener for double click
        productsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(evt)) {
                    int selectedRow = productsTable.getSelectedRow();
                    if (selectedRow != -1) {
                        editSelectedProduct();
                    }
                }
            }
        });
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        addButton = new JButton("Add");
        editButton = new JButton("Edit");
        deleteButton = new JButton("Delete");
        refreshButton = new JButton("Refresh");
        
        addButton.addActionListener(e -> showProductDialog(null));
        editButton.addActionListener(e -> editSelectedProduct());
        deleteButton.addActionListener(e -> deleteSelectedProduct());
        refreshButton.addActionListener(e -> loadProducts());
        
        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(refreshButton);
        
        // Main layout
        add(searchPanel, BorderLayout.NORTH);
        add(new JScrollPane(productsTable), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        updateButtonStates();
    }
    
    private void updateButtonStates() {
        boolean isRowSelected = productsTable.getSelectedRow() != -1;
        editButton.setEnabled(isRowSelected);
        deleteButton.setEnabled(isRowSelected);
    }
    
    public void loadProducts() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT p.*,
                       f.company_name as supplier_name,
                       COALESCE(SUM(CASE
                           WHEN so.status IN ('Draft', 'Confirmed', 'In Transit')
                           THEN sod.quantity
                           ELSE 0
                       END), 0) as on_order_quantity
                FROM products p
                LEFT JOIN suppliers f ON p.supplier_id = f.id
                LEFT JOIN supplier_order_details sod ON p.id = sod.product_id
                LEFT JOIN supplier_orders so ON sod.order_id = so.id
                GROUP BY p.id
                ORDER BY p.name
            """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getInt("id"));
                    row.add(rs.getString("code"));
                    row.add(rs.getString("name"));
                    row.add(rs.getString("description"));
                    row.add(rs.getDouble("price"));

                    int physicalStock = rs.getInt("quantity");
                    int reservedStock = rs.getInt("reserved_quantity");
                    int availableStock = physicalStock - reservedStock;
                    int onOrderQuantity = rs.getInt("on_order_quantity");

                    row.add(physicalStock);
                    row.add(reservedStock);
                    row.add(availableStock);
                    row.add(onOrderQuantity);

                    row.add(rs.getString("category"));
                    row.add(rs.getString("unit_of_measure"));
                    row.add(rs.getInt("minimum_quantity"));
                    row.add(rs.getInt("active") == 1 ? "Yes" : "No");
                    row.add(rs.getString("supplier_name") != null ? rs.getString("supplier_name") : "");
                    row.add(rs.getString("warehouse_position") != null ? rs.getString("warehouse_position") : "");
                    row.add(rs.getDouble("vat_rate"));
                    tableModel.addRow(row);
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                "Error loading products: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void searchProducts() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadProducts();
            return;
        }

        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT p.*,
                       f.company_name as supplier_name,
                       COALESCE(SUM(CASE
                           WHEN so.status IN ('Draft', 'Confirmed', 'In Transit')
                           THEN sod.quantity
                           ELSE 0
                       END), 0) as on_order_quantity
                FROM products p
                LEFT JOIN suppliers f ON p.supplier_id = f.id
                LEFT JOIN supplier_order_details sod ON p.id = sod.product_id
                LEFT JOIN supplier_orders so ON sod.order_id = so.id
                WHERE p.code LIKE ? OR p.name LIKE ? OR p.description LIKE ?
                GROUP BY p.id
                ORDER BY p.name
            """;
            String searchPattern = "%" + searchTerm + "%";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, searchPattern);
                pstmt.setString(2, searchPattern);
                pstmt.setString(3, searchPattern);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Vector<Object> row = new Vector<>();
                        row.add(rs.getInt("id"));
                        row.add(rs.getString("code"));
                        row.add(rs.getString("name"));
                        row.add(rs.getString("description"));
                        row.add(rs.getDouble("price"));

                        int physicalStock = rs.getInt("quantity");
                        int reservedStock = rs.getInt("reserved_quantity");
                        int availableStock = physicalStock - reservedStock;
                        int onOrderQuantity = rs.getInt("on_order_quantity");

                        row.add(physicalStock);
                        row.add(reservedStock);
                        row.add(availableStock);
                        row.add(onOrderQuantity);

                        row.add(rs.getString("category"));
                        row.add(rs.getString("unit_of_measure"));
                        row.add(rs.getInt("minimum_quantity"));
                        row.add(rs.getInt("active") == 1 ? "Yes" : "No");
                        row.add(rs.getString("supplier_name") != null ? rs.getString("supplier_name") : "");
                        row.add(rs.getString("warehouse_position") != null ? rs.getString("warehouse_position") : "");
                        row.add(rs.getDouble("vat_rate"));
                        tableModel.addRow(row);
                    }
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                "Error searching for products: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void showProductDialog(Product product) {
        // Get the parent window for the dialog
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        
        ProductDialog dialog;
        if (parentWindow instanceof JFrame) {
            dialog = new ProductDialog((JFrame) parentWindow, product);
        } else {
            dialog = new ProductDialog((JDialog) parentWindow, product);
        }
        
        dialog.setVisible(true);
        if (dialog.isProductSaved()) {
            loadProducts();
        }
    }
    
    private void editSelectedProduct() {
        int selectedRow = productsTable.getSelectedRow();
        if (selectedRow != -1) {
            // Convert view index to model index (important when table is sorted)
            int modelRow = productsTable.convertRowIndexToModel(selectedRow);
            int productId = (int)tableModel.getValueAt(modelRow, 0);

            try {
                Connection conn = DatabaseManager.getInstance().getConnection();
                String query = """
                    SELECT p.*, f.company_name as supplier_name
                    FROM products p
                    LEFT JOIN suppliers f ON p.supplier_id = f.id
                    WHERE p.id = ?
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, productId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            // Safely read integer and double fields with proper type handling
                            Integer supplierId = getIntegerFromResultSet(rs, "supplier_id");
                            String supplierName = rs.getString("supplier_name");

                            int quantity = getIntegerFromResultSet(rs, "quantity", 0);
                            int minimumQuantity = getIntegerFromResultSet(rs, "minimum_quantity", 0);
                            int active = getIntegerFromResultSet(rs, "active", 1);
                            double acquisitionCost = getDoubleFromResultSet(rs, "acquisition_cost", 0.0);
                            double weight = getDoubleFromResultSet(rs, "weight", 0.0);
                            double vatRate = getDoubleFromResultSet(rs, "vat_rate", 0.0);

                            Product product = new Product(
                                rs.getInt("id"),
                                rs.getString("code"),
                                rs.getString("name"),
                                rs.getString("description"),
                                rs.getDouble("price"),
                                quantity,
                                rs.getString("category") != null ? rs.getString("category") : "",
                                rs.getString("alternative_sku") != null ? rs.getString("alternative_sku") : "",
                                weight,
                                rs.getString("unit_of_measure") != null ? rs.getString("unit_of_measure") : "pcs",
                                minimumQuantity,
                                acquisitionCost,
                                active == 1,
                                supplierId,
                                supplierName != null ? supplierName : "",
                                rs.getString("warehouse_position") != null ? rs.getString("warehouse_position") : "",
                                vatRate
                            );
                            showProductDialog(product);
                        }
                    }
                }
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this,
                    "Error loading product: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void deleteSelectedProduct() {
        int selectedRow = productsTable.getSelectedRow();
        if (selectedRow != -1) {
            // Convert view index to model index (important when table is sorted)
            int modelRow = productsTable.convertRowIndexToModel(selectedRow);
            int id = (int)tableModel.getValueAt(modelRow, 0);
            String name = (String)tableModel.getValueAt(modelRow, 2);
            
            try {
                Connection conn = DatabaseManager.getInstance().getConnection();
                
                // Check for existing dependencies
                boolean hasOrders = hasProductInOrders(conn, id);
                boolean hasInvoices = hasProductInInvoices(conn, id);
                boolean hasSupplierOrders = hasProductInSupplierOrders(conn, id);
                boolean hasPriceLists = hasProductInPriceLists(conn, id);
                boolean hasWarehouseMovements = hasProductInWarehouseMovements(conn, id);
                boolean hasMinStock = hasProductInMinStock(conn, id);
                boolean hasReservations = hasProductInReservations(conn, id);
                boolean hasNotifications = hasProductInNotifications(conn, id);
                
                boolean hasCriticalData = hasOrders || hasInvoices || hasSupplierOrders ||
                    hasWarehouseMovements || hasReservations;
                if (hasCriticalData) {
                    StringBuilder message = new StringBuilder();
                    message.append("Product '").append(name).append("' cannot be deleted because it is part of critical business history:\n");
                    
                    if (hasOrders) message.append("- Customer orders\n");
                    if (hasInvoices) message.append("- Invoice entries\n");
                    if (hasSupplierOrders) message.append("- Supplier orders\n");
                    if (hasPriceLists) message.append("- Price list entries\n");
                    if (hasWarehouseMovements) message.append("- Warehouse movements\n");
                    if (hasReservations) message.append("- Active or historical stock reservations\n");
                    message.append("\nDeleting it would alter orders, invoices or stock history. " +
                        "Set the product as inactive instead.");
                    JOptionPane.showMessageDialog(this,
                        message.toString(),
                        "Product Cannot Be Deleted", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                if (hasPriceLists || hasMinStock || hasNotifications) {
                    String details = (hasPriceLists ? "- Price list entries\n" : "") +
                        (hasMinStock ? "- Minimum stock settings\n" : "") +
                        (hasNotifications ? "- Warehouse notifications\n" : "");
                    int cleanup = JOptionPane.showConfirmDialog(this,
                        "Product '" + name + "' has only non-critical configuration data:\n" + details +
                        "\nThese records will also be deleted. Continue?",
                        "Confirm Product Cleanup", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                    if (cleanup == JOptionPane.YES_OPTION) {
                        deleteProductAndNonCriticalData(conn, id);
                        loadProducts();
                    }
                    return;
                }

                // Safe to delete - no foreign key references
                int result = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete the product '" + name + "'?",
                    "Confirm Deletion",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
                    
                if (result == JOptionPane.YES_OPTION) {
                    String query = "DELETE FROM products WHERE id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                        pstmt.setInt(1, id);
                        pstmt.executeUpdate();
                        loadProducts();
                        
                        JOptionPane.showMessageDialog(this,
                            "Product deleted successfully",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error deleting the product: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteProductAndNonCriticalData(Connection conn, int productId) throws SQLException {
        conn.setAutoCommit(false);
        try {
            String[] statements = {
                "DELETE FROM supplier_price_lists WHERE product_id = ?",
                "DELETE FROM warehouse_notifications WHERE product_id = ?",
                "DELETE FROM minimum_stock WHERE product_id = ?",
                "DELETE FROM products WHERE id = ?"
            };
            for (String statement : statements) {
                try (PreparedStatement pstmt = conn.prepareStatement(statement)) {
                    pstmt.setInt(1, productId);
                    pstmt.executeUpdate();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private boolean hasProductInReservations(Connection conn, int productId) throws SQLException {
        return hasRows(conn, "SELECT COUNT(*) FROM stock_reservations WHERE product_id = ?", productId);
    }

    private boolean hasProductInNotifications(Connection conn, int productId) throws SQLException {
        return hasRows(conn, "SELECT COUNT(*) FROM warehouse_notifications WHERE product_id = ?", productId);
    }

    private boolean hasRows(Connection conn, String query, int productId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
    
    private boolean hasProductInOrders(Connection conn, int productId) throws SQLException {
        String query = "SELECT COUNT(*) FROM order_details WHERE product_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean hasProductInInvoices(Connection conn, int productId) throws SQLException {
        String query = "SELECT COUNT(*) FROM invoice_details WHERE product_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean hasProductInSupplierOrders(Connection conn, int productId) throws SQLException {
        String query = "SELECT COUNT(*) FROM supplier_order_details WHERE product_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean hasProductInPriceLists(Connection conn, int productId) throws SQLException {
        String query = "SELECT COUNT(*) FROM supplier_price_lists WHERE product_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean hasProductInWarehouseMovements(Connection conn, int productId) throws SQLException {
        String query = "SELECT COUNT(*) FROM warehouse_movements WHERE product_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean hasProductInMinStock(Connection conn, int productId) throws SQLException {
        String query = "SELECT COUNT(*) FROM minimum_stock WHERE product_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    // Helper methods to safely read integer and double values from ResultSet
    private Integer getIntegerFromResultSet(ResultSet rs, String columnName) {
        try {
            Object value = rs.getObject(columnName);
            if (value == null) {
                return null;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value instanceof String) {
                String strValue = ((String) value).trim();
                if (strValue.isEmpty()) {
                    return null;
                }
                return Integer.parseInt(strValue);
            }
            return null;
        } catch (SQLException | NumberFormatException e) {
            return null;
        }
    }

    private int getIntegerFromResultSet(ResultSet rs, String columnName, int defaultValue) {
        Integer value = getIntegerFromResultSet(rs, columnName);
        return value != null ? value : defaultValue;
    }

    private double getDoubleFromResultSet(ResultSet rs, String columnName, double defaultValue) {
        try {
            Object value = rs.getObject(columnName);
            if (value == null) {
                return defaultValue;
            }
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value instanceof String) {
                String strValue = ((String) value).trim();
                if (strValue.isEmpty()) {
                    return defaultValue;
                }
                return Double.parseDouble(strValue);
            }
            return defaultValue;
        } catch (SQLException | NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Selects and highlights a specific product in the table by product code
     * @param productCode The code of the product to select
     */
    public void selectProductByCode(String productCode) {
        // First ensure the table is loaded
        loadProducts();

        // Search for the product code in the table model
        for (int modelRow = 0; modelRow < tableModel.getRowCount(); modelRow++) {
            Object value = tableModel.getValueAt(modelRow, 1); // Code is in column 1
            if (value != null && value instanceof String && value.equals(productCode)) {
                // Found the product, convert model index to view index
                int viewRow = productsTable.convertRowIndexToView(modelRow);
                // Select the row in the view
                productsTable.setRowSelectionInterval(viewRow, viewRow);
                // Scroll to make the row visible
                productsTable.scrollRectToVisible(productsTable.getCellRect(viewRow, 0, true));
                // Update button states
                updateButtonStates();
                return;
            }
        }

        // If we get here, the product was not found
        JOptionPane.showMessageDialog(this,
            "Product with code '" + productCode + "' not found in the list.",
            "Product Not Found",
            JOptionPane.INFORMATION_MESSAGE);
    }
}
