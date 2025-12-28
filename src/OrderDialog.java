import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.event.TableModelListener;
import javax.swing.event.TableModelEvent;

public class OrderDialog extends JDialog {
    private Order order;
    private boolean orderSaved = false;
    private JButton selectCustomerButton;
    private Customer selectedCustomer;
    private JTextField dateField;
    private JComboBox<String> statusCombo;
    private JTable itemsTable;
    private DefaultTableModel itemsTableModel;
    private JLabel totalLabel;
    private SimpleDateFormat dateFormat;
    private Map<Integer, Product> productsCache;
    private volatile boolean updatingTotals = false;
    private TableModelListener tableListener;
    private double currentTotal = 0.0; // Track total instead of parsing from label
    private String previousStatus = null; // Track previous status for state changes
    
    // Constructor for JFrame parent
    public OrderDialog(JFrame parent, Order order) {
        super(parent, order == null ? "New Order" : "Edit Order", true);
        this.order = order;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        this.productsCache = new HashMap<>();
        
        setupWindow();
        initComponents();
        loadProducts();
        if (order != null) {
            loadOrderData();
        }
    }
    
    // Constructor for JDialog parent
    public OrderDialog(JDialog parent, Order order) {
        super(parent, order == null ? "New Order" : "Edit Order", true);
        this.order = order;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        this.productsCache = new HashMap<>();
        
        setupWindow();
        initComponents();
        loadProducts();
        if (order != null) {
            loadOrderData();
        }
    }
    
    @Override
    public void dispose() {
        if (tableListener != null && itemsTableModel != null) {
            itemsTableModel.removeTableModelListener(tableListener);
        }
        super.dispose();
    }
    
