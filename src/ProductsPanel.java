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
        String[] columns = {"ID", "Code", "Name", "Description", "Price", "Physical", "Reserved", "Available", "Category", "Unit", "Min Qty", "Active", "Supplier", "Warehouse Pos", "VAT %"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        productsTable = new JTable(tableModel);
        productsTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        productsTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());
        
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
    
    private void loadProducts() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT p.*, f.company_name as supplier_name
                FROM products p
                LEFT JOIN suppliers f ON p.supplier_id = f.id
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

                    row.add(physicalStock);
                    row.add(reservedStock);
                    row.add(availableStock);

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
                SELECT p.*, f.company_name as supplier_name
                FROM products p
                LEFT JOIN suppliers f ON p.supplier_id = f.id
                WHERE p.code LIKE ? OR p.name LIKE ? OR p.description LIKE ?
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

                        row.add(physicalStock);
                        row.add(reservedStock);
                        row.add(availableStock);

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
            int productId = (int)tableModel.getValueAt(selectedRow, 0);

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
            int id = (int)tableModel.getValueAt(selectedRow, 0);
            String name = (String)tableModel.getValueAt(selectedRow, 2);
            
            try {
                Connection conn = DatabaseManager.getInstance().getConnection();
                
                // Check for existing dependencies
                boolean hasOrders = hasProductInOrders(conn, id);
                boolean hasInvoices = hasProductInInvoices(conn, id);
                boolean hasSupplierOrders = hasProductInSupplierOrders(conn, id);
                boolean hasPriceLists = hasProductInPriceLists(conn, id);
                boolean hasWarehouseMovements = hasProductInWarehouseMovements(conn, id);
                boolean hasMinStock = hasProductInMinStock(conn, id);
                
                if (hasOrders || hasInvoices || hasSupplierOrders || hasPriceLists || hasWarehouseMovements || hasMinStock) {
                    StringBuilder message = new StringBuilder();
                    message.append("Cannot delete product '").append(name).append("' because it has:\n");
                    
                    if (hasOrders) message.append("- Customer orders\n");
                    if (hasInvoices) message.append("- Invoice entries\n");
                    if (hasSupplierOrders) message.append("- Supplier orders\n");
                    if (hasPriceLists) message.append("- Price list entries\n");
                    if (hasWarehouseMovements) message.append("- Warehouse movements\n");
                    if (hasMinStock) message.append("- Minimum stock settings\n");
                    
                    message.append("\nOptions:\n");
                    message.append("1. Delete/reassign related records first\n");
                    message.append("2. Use 'Force Delete' to remove all related data");
                    
                    String[] options = {"Cancel", "Force Delete (All Data)"};
                    int choice = JOptionPane.showOptionDialog(this,
                        message.toString(),
                        "Cannot Delete Product",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null, options, options[0]);
                    
                    if (choice == 1) { // Force Delete
                        performCascadeDelete(conn, id, name);
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
    
    private void performCascadeDelete(Connection conn, int id, String name) {
        int confirmResult = JOptionPane.showConfirmDialog(this,
            "WARNING: This will permanently delete product '" + name + "' and ALL related data:\n" +
            "- All customer orders containing this product\n" +
            "- All invoice entries\n" +
            "- All supplier orders\n" +
            "- All price list entries\n" +
            "- All warehouse movements\n" +
            "- All minimum stock settings\n\n" +
            "This action CANNOT be undone!\n\n" +
            "Are you absolutely sure?",
            "FORCE DELETE - Final Confirmation",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.ERROR_MESSAGE);
            
        if (confirmResult != JOptionPane.YES_OPTION) {
            return;
        }
        
        try {
            conn.setAutoCommit(false);
            
            try {
                // Delete in order to respect foreign key constraints
                
                // 1. Delete order details
                String deleteOrderDetails = "DELETE FROM order_details WHERE product_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteOrderDetails)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " order details");
                }
                
                // 2. Delete invoice details
                String deleteInvoiceDetails = "DELETE FROM invoice_details WHERE product_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteInvoiceDetails)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " invoice details");
                }
                
                // 3. Delete supplier order details
                String deleteSupplierOrderDetails = "DELETE FROM supplier_order_details WHERE product_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteSupplierOrderDetails)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " supplier order details");
                }
                
                // 4. Delete price lists
                String deletePriceLists = "DELETE FROM supplier_price_lists WHERE product_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deletePriceLists)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " price list entries");
                }
                
                // 5. Delete warehouse movements
                String deleteWarehouseMovements = "DELETE FROM warehouse_movements WHERE product_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteWarehouseMovements)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " warehouse movements");
                }
                
                // 6. Delete warehouse notifications
                String deleteNotifications = "DELETE FROM warehouse_notifications WHERE product_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteNotifications)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " warehouse notifications");
                }
                
                // 7. Delete minimum stock settings
                String deleteMinStock = "DELETE FROM minimum_stock WHERE product_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteMinStock)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " minimum stock settings");
                }
                
                // 8. Finally delete the product
                String deleteProduct = "DELETE FROM products WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteProduct)) {
                    pstmt.setInt(1, id);
                    pstmt.executeUpdate();
                    System.out.println("Deleted product");
                }

                conn.commit();
                loadProducts();

                JOptionPane.showMessageDialog(this,
                    "Product '" + name + "' and all related records deleted successfully",
                    "Force Delete Completed",
                    JOptionPane.INFORMATION_MESSAGE);
                    
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error during force delete: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
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
}