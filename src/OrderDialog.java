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
    private DatePickerField dateField;
    private JComboBox<String> statusCombo;
    private JComboBox<String> paymentStatusCombo;
    private JTextField paidAmountField;
    private JLabel paidAmountLabel;
    private JPanel paymentPanel;
    private JButton newCustomerButton;
    private JButton addItemButton;
    private JButton removeItemButton;
    private JTable itemsTable;
    private DefaultTableModel itemsTableModel;
    private JLabel totalLabel;
    private SimpleDateFormat dateFormat;
    private Map<Integer, Product> productsCache;
    private volatile boolean updatingTotals = false;
    private TableModelListener tableListener;
    private double currentTotal = 0.0; // Customer-facing total, VAT included
    private double currentTotalExVat = 0.0;
    private boolean paymentSelectionChanged;
    private boolean loadingPayment;
    private boolean linkedInvoiceLocked;
    private String previousStatus = null; // Track previous status for state changes
    
    // Constructor for JFrame parent
    public OrderDialog(JFrame parent, Order order) {
        super(parent, order == null ? "New Customer Order" : "Edit Customer Order", true);
        this.order = order;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        this.productsCache = new HashMap<>();
        
        setupWindow();
        initComponents();
        loadProducts();
        if (order != null) {
            loadOrderData();
        }
        initializeLinkedInvoiceLock();
        configureEditability();
    }
    
    // Constructor for JDialog parent
    public OrderDialog(JDialog parent, Order order) {
        super(parent, order == null ? "New Customer Order" : "Edit Customer Order", true);
        this.order = order;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        this.productsCache = new HashMap<>();
        
        setupWindow();
        initComponents();
        loadProducts();
        if (order != null) {
            loadOrderData();
        }
        initializeLinkedInvoiceLock();
        configureEditability();
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
        Dialogs.centerWindow(this);
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
        orderPanel.add(new JLabel("Customer *:"), gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2;
        selectCustomerButton = new JButton("Click to select customer...");
        selectCustomerButton.putClientProperty("i18n.literal", true);
        selectCustomerButton.setPreferredSize(new Dimension(300, 35));
        selectCustomerButton.setHorizontalAlignment(SwingConstants.LEFT);
        selectCustomerButton.addActionListener(e -> showCustomerSelectionDialog());
        orderPanel.add(selectCustomerButton, gbc);
        
        gbc.gridx = 3; gbc.gridwidth = 1;
        newCustomerButton = new JButton("New Customer");
        newCustomerButton.addActionListener(e -> createNewCustomer());
        orderPanel.add(newCustomerButton, gbc);
        
        // Date
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        orderPanel.add(new JLabel("Date:"), gbc);
        
        gbc.gridx = 1;
        dateField = new DatePickerField(dateFormat.format(new Date()));
        orderPanel.add(dateField, gbc);

        // Status
        gbc.gridx = 2;
        orderPanel.add(new JLabel("Status:"), gbc);

        gbc.gridx = 3;
        statusCombo = new JComboBox<>(new String[]{"New", "Reservation", "In Progress", "Completed", "Cancelled"});
        statusCombo.putClientProperty("i18n.values", true);
        orderPanel.add(statusCombo, gbc);

        JLabel orderStatusHelp = Dialogs.clickHelpLabel(I18n.tr("tooltip.order.statusFlow"));
        gbc.gridx = 4; gbc.gridy = 0; gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHEAST;
        orderPanel.add(orderStatusHelp, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;

        // Payment Status
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        orderPanel.add(new JLabel("Payment Status:"), gbc);

        gbc.gridx = 1;
        paymentStatusCombo = new JComboBox<>(new String[]{"Not Paid", "Partially Paid", "Paid"});
        paymentStatusCombo.putClientProperty("i18n.values", true);
        paymentStatusCombo.addActionListener(e -> {
            if (!loadingPayment) paymentSelectionChanged = true;
            updatePaymentAmountField();
        });
        orderPanel.add(paymentStatusCombo, gbc);

        // Paid Amount
        gbc.gridx = 2;
        paidAmountLabel = new JLabel("Paid Amount:");
        orderPanel.add(paidAmountLabel, gbc);

        gbc.gridx = 3;
        paidAmountField = new JTextField("0.00");
        paidAmountField.setEnabled(false);
        orderPanel.add(paidAmountField, gbc);

        // Products table
        String[] columns = {"ID", "Product", "Quantity *", "Unit Price incl. VAT", "Total incl. VAT", "VAT %"};
        itemsTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 2 && !isProtectedHistoricalOrder(); // Only quantity is editable
            }
        };
        itemsTable = new JTable(itemsTableModel);
        TableStyleUtil.applyCompact(itemsTable);
        
        // Hide ID column
        itemsTable.getColumnModel().getColumn(0).setMinWidth(0);
        itemsTable.getColumnModel().getColumn(0).setMaxWidth(0);
        itemsTable.getColumnModel().getColumn(0).setPreferredWidth(0);
        // VAT is snapshotted for accounting, but the order table keeps the customer view simple.
        itemsTable.getColumnModel().getColumn(5).setMinWidth(0);
        itemsTable.getColumnModel().getColumn(5).setMaxWidth(0);
        itemsTable.getColumnModel().getColumn(5).setPreferredWidth(0);
        
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
        addItemButton = new JButton("Add Product");
        removeItemButton = new JButton("Remove Product");
        
        addItemButton.addActionListener(e -> showProductSelectionDialog());
        removeItemButton.addActionListener(e -> removeSelectedProduct());
        
        tableButtonPanel.add(addItemButton);
        tableButtonPanel.add(removeItemButton);
        
        // Panel for total
        JPanel totalPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        totalLabel = new JLabel("Total: " + SettingsPanel.formatCurrency(0));
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
        
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(Dialogs.requiredLegendPanel(), BorderLayout.WEST);
        footer.add(buttonPanel, BorderLayout.EAST);

        mainPanel.add(orderPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(footer, BorderLayout.SOUTH);
        
        add(mainPanel);
    }
    
    private void initializeLinkedInvoiceLock() {
        linkedInvoiceLocked = order != null && hasLinkedInvoice(order.getId());
    }

    private boolean hasLinkedInvoice(int orderId) {
        try (PreparedStatement p = DatabaseManager.getInstance().getConnection().prepareStatement(
                "SELECT 1 FROM invoices WHERE order_id=? LIMIT 1")) {
            p.setInt(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                return r.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to verify linked invoice", e);
        }
    }

    private boolean isCompletedPaymentOnly() {
        return order != null && "Completed".equals(order.getStatus());
    }

    private boolean isPaymentOnlyOrder() {
        return order != null && !isReversedOrder()
            && (isCompletedPaymentOnly() || linkedInvoiceLocked);
    }

    private boolean isReversedOrder() {
        return order != null && "Reversed".equals(order.getStatus());
    }

    private boolean isProtectedHistoricalOrder() {
        return isPaymentOnlyOrder() || isReversedOrder();
    }

    private void configureEditability() {
        if (!isProtectedHistoricalOrder()) return;

        selectCustomerButton.setEnabled(false);
        newCustomerButton.setEnabled(false);
        dateField.setEnabled(false);
        statusCombo.setEnabled(false);
        addItemButton.setEnabled(false);
        removeItemButton.setEnabled(false);

        boolean paymentEditable = isPaymentOnlyOrder();
        paymentStatusCombo.setEnabled(paymentEditable);
        if (paymentEditable) {
            updatePaymentAmountField();
        } else {
            paidAmountField.setEnabled(false);
        }
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
            Dialogs.showMessageDialog(this,
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
                        rs.getDouble("sale_price_inc_vat"),
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

        // Load payment status
        loadingPayment = true;
        String paymentStatus = order.getPaymentStatus();
        if ("PAID".equals(paymentStatus)) {
            paymentStatusCombo.setSelectedItem("Paid");
        } else if ("PARTIALLY_PAID".equals(paymentStatus)) {
            paymentStatusCombo.setSelectedItem("Partially Paid");
        } else {
            paymentStatusCombo.setSelectedItem("Not Paid");
        }
        loadingPayment = false;
        paymentSelectionChanged = false;
        paidAmountField.setText(Numbers.moneyText(order.getPaidAmount()));
        updatePaymentAmountField();

        for (OrderItem item : order.getItems()) {
            Vector<Object> row = new Vector<>();
            row.add(item.getProductId());
            row.add(item.getProductName());
            row.add(item.getQuantity());
            row.add(item.getUnitPriceIncVat());
            row.add(Numbers.moneyText(item.getTotalIncVat()));
            row.add(item.getVatRate());
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
        ProductSelectionDialog dialog = new ProductSelectionDialog(this, true, true);
        dialog.setVisible(true);
        
        if (dialog.isProductSelected()) {
            Product product = dialog.getSelectedProduct();
            int quantity = dialog.getSelectedQuantity();
            double vatRate = dialog.getSelectedVatRate();
            
            // Check if product already exists in the table
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                int existingId = (int) itemsTableModel.getValueAt(i, 0);
                if (existingId == product.getId()) {
                    int choice = Dialogs.showConfirmDialog(this,
                        "This product is already in the order.\n" +
                        "Do you want to increase the quantity instead?",
                        "Product Already Added",
                        JOptionPane.YES_NO_OPTION);
                    
                    if (choice == JOptionPane.YES_OPTION) {
                        int currentQty = parseInteger(itemsTableModel.getValueAt(i, 2));
                        itemsTableModel.setValueAt(Math.addExact(currentQty, quantity), i, 2);
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
            row.add(product.getSalePriceIncVat());
            row.add(Numbers.moneyText(SalePrice.lineIncVat(quantity, product.getSalePriceIncVat())));
            row.add(vatRate);
            itemsTableModel.addRow(row);
            
            updateTotals();
        }
    }
    
    private void removeSelectedProduct() {
        int selectedRow = itemsTable.getSelectedRow();
        if (selectedRow != -1) {
            String productName = (String) itemsTableModel.getValueAt(selectedRow, 1);
            int result = Dialogs.showConfirmDialog(this,
                "Are you sure you want to remove " + productName + " from the order?",
                "Confirm Removal",
                JOptionPane.YES_NO_OPTION);
                
            if (result == JOptionPane.YES_OPTION) {
                itemsTableModel.removeRow(selectedRow);
                updateTotals();
            }
        } else {
            Dialogs.showMessageDialog(this,
                "Please select a product to remove",
                "No Selection", JOptionPane.WARNING_MESSAGE);
        }
    }
    
    private void updatePaymentAmountField() {
        String selectedPayment = (String) paymentStatusCombo.getSelectedItem();
        boolean required = "Partially Paid".equals(selectedPayment);
        paidAmountLabel.setText(required ? "Paid Amount *:" : "Paid Amount:");
        if (required) {
            paidAmountField.setEnabled(true);
        } else {
            paidAmountField.setEnabled(false);
            if ("Paid".equals(selectedPayment)) {
                paidAmountField.setText(Numbers.moneyText(order != null && !paymentSelectionChanged
                    ? order.getPaidAmount() : currentTotal));
            } else {
                paidAmountField.setText("0.00");
            }
        }
    }

    private synchronized void updateTotals() {
        if (updatingTotals) return;

        updatingTotals = true;
        try {
            double totalIncVat = 0;
            double totalExVat = 0;

            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                int quantity = parseInteger(itemsTableModel.getValueAt(i, 2));
                double unitPriceIncVat = parseDouble(itemsTableModel.getValueAt(i, 3));
                double vatRate = Numbers.vat(itemsTableModel.getValueAt(i, 5));

                double lineIncVat = SalePrice.lineIncVat(quantity, unitPriceIncVat);
                double lineExVat = SalePrice.lineExVat(quantity, unitPriceIncVat, vatRate);
                totalIncVat = Numbers.add(totalIncVat, lineIncVat);
                totalExVat = Numbers.add(totalExVat, lineExVat);

                String newTotal = Numbers.moneyText(lineIncVat);
                String currentTotalStr = String.valueOf(itemsTableModel.getValueAt(i, 4));
                if (!newTotal.equals(currentTotalStr)) {
                    itemsTableModel.setValueAt(newTotal, i, 4);
                }
            }

            currentTotal = totalIncVat;
            currentTotalExVat = totalExVat;
            totalLabel.setText(I18n.tr("Total incl. VAT") + ": " +
                SettingsPanel.formatCurrency(totalIncVat));

            if ("Paid".equals(paymentStatusCombo.getSelectedItem()) &&
                    (order == null || paymentSelectionChanged)) {
                paidAmountField.setText(Numbers.moneyText(totalIncVat));
            }

        } catch (IllegalArgumentException e) {
            totalLabel.setText(I18n.tr("Invalid numeric input"));
        } finally {
            updatingTotals = false;
        }
    }
    
    private int parseInteger(Object obj) { return Numbers.quantity(obj); }

    private double parseDouble(Object obj) { return Numbers.nonNegative(obj); }

    private void validateRows() {
        Numbers.commitEdits(itemsTable);
        java.util.Set<Integer> products = new java.util.HashSet<>();
        double validatedIncVat = 0;
        double validatedExVat = 0;
        for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
            int id = Numbers.positiveQuantity(itemsTableModel.getValueAt(i, 0));
            if (!products.add(id)) throw new IllegalArgumentException("Duplicate product in order");
            int quantity = Numbers.positiveQuantity(itemsTableModel.getValueAt(i, 2));
            double priceIncVat = Numbers.nonNegative(itemsTableModel.getValueAt(i, 3));
            double vatRate = Numbers.vat(itemsTableModel.getValueAt(i, 5));
            validatedIncVat = Numbers.add(validatedIncVat,
                SalePrice.lineIncVat(quantity, priceIncVat));
            validatedExVat = Numbers.add(validatedExVat,
                SalePrice.lineExVat(quantity, priceIncVat, vatRate));
        }
        currentTotal = validatedIncVat;
        currentTotalExVat = validatedExVat;
        updateTotals();
    }

    private void saveCompletedPaymentOnly() {
        Connection conn = null;
        try {
            String selected = String.valueOf(paymentStatusCombo.getSelectedItem());
            String paymentCode;
            double paidAmount = 0.0;

            if ("Paid".equals(selected)) {
                paymentCode = "PAID";
            } else if ("Partially Paid".equals(selected)) {
                paymentCode = "PARTIALLY_PAID";
                paidAmount = Numbers.money(Numbers.decimal(paidAmountField.getText()));
            } else {
                paymentCode = "NOT_PAID";
            }

            conn = DatabaseManager.getInstance().getConnection();
            DatabaseManager.beginTransaction(conn);
            try {
                OrdersPanel.updateOrderPayment(conn, order.getId(), paymentCode, paidAmount);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

            orderSaved = true;
            dispose();
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                I18n.tr("Error while updating payment") + ": " + e.getMessage(),
                I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveOrder() {
        try {
            if (isReversedOrder()) {
                Dialogs.showMessageDialog(this,
                    I18n.tr("This sale has already been reversed and is kept as historical data."),
                    I18n.tr("Historical Sale"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (isPaymentOnlyOrder()) {
                saveCompletedPaymentOnly();
                return;
            }
            validateRows();
            if (selectedCustomer == null) {
                Dialogs.showMessageDialog(this,
                    "Please select a customer",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (itemsTableModel.getRowCount() == 0) {
                Dialogs.showMessageDialog(this,
                    "Add at least one product to the order",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Date orderDate = DateUtils.parseDate(dateField.getText(), dateFormat);
            String newStatus = (String)statusCombo.getSelectedItem();

            // Get payment information
            String paymentStatusUI = (String) paymentStatusCombo.getSelectedItem();
            String paymentStatus;
            double paidAmount = 0.0;

            if ("Paid".equals(paymentStatusUI)) {
                paymentStatus = "PAID";
                paidAmount = order != null && !paymentSelectionChanged
                    ? order.getPaidAmount() : currentTotal;
            } else if ("Partially Paid".equals(paymentStatusUI)) {
                paymentStatus = "PARTIALLY_PAID";
                try {
                    java.math.BigDecimal enteredPaid=Numbers.decimal(paidAmountField.getText());
                    if (enteredPaid.signum() < 0) {
                        Dialogs.showMessageDialog(this,
                            "Paid amount cannot be negative",
                            "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    paidAmount = Numbers.money(enteredPaid);
                    if (paidAmount > currentTotal) {
                        int choice = Dialogs.showConfirmDialog(this,
                            I18n.tr("Paid amount exceeds order total. Continue?"),
                            "Warning", JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                        if (choice != JOptionPane.YES_OPTION) {
                            return;
                        }
                    }
                } catch (IllegalArgumentException ex) {
                    Dialogs.showMessageDialog(this,
                        "Invalid paid amount. Please enter a valid number.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                paymentStatus = "NOT_PAID";
                paidAmount = 0.0;
            }

            paymentStatus = PaymentAmounts.status(paidAmount, currentTotal);

            // Build list of stock items
            List<StockManager.StockItem> stockItems = new ArrayList<>();
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                int productId = (int)itemsTableModel.getValueAt(i, 0);
                String productName = (String)itemsTableModel.getValueAt(i, 1);
                int quantity = parseInteger(itemsTableModel.getValueAt(i, 2));
                stockItems.add(new StockManager.StockItem(productId, productName, quantity));
            }

            Connection conn = DatabaseManager.getInstance().getConnection();
            java.util.List<Integer> productIds = new java.util.ArrayList<>();
            for (StockManager.StockItem item : stockItems) productIds.add(item.getProductId());
            String missingCost = CostIntegrity.missingPurchaseCostMessage(conn, productIds);
            if (missingCost != null) {
                Dialogs.showMessageDialog(this, missingCost,
                    I18n.tr("Purchase Cost Required"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            DatabaseManager.beginTransaction(conn);
            try {
            if (order != null) {
                try (PreparedStatement p=conn.prepareStatement("SELECT 1 FROM invoices WHERE order_id=? LIMIT 1")) {
                    p.setInt(1,order.getId()); try (ResultSet r=p.executeQuery()) {
                        if (r.next()) throw new SQLException(I18n.tr("This order already has an invoice and cannot be modified."));
                    }
                }
            }
            // Reservation is the only state that does not touch warehouse availability.
            // New and In Progress reserve the full requested quantity; Completed consumes it.
            if ("New".equals(newStatus) || "In Progress".equals(newStatus) || "Completed".equals(newStatus)) {
                Map<String, StockManager.StockAvailability> insufficient =
                    StockManager.checkStockAvailability(
                        conn,
                        stockItems,
                        order != null ? order.getId() : null,
                        "ORDER"
                    );

                if (!insufficient.isEmpty()) {
                    StringBuilder message = new StringBuilder(
                        I18n.tr("Insufficient stock for the following products:"))
                        .append("\n\n");
                    for (Map.Entry<String, StockManager.StockAvailability> entry : insufficient.entrySet()) {
                        message.append("- ")
                               .append(entry.getKey())
                               .append(": ")
                               .append(entry.getValue().getFormattedMessage())
                               .append("\n");
                    }

                    message.append("\n").append(I18n.tr(
                        "With insufficient available stock, the order can only be saved as Reservation. Reservation does not change stock, reserved quantities, lots or FIFO."));
                    statusCombo.setSelectedItem("Reservation");

                    Dialogs.showMessageDialog(this,
                        message.toString(),
                        I18n.tr("Insufficient Stock"),
                        JOptionPane.WARNING_MESSAGE);
                    conn.rollback();
                    return;
                }
            }

                int orderId;
                Date completedDate = "Completed".equals(newStatus) ? new Date() : null;

                if (order == null) {
                    // New order
                    String orderQuery = """
                        INSERT INTO orders (customer_id, order_date, completed_date, status, total_ex_vat, total_inc_vat, payment_status, paid_amount)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(orderQuery, Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setInt(1, selectedCustomer.getId());
                        pstmt.setTimestamp(2, DateUtils.toSqlTimestamp(orderDate));
                        pstmt.setTimestamp(3, DateUtils.toSqlTimestamp(completedDate));
                        pstmt.setString(4, newStatus);
                        pstmt.setDouble(5, currentTotalExVat);
                        pstmt.setDouble(6, currentTotal);
                        pstmt.setString(7, paymentStatus);
                        pstmt.setDouble(8, paidAmount);
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
                    handleStockForNewStatus(conn, orderId, newStatus, stockItems, completedDate);

                } else {
                    // Update existing order
                    orderId = order.getId();
                    StockManager.deleteOrder(conn, orderId, null);

                    String orderQuery = """
                        UPDATE orders
                        SET customer_id = ?, order_date = ?, completed_date = ?, status = ?, total_ex_vat = ?, total_inc_vat = ?, payment_status = ?, paid_amount = ?
                        WHERE id = ?
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(orderQuery)) {
                        pstmt.setInt(1, selectedCustomer.getId());
                        pstmt.setTimestamp(2, DateUtils.toSqlTimestamp(orderDate));
                        pstmt.setTimestamp(3, DateUtils.toSqlTimestamp(completedDate));
                        pstmt.setString(4, newStatus);
                        pstmt.setDouble(5, currentTotalExVat);
                        pstmt.setDouble(6, currentTotal);
                        pstmt.setString(7, paymentStatus);
                        pstmt.setDouble(8, paidAmount);
                        pstmt.setInt(9, orderId);
                        pstmt.executeUpdate();
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
                    handleStockForNewStatus(conn, orderId, newStatus, stockItems, completedDate);
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

    private void insertOrderDetails(Connection conn, int orderId) throws SQLException {
        String detailQuery = """
            INSERT INTO order_details
                (order_id, product_id, quantity, sale_unit_price_ex_vat,
                 sale_unit_price_inc_vat, vat_rate)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement pstmt = conn.prepareStatement(detailQuery)) {
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                int quantity = parseInteger(itemsTableModel.getValueAt(i, 2));
                double incVat = parseDouble(itemsTableModel.getValueAt(i, 3));
                double vatRate = Numbers.vat(itemsTableModel.getValueAt(i, 5));
                double exVat = SalePrice.unitExVatForLine(quantity, incVat, vatRate);
                pstmt.setInt(1, orderId);
                pstmt.setInt(2, (int)itemsTableModel.getValueAt(i, 0));
                pstmt.setInt(3, quantity);
                pstmt.setDouble(4, exVat);
                pstmt.setDouble(5, incVat);
                pstmt.setDouble(6, vatRate);
                pstmt.executeUpdate();
            }
        }
    }

    private void handleStockForNewStatus(Connection conn, int orderId, String status,
                                         List<StockManager.StockItem> items, Date completedDate) throws SQLException {
        switch (status) {
            case "Reservation":
                // Commercial reservation only: no warehouse reservation, stock movement or FIFO effect.
                break;
            case "New":
            case "In Progress":
                // Fully covered order: reserve the entire requested quantity without changing physical stock.
                for (StockManager.StockItem item : items) {
                    StockManager.createOrUpdateReservation(conn, item.getProductId(),
                        "ORDER", orderId, item.getQuantity(), "Order #" + orderId);
                }
                break;
            case "Completed":
                // Complete reservation and decrement stock
                // First create reservation if items exist, then complete it
                for (StockManager.StockItem item : items) {
                    StockManager.createOrUpdateReservation(conn, item.getProductId(),
                        "ORDER", orderId, item.getQuantity(), "Order #" + orderId);
                }
                StockManager.completeReservationAndDecrementStock(conn, "ORDER", orderId,
                    completedDate, String.valueOf(orderId));
                break;
            case "Cancelled":
                // No stock action
                break;
        }
    }

    public boolean isOrderSaved() {
        return orderSaved;
    }
}

