import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;

public class SupplierOrderDialog extends JDialog {
    private int supplierId;
    private String supplierName;
    private SupplierOrder order;
    private boolean orderSaved = false;
    private String previousStatus = null;

    private JTextField numberField;
    private JTextField dateField;
    private JTextField deliveryDateField;
    private JComboBox<String> statusCombo;
    private JTextArea notesArea;
    private JTable itemsTable;
    private DefaultTableModel itemsTableModel;
    private JLabel totalLabel;
    private SimpleDateFormat dateFormat;
    private Map<Integer, Product> productsCache;
    private boolean updatingTotals = false; // Flag to prevent recursion

    public SupplierOrderDialog(JDialog parent, int supplierId, String supplierName, SupplierOrder order) {
        super(parent, order == null ? "New Supplier Order" : "Edit Supplier Order", true);
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.order = order;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        this.productsCache = new HashMap<>();

        setupWindow();
        initComponents();
        loadProducts();
        if (order != null) {
            loadOrderData();
        } else {
            generateOrderNumber();
        }
    }

    private void setupWindow() {
        setSize(800, 600);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(10, 10));
    }

    private void initComponents() {
        // Main panel with padding
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel for order data
        JPanel orderPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Order number
        gbc.gridx = 0; gbc.gridy = 0;
        orderPanel.add(new JLabel("Order Number:"), gbc);

        gbc.gridx = 1;
        numberField = new JTextField(15);
        numberField.setEditable(false);
        orderPanel.add(numberField, gbc);

        // Supplier
        gbc.gridx = 2;
        orderPanel.add(new JLabel("Supplier:"), gbc);

        gbc.gridx = 3;
        JTextField supplierField = new JTextField(supplierName);
        supplierField.setEditable(false);
        orderPanel.add(supplierField, gbc);

        // Order date
        gbc.gridx = 0; gbc.gridy = 1;
        orderPanel.add(new JLabel("Order Date:"), gbc);

        gbc.gridx = 1;
        dateField = new JTextField(10);
        dateField.setText(DateUtils.formatDate(new Date(), dateFormat));
        orderPanel.add(dateField, gbc);

        // Delivery date
        gbc.gridx = 2;
        orderPanel.add(new JLabel("Delivery Date:"), gbc);

        gbc.gridx = 3;
        deliveryDateField = new JTextField(10);
        orderPanel.add(deliveryDateField, gbc);

        // Status
        gbc.gridx = 0; gbc.gridy = 2;
        orderPanel.add(new JLabel("Status:"), gbc);

        gbc.gridx = 1;
        statusCombo = new JComboBox<>(new String[]{"Draft", "Confirmed", "In Transit", "Completed", "Cancelled"});
        orderPanel.add(statusCombo, gbc);

        // Notes
        gbc.gridx = 0; gbc.gridy = 3;
        orderPanel.add(new JLabel("Notes:"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 3;
        notesArea = new JTextArea(3, 40);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        orderPanel.add(new JScrollPane(notesArea), gbc);

        // Products table
        String[] columns = {"Code", "Product", "Quantity", "Unit Price", "Total", "Notes"};
        itemsTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 2 || column == 5; // Only quantity and notes are editable
            }
        };
        itemsTable = new JTable(itemsTableModel);

        // FIXED: Improved listener to prevent infinite recursion
        itemsTableModel.addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE &&
                e.getColumn() == 2 && !updatingTotals) {
                SwingUtilities.invokeLater(this::updateTotals);
            }
        });

        // Table buttons panel
        JPanel tableButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addItemButton = new JButton("Add Product");
        JButton removeItemButton = new JButton("Remove Product");

        addItemButton.addActionListener(e -> showAddProductDialog());
        removeItemButton.addActionListener(e -> removeSelectedProduct());

        tableButtonPanel.add(addItemButton);
        tableButtonPanel.add(removeItemButton);

        // Total panel
        JPanel totalPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        totalLabel = new JLabel("Total: € 0.00");
        totalLabel.setFont(new Font(totalLabel.getFont().getName(), Font.BOLD, 14));
        totalPanel.add(totalLabel);

        // Main buttons panel
        JPanel buttonPanel = new JPanel();
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> saveOrder());
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        // Assembly
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(tableButtonPanel, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(itemsTable), BorderLayout.CENTER);
        centerPanel.add(totalPanel, BorderLayout.SOUTH);

        mainPanel.add(orderPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void generateOrderNumber() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT COALESCE(MAX(CAST(SUBSTR(number, 5) AS INTEGER)), 0) + 1 as next_num
                FROM supplier_orders
                WHERE number LIKE ?
            """;

            Calendar cal = Calendar.getInstance();
            String yearPrefix = String.format("OF%d", cal.get(Calendar.YEAR));

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, yearPrefix + "%");
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int nextNum = rs.getInt("next_num");
                    numberField.setText(String.format("%s%04d", yearPrefix, nextNum));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error generating order number: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadProducts() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT p.*, COALESCE(l.price, p.price) as supplier_price,
                       l.supplier_product_code
                FROM products p
                LEFT JOIN supplier_price_lists l ON p.id = l.product_id
                    AND l.supplier_id = ?
                    AND (l.validity_end_date IS NULL OR DATE(l.validity_end_date) >= DATE('now'))
                ORDER BY p.name
            """;

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, supplierId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Product product = new Product(
                        rs.getInt("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getDouble("supplier_price"),
                        rs.getInt("quantity")
                    );
                    productsCache.put(product.getId(), product);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading products: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadOrderData() {
        numberField.setText(order.getNumber());
        dateField.setText(DateUtils.formatDate(order.getOrderDate(), dateFormat));
        if (order.getExpectedDeliveryDate() != null) {
            deliveryDateField.setText(DateUtils.formatDate(order.getExpectedDeliveryDate(), dateFormat));
        }
        statusCombo.setSelectedItem(order.getStatus());
        previousStatus = order.getStatus(); // Store current status as previous
        notesArea.setText(order.getNotes());

        // Load products
        for (SupplierOrderItem item : order.getItems()) {
            Vector<Object> row = new Vector<>();
            row.add(item.getProductCode());
            row.add(item.getProductName());
            row.add(item.getQuantity());
            row.add(String.format("%.2f", item.getUnitPrice()));
            row.add(String.format("%.2f", item.getTotal()));
            row.add(item.getNotes());
            itemsTableModel.addRow(row);
        }

        updateTotals();
    }

    private void showAddProductDialog() {
        JDialog dialog = new JDialog(this, "Add Product", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(500, 200);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Product combobox
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Product:"), gbc);

        gbc.gridx = 1;
        JComboBox<ProductDisplay> productCombo = new JComboBox<>();
        for (Product product : productsCache.values()) {
            productCombo.addItem(new ProductDisplay(product));
        }
        panel.add(productCombo, gbc);

        // Quantity
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Quantity:"), gbc);

        gbc.gridx = 1;
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(1, 1, 9999, 1);
        JSpinner quantitySpinner = new JSpinner(spinnerModel);
        panel.add(quantitySpinner, gbc);

        // Notes
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Notes:"), gbc);

        gbc.gridx = 1;
        JTextField noteField = new JTextField(30);
        panel.add(noteField, gbc);

        // Buttons
        JPanel buttonPanel = new JPanel();
        JButton addButton = new JButton("Add");
        JButton cancelButton = new JButton("Cancel");

        addButton.addActionListener(e -> {
            ProductDisplay selectedProduct = (ProductDisplay)productCombo.getSelectedItem();
            if (selectedProduct == null) {
                JOptionPane.showMessageDialog(dialog,
                    "Please select a product",
                    "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int quantity = (int)quantitySpinner.getValue();
            String note = noteField.getText().trim();

            // Check if product is already present
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                String code = (String)itemsTableModel.getValueAt(i, 0);
                if (code.equals(selectedProduct.getProduct().getCode())) {
                    JOptionPane.showMessageDialog(dialog,
                        "This product is already in the order",
                        "Warning", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            // Add to table
            Vector<Object> row = new Vector<>();
            row.add(selectedProduct.getProduct().getCode());
            row.add(selectedProduct.getProduct().getName());
            row.add(quantity);
            row.add(String.format("%.2f", selectedProduct.getProduct().getPrice()));
            row.add(String.format("%.2f", quantity * selectedProduct.getProduct().getPrice()));
            row.add(note);
            itemsTableModel.addRow(row);

            updateTotals();
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(addButton);
        buttonPanel.add(cancelButton);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private static class ProductDisplay {
        private Product product;

        public ProductDisplay(Product product) {
            this.product = product;
        }

        public Product getProduct() { return product; }

        @Override
        public String toString() {
            return String.format("%s - %s (€ %.2f)",
                product.getCode(), product.getName(), product.getPrice());
        }
    }

    private void removeSelectedProduct() {
        int selectedRow = itemsTable.getSelectedRow();
        if (selectedRow != -1) {
            itemsTableModel.removeRow(selectedRow);
            updateTotals();
        }
    }

    // FIXED: Improved method to prevent infinite recursion
    private void updateTotals() {
        if (updatingTotals) return;

        updatingTotals = true;
        try {
            double total = 0;

            // Calculate total without modifying the table
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                Object quantityObj = itemsTableModel.getValueAt(i, 2);
                Object priceObj = itemsTableModel.getValueAt(i, 3);

                int quantity = parseInteger(quantityObj);
                double price = parseDouble(priceObj);

                double itemTotal = quantity * price;
                total += itemTotal;
            }

            // Update the total label
            totalLabel.setText(String.format("Total: € %.2f", total));

            // Update row totals in a separate EDT event
            SwingUtilities.invokeLater(() -> {
                try {
                    updatingTotals = true;
                    for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                        Object quantityObj = itemsTableModel.getValueAt(i, 2);
                        Object priceObj = itemsTableModel.getValueAt(i, 3);

                        int quantity = parseInteger(quantityObj);
                        double price = parseDouble(priceObj);

                        double itemTotal = quantity * price;

                        // Only update if the value has changed
                        String currentTotal = (String) itemsTableModel.getValueAt(i, 4);
                        String newTotal = String.format("%.2f", itemTotal);
                        if (!newTotal.equals(currentTotal)) {
                            itemsTableModel.setValueAt(newTotal, i, 4);
                        }
                    }
                } finally {
                    updatingTotals = false;
                }
            });

        } finally {
            updatingTotals = false;
        }
    }

    // Helper methods for safe parsing
    private int parseInteger(Object obj) {
        if (obj instanceof Integer) {
            return (Integer) obj;
        }
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseDouble(Object obj) {
        if (obj instanceof Double) {
            return (Double) obj;
        }
        try {
            String str = obj.toString().replace(",", ".");
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void saveOrder() {
        try {
            // Validation
            if (itemsTableModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this,
                    "Add at least one product to the order",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // FIXED: Use DateUtils for date parsing
            Date orderDate;
            Date deliveryDate = null;
            try {
                orderDate = DateUtils.parseDate(dateField.getText(), dateFormat);
                String deliveryDateText = deliveryDateField.getText().trim();
                if (!deliveryDateText.isEmpty()) {
                    deliveryDate = DateUtils.parseDate(deliveryDateText, dateFormat);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Invalid date format. Use dd/MM/yyyy format",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (orderDate == null) {
                JOptionPane.showMessageDialog(this,
                    "Please enter a valid order date",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Calculate total
            double total = Double.parseDouble(
                totalLabel.getText().replace("Total: € ", "").replace(",", "."));

            String newStatus = (String)statusCombo.getSelectedItem();

            // Build list of stock items
            List<StockManager.StockItem> stockItems = new ArrayList<>();
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                String code = (String)itemsTableModel.getValueAt(i, 0);
                String productName = (String)itemsTableModel.getValueAt(i, 1);
                int quantity = parseInteger(itemsTableModel.getValueAt(i, 2));

                // Find product ID by code
                int productId = -1;
                for (Product p : productsCache.values()) {
                    if (p.getCode().equals(code)) {
                        productId = p.getId();
                        break;
                    }
                }
                if (productId != -1) {
                    stockItems.add(new StockManager.StockItem(productId, productName, quantity));
                }
            }

            Connection conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false);

            try {
                int orderId;

                if (order == null) {
                    // Insert new order
                    String orderQuery = """
                        INSERT INTO supplier_orders (
                            supplier_id, number, order_date, expected_delivery_date,
                            status, total, notes
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;

                    try (PreparedStatement pstmt = conn.prepareStatement(orderQuery, Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setInt(1, supplierId);
                        pstmt.setString(2, numberField.getText());
                        pstmt.setTimestamp(3, DateUtils.toSqlTimestamp(orderDate));
                        pstmt.setTimestamp(4, deliveryDate != null ? DateUtils.toSqlTimestamp(deliveryDate) : null);
                        pstmt.setString(5, newStatus);
                        pstmt.setDouble(6, total);
                        pstmt.setString(7, notesArea.getText().trim());
                        pstmt.executeUpdate();

                        try (ResultSet rs = pstmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                orderId = rs.getInt(1);
                            } else {
                                throw new SQLException("Failed to get order ID");
                            }
                        }
                    }

                    // Insert order details
                    insertOrderDetails(conn, orderId);

                    // Handle stock if Completed
                    if ("Completed".equals(newStatus)) {
                        StockManager.incrementStock(conn, stockItems, orderDate,
                            numberField.getText(), "SUPPLIER_ORDER");
                    }

                } else {
                    // Update existing order
                    orderId = order.getId();

                    String orderQuery = """
                        UPDATE supplier_orders SET
                            order_date = ?, expected_delivery_date = ?,
                            status = ?, total = ?, notes = ?
                        WHERE id = ?
                    """;

                    try (PreparedStatement pstmt = conn.prepareStatement(orderQuery)) {
                        pstmt.setTimestamp(1, DateUtils.toSqlTimestamp(orderDate));
                        pstmt.setTimestamp(2, deliveryDate != null ? DateUtils.toSqlTimestamp(deliveryDate) : null);
                        pstmt.setString(3, newStatus);
                        pstmt.setDouble(4, total);
                        pstmt.setString(5, notesArea.getText().trim());
                        pstmt.setInt(6, orderId);
                        pstmt.executeUpdate();
                    }

                    // Delete old details
                    String deleteDetailsQuery = "DELETE FROM supplier_order_details WHERE order_id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(deleteDetailsQuery)) {
                        pstmt.setInt(1, orderId);
                        pstmt.executeUpdate();
                    }

                    // Insert new details
                    insertOrderDetails(conn, orderId);

                    // Handle status change for stock
                    handleStatusChange(conn, orderId, previousStatus, newStatus, stockItems, orderDate);
                }

                conn.commit();
                orderSaved = true;
                dispose();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error saving the order: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void insertOrderDetails(Connection conn, int orderId) throws SQLException {
        String detailQuery = """
            INSERT INTO supplier_order_details (
                order_id, product_id, quantity, unit_price,
                total, notes
            ) VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(detailQuery)) {
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                String code = (String)itemsTableModel.getValueAt(i, 0);
                int productId = -1;
                for (Product p : productsCache.values()) {
                    if (p.getCode().equals(code)) {
                        productId = p.getId();
                        break;
                    }
                }
                if (productId == -1) continue;

                int quantity = parseInteger(itemsTableModel.getValueAt(i, 2));
                double unitPrice = parseDouble(itemsTableModel.getValueAt(i, 3));
                double total = parseDouble(itemsTableModel.getValueAt(i, 4));
                String notes = (String)itemsTableModel.getValueAt(i, 5);

                pstmt.setInt(1, orderId);
                pstmt.setInt(2, productId);
                pstmt.setInt(3, quantity);
                pstmt.setDouble(4, unitPrice);
                pstmt.setDouble(5, total);
                pstmt.setString(6, notes);
                pstmt.executeUpdate();
            }
        }
    }

    private void handleStatusChange(Connection conn, int orderId, String oldStatus, String newStatus,
                                    List<StockManager.StockItem> items, Date orderDate) throws SQLException {
        // Supplier orders only increment stock when marked as Completed
        // If changing FROM Completed to another status, we need to reverse the stock increment
        if ("Completed".equals(oldStatus) && !"Completed".equals(newStatus)) {
            // Reverse stock increment: decrement it back
            for (StockManager.StockItem item : items) {
                String query = "UPDATE products SET quantity = quantity - ? WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, item.getQuantity());
                    pstmt.setInt(2, item.getProductId());
                    pstmt.executeUpdate();
                }
            }
        }

        // If changing TO Completed from another status, increment stock
        if ("Completed".equals(newStatus) && !"Completed".equals(oldStatus)) {
            StockManager.incrementStock(conn, items, orderDate,
                numberField.getText(), "SUPPLIER_ORDER");
        }
    }

    public boolean isOrderSaved() {
        return orderSaved;
    }
}