    private void setupWindow() {
        setSize(800, 600);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(10, 10));
    }
    
    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top panel for order data
        JPanel orderPanel = new JPanel(new GridBagLayout());
        orderPanel.setBorder(BorderFactory.createTitledBorder("Order Information"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Customer Selection
        gbc.gridx = 0; gbc.gridy = 0;
        orderPanel.add(new JLabel("* Customer:"), gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2;
        selectCustomerButton = new JButton("Click to select customer...");
        selectCustomerButton.setPreferredSize(new Dimension(300, 35));
        selectCustomerButton.setHorizontalAlignment(SwingConstants.LEFT);
        selectCustomerButton.addActionListener(e -> showCustomerSelectionDialog());
        orderPanel.add(selectCustomerButton, gbc);
        
        gbc.gridx = 3; gbc.gridwidth = 1;
        JButton newCustomerButton = new JButton("New Customer");
        newCustomerButton.addActionListener(e -> createNewCustomer());
        orderPanel.add(newCustomerButton, gbc);
        
        // Date
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        orderPanel.add(new JLabel("Date:"), gbc);
        
        gbc.gridx = 1;
        dateField = new JTextField(dateFormat.format(new Date()));
        orderPanel.add(dateField, gbc);

        // Status
        gbc.gridx = 2;
        orderPanel.add(new JLabel("Status:"), gbc);

        gbc.gridx = 3;
        statusCombo = new JComboBox<>(new String[]{"New", "In Progress", "Completed", "Cancelled"});
        orderPanel.add(statusCombo, gbc);
        
        // Products table
        String[] columns = {"ID", "Product", "Quantity", "Unit Price", "Total"};
        itemsTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 2; // Only quantity is editable
            }
        };
        itemsTable = new JTable(itemsTableModel);
        
        // Hide ID column
        itemsTable.getColumnModel().getColumn(0).setMinWidth(0);
        itemsTable.getColumnModel().getColumn(0).setMaxWidth(0);
        itemsTable.getColumnModel().getColumn(0).setPreferredWidth(0);
        
        itemsTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        itemsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        itemsTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        itemsTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        
        // Store listener reference and improve thread safety
        tableListener = new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (e.getType() == TableModelEvent.UPDATE && 
                    e.getColumn() == 2 && !updatingTotals) {
                    synchronized (OrderDialog.this) {
                        if (!updatingTotals) {
                            SwingUtilities.invokeLater(() -> updateTotals());
                        }
                    }
                }
            }
        };
        itemsTableModel.addTableModelListener(tableListener);
        
        // Panel for table buttons
        JPanel tableButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addItemButton = new JButton("Add Product");
        JButton removeItemButton = new JButton("Remove Product");
        
        addItemButton.addActionListener(e -> showProductSelectionDialog());
        removeItemButton.addActionListener(e -> removeSelectedProduct());
        
        tableButtonPanel.add(addItemButton);
        tableButtonPanel.add(removeItemButton);
        
        // Panel for total
        JPanel totalPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        totalLabel = new JLabel("Total: € 0.00");
        totalLabel.setFont(new Font(totalLabel.getFont().getName(), Font.BOLD, 14));
        totalPanel.add(totalLabel);
        
        // Panel for main buttons
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
    
    private void showCustomerSelectionDialog() {
        CustomerSelectionDialog dialog = new CustomerSelectionDialog(this);
        dialog.setVisible(true);
        
        if (dialog.isCustomerSelected()) {
            selectedCustomer = dialog.getSelectedCustomer();
            updateCustomerButton();
        }
    }
    
    private void updateCustomerButton() {
        if (selectedCustomer != null) {
            String buttonText = String.format("%s %s",
                selectedCustomer.getFirstName(),
                selectedCustomer.getLastName());

            if (selectedCustomer.getEmail() != null && !selectedCustomer.getEmail().isEmpty()) {
                buttonText += " (" + selectedCustomer.getEmail() + ")";
            }

            selectCustomerButton.setText(buttonText);
            selectCustomerButton.setToolTipText("Customer: " + buttonText);
        }
    }
    
    private void createNewCustomer() {
        CustomerDialog dialog = new CustomerDialog(this, null);
        dialog.setVisible(true);
        if (dialog.isCustomerSaved()) {
            JOptionPane.showMessageDialog(this,
                "Customer created successfully. Please select it from the list.",
                "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void loadProducts() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = "SELECT * FROM products ORDER BY name";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    Product product = new Product(
                        rs.getInt("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getDouble("price"),
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
        // Load customer data
        if (order.getCustomerId() > 0) {
            try {
                selectedCustomer = loadCustomerById(order.getCustomerId());
                if (selectedCustomer != null) {
                    updateCustomerButton();
                }
            } catch (SQLException e) {
                e.printStackTrace();
                selectCustomerButton.setText(order.getCustomerName());
            }
        }

        dateField.setText(DateUtils.formatDate(order.getOrderDate()));
        statusCombo.setSelectedItem(order.getStatus());
        previousStatus = order.getStatus(); // Store current status as previous

        for (OrderItem item : order.getItems()) {
            Vector<Object> row = new Vector<>();
            row.add(item.getProductId());
            row.add(item.getProductName());
            row.add(item.getQuantity());
            row.add(String.format("%.2f", item.getUnitPrice()));
            row.add(String.format("%.2f", item.getTotal()));
            itemsTableModel.addRow(row);
        }

        updateTotals();
    }
    
    private Customer loadCustomerById(int customerId) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        String query = "SELECT * FROM customers WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, customerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Customer(
                        rs.getInt("id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("address")
                    );
                }
            }
        }
        return null;
    }
    
    private void showProductSelectionDialog() {
        ProductSelectionDialog dialog = new ProductSelectionDialog(this);
        dialog.setVisible(true);
        
        if (dialog.isProductSelected()) {
            Product product = dialog.getSelectedProduct();
            int quantity = dialog.getSelectedQuantity();
            
            // Check if product already exists in the table
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                int existingId = (int) itemsTableModel.getValueAt(i, 0);
                if (existingId == product.getId()) {
                    int choice = JOptionPane.showConfirmDialog(this,
                        "This product is already in the order.\n" +
                        "Do you want to increase the quantity instead?",
                        "Product Already Added",
                        JOptionPane.YES_NO_OPTION);
                    
                    if (choice == JOptionPane.YES_OPTION) {
                        int currentQty = parseInteger(itemsTableModel.getValueAt(i, 2));
                        itemsTableModel.setValueAt(currentQty + quantity, i, 2);
                        updateTotals();
                    }
                    return;
                }
            }
            
            // Add new product to table
            Vector<Object> row = new Vector<>();
            row.add(product.getId());
            row.add(product.getName());
            row.add(quantity);
            row.add(String.format("%.2f", product.getPrice()));
            row.add(String.format("%.2f", quantity * product.getPrice()));
            itemsTableModel.addRow(row);
            
            updateTotals();
        }
    }
    
    private void removeSelectedProduct() {
        int selectedRow = itemsTable.getSelectedRow();
        if (selectedRow != -1) {
            String productName = (String) itemsTableModel.getValueAt(selectedRow, 1);
            int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to remove " + productName + " from the order?",
                "Confirm Removal",
                JOptionPane.YES_NO_OPTION);
                
            if (result == JOptionPane.YES_OPTION) {
                itemsTableModel.removeRow(selectedRow);
                updateTotals();
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "Please select a product to remove",
                "No Selection", JOptionPane.WARNING_MESSAGE);
        }
    }
    
    private synchronized void updateTotals() {
        if (updatingTotals) return;

        updatingTotals = true;
        try {
            double total = 0;

            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                Object quantityObj = itemsTableModel.getValueAt(i, 2);
                Object priceObj = itemsTableModel.getValueAt(i, 3);

                int quantity = parseInteger(quantityObj);
                double price = parseDouble(priceObj);

                double itemTotal = quantity * price;
                total += itemTotal;

                // Update row total
                String newTotal = String.format("%.2f", itemTotal);
                String currentTotalStr = (String) itemsTableModel.getValueAt(i, 4);
                if (!newTotal.equals(currentTotalStr)) {
                    itemsTableModel.setValueAt(newTotal, i, 4);
                }
            }

            currentTotal = total; // Store total value
            totalLabel.setText(String.format("Total: € %.2f", total));

        } finally {
            updatingTotals = false;
        }
    }
    
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
            if (selectedCustomer == null) {
                JOptionPane.showMessageDialog(this,
                    "Please select a customer",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (itemsTableModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this,
                    "Add at least one product to the order",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Date orderDate = DateUtils.parseDate(dateField.getText(), dateFormat);
            String newStatus = (String)statusCombo.getSelectedItem();

            // Build list of stock items
            List<StockManager.StockItem> stockItems = new ArrayList<>();
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                int productId = (int)itemsTableModel.getValueAt(i, 0);
                String productName = (String)itemsTableModel.getValueAt(i, 1);
                int quantity = parseInteger(itemsTableModel.getValueAt(i, 2));
                stockItems.add(new StockManager.StockItem(productId, productName, quantity));
            }

            // Check stock availability for In Progress and Completed states
            if ("In Progress".equals(newStatus) || "Completed".equals(newStatus)) {
                Map<String, StockManager.StockAvailability> insufficient =
                    StockManager.checkStockAvailability(
                        DatabaseManager.getInstance().getConnection(),
                        stockItems,
                        order != null ? order.getId() : null,
                        "ORDER"
                    );

                if (!insufficient.isEmpty()) {
                    StringBuilder message = new StringBuilder("Insufficient stock for the following products:\n\n");
                    for (Map.Entry<String, StockManager.StockAvailability> entry : insufficient.entrySet()) {
                        message.append(String.format("- %s: %s\n",
                            entry.getKey(), entry.getValue().getFormattedMessage()));
                    }
                    message.append("\nDo you want to proceed anyway?\nThis will result in negative stock.");

                    int choice = JOptionPane.showConfirmDialog(this,
                        message.toString(),
                        "Insufficient Stock",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                    if (choice != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
            }

            Connection conn = DatabaseManager.getInstance().getConnection();
            conn.setAutoCommit(false);
            try {
                int orderId;

                if (order == null) {
                    // New order
                    String orderQuery = """
                        INSERT INTO orders (customer_id, order_date, status, total)
                        VALUES (?, ?, ?, ?)
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(orderQuery, Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setInt(1, selectedCustomer.getId());
                        pstmt.setTimestamp(2, DateUtils.toSqlTimestamp(orderDate));
                        pstmt.setString(3, newStatus);
                        pstmt.setDouble(4, currentTotal);
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

                    // Handle stock based on status
                    handleStockForNewStatus(conn, orderId, newStatus, stockItems, orderDate);

                } else {
                    // Update existing order
                    orderId = order.getId();

                    String orderQuery = """
                        UPDATE orders
                        SET customer_id = ?, order_date = ?, status = ?, total = ?
                        WHERE id = ?
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(orderQuery)) {
                        pstmt.setInt(1, selectedCustomer.getId());
                        pstmt.setTimestamp(2, DateUtils.toSqlTimestamp(orderDate));
                        pstmt.setString(3, newStatus);
                        pstmt.setDouble(4, currentTotal);
                        pstmt.setInt(5, orderId);
                        pstmt.executeUpdate();
                    }

                    // If order was "In Progress", cancel old reservations before changing details
                    // This ensures reservations match the NEW quantities, not old ones
                    if ("In Progress".equals(previousStatus)) {
                        StockManager.cancelReservation(conn, "ORDER", orderId);
                    }

                    // Delete old details
                    String deleteDetailsQuery = "DELETE FROM order_details WHERE order_id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(deleteDetailsQuery)) {
                        pstmt.setInt(1, orderId);
                        pstmt.executeUpdate();
                    }

                    // Insert new details
                    insertOrderDetails(conn, orderId);

                    // Handle status change (now reservations are clean if they existed)
                    handleStatusChange(conn, orderId, previousStatus, newStatus, stockItems, orderDate);
                }

                conn.commit();
                orderSaved = true;
                dispose();

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error saving the order: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void insertOrderDetails(Connection conn, int orderId) throws SQLException {
        String detailQuery = """
            INSERT INTO order_details (order_id, product_id, quantity, unit_price)
            VALUES (?, ?, ?, ?)
        """;
        try (PreparedStatement pstmt = conn.prepareStatement(detailQuery)) {
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                pstmt.setInt(1, orderId);
                pstmt.setInt(2, (int)itemsTableModel.getValueAt(i, 0));
                pstmt.setInt(3, parseInteger(itemsTableModel.getValueAt(i, 2)));
                pstmt.setDouble(4, parseDouble(itemsTableModel.getValueAt(i, 3)));
                pstmt.executeUpdate();
            }
        }
    }

    private void handleStockForNewStatus(Connection conn, int orderId, String status,
                                         List<StockManager.StockItem> items, Date orderDate) throws SQLException {
        switch (status) {
            case "New":
                // No stock action
                break;
            case "In Progress":
                // Create reservations
                for (StockManager.StockItem item : items) {
                    StockManager.createOrUpdateReservation(conn, item.getProductId(),
                        "ORDER", orderId, item.getQuantity(), "Order #" + orderId);
                }
                break;
            case "Completed":
                // Decrement stock directly (no prior reservation)
                StockManager.decrementStockDirectly(conn, items, orderDate,
                    String.valueOf(orderId), "ORDER");
                break;
            case "Cancelled":
                // No stock action
                break;
        }
    }

    private void handleStatusChange(Connection conn, int orderId, String oldStatus, String newStatus,
                                    List<StockManager.StockItem> items, Date orderDate) throws SQLException {
        // Note: If oldStatus was "In Progress", reservations were already cancelled
        // before updating details, so we don't need to cancel them here

        if ("Completed".equals(oldStatus) && !"Completed".equals(newStatus)) {
            // Restore stock
            StockManager.restoreStockFromDocument(conn, orderId, "ORDER");
        }

        // Handle transition to new status
        switch (newStatus) {
            case "New":
                // No action needed
                break;
            case "In Progress":
                // Create new reservations with current quantities
                for (StockManager.StockItem item : items) {
                    StockManager.createOrUpdateReservation(conn, item.getProductId(),
                        "ORDER", orderId, item.getQuantity(), "Order #" + orderId);
                }
                break;
            case "Completed":
                // Always decrement stock directly (reservations were cancelled if they existed)
                StockManager.decrementStockDirectly(conn, items, orderDate,
                    String.valueOf(orderId), "ORDER");
                break;
            case "Cancelled":
                // No action needed (reservations already cancelled if they existed)
                break;
        }
    }

    public boolean isOrderSaved() {
        return orderSaved;
    }
}