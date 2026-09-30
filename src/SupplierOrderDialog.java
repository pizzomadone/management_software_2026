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
    private DatePickerField dateField;
    private DatePickerField deliveryDateField;
    private JComboBox<String> statusCombo;
    private JTextArea notesArea;
    private JTable itemsTable;
    private DefaultTableModel itemsTableModel;
    private JLabel totalLabel;
    private SimpleDateFormat dateFormat;
    private Map<Integer, Product> productsCache;
    private boolean updatingTotals = false; // Flag to prevent recursion

    public SupplierOrderDialog(Window parent, int supplierId, String supplierName, SupplierOrder order) {
        super(parent,
            I18n.tr(order == null ? "New Supplier Order" : "Edit Supplier Order"),
            Dialog.ModalityType.APPLICATION_MODAL);
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
        
        setLayout(new BorderLayout(3, 3));
    }

    private void initComponents() {
        // Main panel with padding
        JPanel mainPanel = new JPanel(new BorderLayout(3, 3));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        // Top panel for order data
        JPanel orderPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 3, 2, 3);
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
        gbc.weightx = 1.0;

        JTextField supplierField = new JTextField(supplierName, 24);
        supplierField.setEditable(false);
        Dimension supplierSize = supplierField.getPreferredSize();
        supplierField.setPreferredSize(new Dimension(260, supplierSize.height));
        supplierField.setMinimumSize(new Dimension(220, supplierSize.height));
        orderPanel.add(supplierField, gbc);
        gbc.weightx = 0.0;

        // Order date
        gbc.gridx = 0; gbc.gridy = 1;
        orderPanel.add(new JLabel("Order Date:"), gbc);

        gbc.gridx = 1;
        dateField = new DatePickerField(10);
        dateField.setText(DateUtils.formatDate(new Date(), dateFormat));
        orderPanel.add(dateField, gbc);

        // Delivery date
        gbc.gridx = 2;
        orderPanel.add(new JLabel("Delivery Date:"), gbc);

        gbc.gridx = 3;
        gbc.weightx = 1.0;
        deliveryDateField = new DatePickerField(12, true);
        Dimension deliverySize = deliveryDateField.getPreferredSize();
        deliveryDateField.setPreferredSize(new Dimension(150, deliverySize.height));
        deliveryDateField.setMinimumSize(new Dimension(135, deliverySize.height));
        orderPanel.add(deliveryDateField, gbc);
        gbc.weightx = 0.0;

        // Status
        gbc.gridx = 0; gbc.gridy = 2;
        orderPanel.add(new JLabel("Status:"), gbc);

        gbc.gridx = 1;
        statusCombo = new JComboBox<>(new String[]{"Draft", "Confirmed", "In Transit", "Completed", "Cancelled"});
        statusCombo.putClientProperty("i18n.values", true);
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
        String[] columns = {"Code", "Product", "Quantity *", "Unit Price", "Total", "Notes", "Product ID"};
        itemsTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 2 || column == 3 || column == 5; // Quantity, agreed price and notes.
            }
        };
        itemsTable = new JTable(itemsTableModel);
        TableStyleUtil.applyCompact(itemsTable);
        itemsTable.setToolTipText(I18n.tr("Unit prices start from the supplier's current product price and can be edited for discounts or negotiated terms."));
        itemsTable.removeColumn(itemsTable.getColumnModel().getColumn(6));

        // FIXED: Improved listener to prevent infinite recursion
        itemsTableModel.addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE &&
                (e.getColumn() == 2 || e.getColumn() == 3) && !updatingTotals) {
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
        JButton reprice = new JButton("Refresh Supplier Prices");
        reprice.setToolTipText(I18n.tr("Replace order prices with the current prices saved for this supplier."));
        reprice.addActionListener(e -> refreshSupplierPrices());
        tableButtonPanel.add(reprice);

        // Total panel
        JPanel totalPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        totalLabel = new JLabel("Total: " + SettingsPanel.formatCurrency(0));
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

        JPanel footer = new JPanel(new BorderLayout());
        footer.add(Dialogs.requiredLegendPanel(), BorderLayout.WEST);
        footer.add(buttonPanel, BorderLayout.EAST);

        mainPanel.add(orderPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(footer, BorderLayout.SOUTH);

        add(mainPanel);
        pack();
        centerOnOwner();
    }

    private void centerOnOwner() {
        Window owner = getOwner();
        Dialogs.centerWindow(this);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) centerOnOwner();
        super.setVisible(visible);
    }

    private void generateOrderNumber() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            numberField.setText(DocumentNumbers.nextSupplierNumber(conn, new Date()));
        } catch (SQLException e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error generating order number: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadProducts() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            productsCache.clear();
            String query = """
                SELECT p.*, ps.purchase_price
                FROM product_suppliers ps
                JOIN products p ON p.id=ps.product_id
                WHERE ps.supplier_id=? AND p.active=1
                ORDER BY p.name,p.code
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
                        rs.getDouble("purchase_price"),
                        rs.getInt("quantity")
                    );
                    productsCache.put(product.getId(), product);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
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
            row.add(item.getUnitPrice());
            row.add(Numbers.moneyText(item.getTotal()));
            row.add(item.getNotes());
            row.add(item.getProductId());
            itemsTableModel.addRow(row);
        }

        updateTotals();
    }

    private void showAddProductDialog() {
        JDialog dialog = new JDialog(this, I18n.tr("Add Product"), true);
        dialog.setLayout(new BorderLayout(8, 8));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Product
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Product *:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JComboBox<ProductDisplay> productCombo = new JComboBox<>();
        for (Product product : productsCache.values()) {
            productCombo.addItem(new ProductDisplay(product));
        }
        productCombo.setPreferredSize(new Dimension(430, productCombo.getPreferredSize().height));
        productCombo.setMaximumSize(productCombo.getPreferredSize());
        panel.add(productCombo, gbc);

        // Quantity
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Quantity *:"), gbc);

        gbc.gridx = 1;
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(1, 1, 9999, 1);
        JSpinner quantitySpinner = new JSpinner(spinnerModel);
        Dimension spinnerSize = quantitySpinner.getPreferredSize();
        quantitySpinner.setPreferredSize(new Dimension(85, spinnerSize.height));
        quantitySpinner.setMinimumSize(quantitySpinner.getPreferredSize());
        panel.add(quantitySpinner, gbc);

        // Notes
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel("Notes:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        JTextArea noteArea = new JTextArea(3, 30);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        JScrollPane noteScroll = new JScrollPane(noteArea);
        noteScroll.setPreferredSize(new Dimension(430, 76));
        panel.add(noteScroll, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton addButton = new JButton("Add");
        JButton cancelButton = new JButton("Cancel");

        addButton.addActionListener(e -> {
            ProductDisplay selectedProduct = (ProductDisplay)productCombo.getSelectedItem();
            if (selectedProduct == null) {
                Dialogs.showMessageDialog(dialog,
                    "Please select a product",
                    "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int quantity; double quotedPrice;
            try {
                quantitySpinner.commitEdit();
                quantity = Numbers.positiveQuantity(quantitySpinner.getValue());
                quotedPrice = SupplierPricing.price(
                    DatabaseManager.getInstance().getConnection(),
                    supplierId,
                    selectedProduct.getProduct().getId());
            } catch (Exception ex) {
                Dialogs.showMessageDialog(dialog, ex.getMessage(),
                    "Invalid product", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String note = noteArea.getText().trim();

            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                String code = (String)itemsTableModel.getValueAt(i, 0);
                if (code.equals(selectedProduct.getProduct().getCode())) {
                    Dialogs.showMessageDialog(dialog,
                        "This product is already in the order",
                        "Warning", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            Vector<Object> row = new Vector<>();
            row.add(selectedProduct.getProduct().getCode());
            row.add(selectedProduct.getProduct().getName());
            row.add(quantity);
            row.add(quotedPrice);
            row.add(Numbers.moneyText(Numbers.lineTotal(quantity, quotedPrice)));
            row.add(note);
            row.add(selectedProduct.getProduct().getId());
            itemsTableModel.addRow(row);

            updateTotals();
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(addButton);
        buttonPanel.add(cancelButton);

        JPanel dialogFooter = new JPanel(new BorderLayout());
        dialogFooter.add(Dialogs.requiredLegendPanel(), BorderLayout.WEST);
        dialogFooter.add(buttonPanel, BorderLayout.EAST);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(dialogFooter, BorderLayout.SOUTH);
        I18n.localize(dialog);
        dialog.pack();
        dialog.setResizable(false);
        Dialogs.centerWindow(dialog);
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
            return String.format("%s - %s (%s)",
                product.getCode(), product.getName(), SettingsPanel.formatCurrency(product.getPrice()));
        }
    }

    private void removeSelectedProduct() {
        int selectedRow = itemsTable.getSelectedRow();
        if (selectedRow != -1) {
            itemsTableModel.removeRow(selectedRow);
            updateTotals();
        }
    }

    private void refreshSupplierPrices() {
        refreshSupplierPrices(true);
    }

    private void refreshSupplierPrices(boolean askConfirmation) {
        try {
            Numbers.commitEdits(itemsTable);
            if (askConfirmation && Dialogs.showConfirmDialog(
                    this,
                    I18n.tr("Replace all entered unit prices with the current supplier prices?"),
                    I18n.tr("Refresh Supplier Prices"),
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }

            Connection c = DatabaseManager.getInstance().getConnection();
            double[] prices = new double[itemsTableModel.getRowCount()];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = SupplierPricing.price(
                    c, supplierId, productIdForRow(c, i));
            }
            for (int i = 0; i < prices.length; i++) {
                itemsTableModel.setValueAt(prices[i], i, 3);
            }
            updateTotals();
        } catch (Exception e) {
            Dialogs.showMessageDialog(
                this, I18n.tr(e.getMessage()),
                I18n.tr("Unable to refresh supplier prices"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateTotals() {
        if(updatingTotals)return;
        updatingTotals=true;
        try {
            double total=0;
            for(int i=0;i<itemsTableModel.getRowCount();i++) {
                double line=Numbers.lineTotal(parseInteger(itemsTableModel.getValueAt(i,2)),parseDouble(itemsTableModel.getValueAt(i,3)));
                total=Numbers.add(total,line);
                itemsTableModel.setValueAt(Numbers.moneyText(line),i,4);
            }
            totalLabel.setText("Total: "+SettingsPanel.formatCurrency(total));
        } catch(IllegalArgumentException e) {totalLabel.setText("Invalid quantity or price");}
        finally {updatingTotals=false;}
    }

    // Helper methods for safe parsing
    private int parseInteger(Object value) { return Numbers.positiveQuantity(value); }
    private double parseDouble(Object value) { return Numbers.nonNegative(value); }

    private int productIdForRow(Connection c, int row) throws SQLException {
        if (itemsTableModel.getColumnCount()>6 && itemsTableModel.getValueAt(row,6)!=null)
            return Numbers.positiveQuantity(itemsTableModel.getValueAt(row,6));
        try (PreparedStatement p = c.prepareStatement("SELECT id FROM products WHERE code=?")) {
            p.setString(1, String.valueOf(itemsTableModel.getValueAt(row,0)));
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Product no longer exists: " + itemsTableModel.getValueAt(row,0));
                return r.getInt(1);
            }
        }
    }

    private void saveOrder() {
        try {
            Numbers.commitEdits(itemsTable);
            // Validation
            if (itemsTableModel.getRowCount() == 0) {
                Dialogs.showMessageDialog(this,
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
                Dialogs.showMessageDialog(this,
                    I18n.tr("Invalid date format. Use dd/MM/yyyy format"),
                    I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (orderDate == null) {
                Dialogs.showMessageDialog(this,
                    I18n.tr("Please enter a valid order date"),
                    I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            // A delivery date may be the same as or later than the order date.
            // Only a delivery date before the order date is invalid.
            if (deliveryDate != null && deliveryDate.before(orderDate)) {
                Dialogs.showMessageDialog(this,
                    I18n.tr("Delivery date cannot be before order date"),
                    I18n.tr("Invalid date"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Calculate total
            double total = 0.0;
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                total = Numbers.add(total, Numbers.lineTotal(parseInteger(itemsTableModel.getValueAt(i, 2)),
                    parseDouble(itemsTableModel.getValueAt(i, 3))));
            }

            String newStatus = (String)statusCombo.getSelectedItem();

            // Build list of stock items
            List<StockManager.StockItem> stockItems = new ArrayList<>();
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                String code = (String)itemsTableModel.getValueAt(i, 0);
                String productName = (String)itemsTableModel.getValueAt(i, 1);
                int quantity = parseInteger(itemsTableModel.getValueAt(i, 2));

                int productId = productIdForRow(DatabaseManager.getInstance().getConnection(), i);
                stockItems.add(new StockManager.StockItem(productId, productName, quantity));
            }

            Connection conn = DatabaseManager.getInstance().getConnection();
            DatabaseManager.beginTransaction(conn);

            try {
                int orderId;

                if (order == null) {
                    numberField.setText(DocumentNumbers.nextSupplierNumber(conn, orderDate));
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
                        InventoryLotManager.receiveSupplierOrder(
                            conn, orderId, orderDate, numberField.getText());
                    }

                } else {
                    // Update existing order
                    orderId = order.getId();
                    List<StockManager.StockItem> oldStockItems = loadStoredStockItems(conn, orderId);

                    try (PreparedStatement p = conn.prepareStatement("SELECT status FROM supplier_orders WHERE id=?")) {
                        p.setInt(1, orderId);
                        try (ResultSet r = p.executeQuery()) {
                            if (!r.next()) throw new SQLException("Supplier order no longer exists");
                            previousStatus = r.getString(1);
                        }
                    }
                    if ("Completed".equals(previousStatus)) {
                        InventoryLotManager.reverseSupplierOrder(conn, orderId, order.getNumber());
                    }

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

                    if ("Completed".equals(newStatus)) {
                        InventoryLotManager.receiveSupplierOrder(
                            conn, orderId, orderDate, numberField.getText());
                    }
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
            Dialogs.showMessageDialog(this,
                "Error saving the order: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<StockManager.StockItem> loadStoredStockItems(Connection conn, int orderId)
            throws SQLException {
        List<StockManager.StockItem> items = new ArrayList<>();
        String query = """
            SELECT d.product_id, d.quantity, COALESCE(p.name, '') AS product_name
            FROM supplier_order_details d
            LEFT JOIN products p ON p.id = d.product_id
            WHERE d.order_id = ?
        """;
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, orderId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    items.add(new StockManager.StockItem(rs.getInt("product_id"),
                        rs.getString("product_name"), rs.getInt("quantity")));
                }
            }
        }
        return items;
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
                int productId = productIdForRow(conn, i);

                int quantity = parseInteger(itemsTableModel.getValueAt(i, 2));
                double unitPrice = parseDouble(itemsTableModel.getValueAt(i, 3));
                double total = Numbers.lineTotal(quantity, unitPrice);
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

    public boolean isOrderSaved() {
        return orderSaved;
    }
}
