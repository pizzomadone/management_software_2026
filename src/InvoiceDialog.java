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
import java.text.ParseException;
import java.util.Date;

public class InvoiceDialog extends JDialog {
    private Invoice invoice;
    private boolean invoiceSaved = false;
    private JTextField numberField;
    private JTextField dateField;
    private JButton selectCustomerButton;
    private Customer selectedCustomer;
    private JComboBox<String> statusCombo;
    private JTable itemsTable;
    private DefaultTableModel itemsTableModel;
    private JLabel taxableAmountLabel;
    private JLabel vatLabel;
    private JLabel totalLabel;
    private SimpleDateFormat dateFormat;
    private Map<Integer, Product> productsCache;
    private volatile boolean isUpdatingTotals = false;
    private double currentTaxableAmount = 0.0;
    private double currentVat = 0.0;
    private double currentTotal = 0.0;
    private String previousStatus = null;

    // Constructor for JFrame parent
    public InvoiceDialog(JFrame parent, Invoice invoice) {
        super(parent, invoice == null ? "New Invoice" : "Modify Invoice", true);
        this.invoice = invoice;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        this.productsCache = new HashMap<>();

        setupWindow();
        initComponents();
        loadProducts();
        if (invoice != null) {
            loadInvoiceData();
        } else {
            setupNewInvoice();
        }
    }

    // Constructor for JDialog parent
    public InvoiceDialog(JDialog parent, Invoice invoice) {
        super(parent, invoice == null ? "New Invoice" : "Modify Invoice", true);
        this.invoice = invoice;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        this.productsCache = new HashMap<>();

        setupWindow();
        initComponents();
        loadProducts();
        if (invoice != null) {
            loadInvoiceData();
        } else {
            setupNewInvoice();
        }
    }

    private void setupWindow() {
        setSize(1000, 750);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(10, 10));
    }

    private void initComponents() {
        // Main panel with padding
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel for invoice data
        JPanel invoicePanel = new JPanel(new GridBagLayout());
        invoicePanel.setBorder(BorderFactory.createTitledBorder("Invoice Information"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Invoice Number
        gbc.gridx = 0; gbc.gridy = 0;
        invoicePanel.add(new JLabel("Number:"), gbc);

        gbc.gridx = 1;
        numberField = new JTextField(15);
        numberField.setEditable(false);
        numberField.setBackground(Color.LIGHT_GRAY);
        invoicePanel.add(numberField, gbc);

        // Date
        gbc.gridx = 2;
        invoicePanel.add(new JLabel("Date:"), gbc);

        gbc.gridx = 3;
        dateField = new JTextField(10);
        dateField.setText(dateFormat.format(new Date()));
        invoicePanel.add(dateField, gbc);

        // Customer Selection
        gbc.gridx = 0; gbc.gridy = 1;
        invoicePanel.add(new JLabel("* Customer:"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 2;
        selectCustomerButton = new JButton("Click to select customer...");
        selectCustomerButton.setPreferredSize(new Dimension(300, 35));
        selectCustomerButton.setHorizontalAlignment(SwingConstants.LEFT);
        selectCustomerButton.addActionListener(e -> showCustomerSelectionDialog());
        invoicePanel.add(selectCustomerButton, gbc);

        gbc.gridx = 3; gbc.gridwidth = 1;
        JButton newCustomerButton = new JButton("New Customer");
        newCustomerButton.addActionListener(e -> createNewCustomer());
        invoicePanel.add(newCustomerButton, gbc);

        // Status
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        invoicePanel.add(new JLabel("Status:"), gbc);

        gbc.gridx = 1;
        statusCombo = new JComboBox<>(new String[]{"Draft", "Issued", "Paid", "Canceled"});
        invoicePanel.add(statusCombo, gbc);

        // Products table
        String[] columns = {"Code", "Product", "Quantity", "Unit Price €", "VAT Rate %", "Total €"};
        itemsTableModel = new InvoiceTableModel(columns, 0);
        itemsTable = new JTable(itemsTableModel);

        // Configure table
        itemsTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        itemsTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        itemsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        itemsTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        itemsTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        itemsTable.getColumnModel().getColumn(5).setPreferredWidth(100);

        configureTableEditors();

        // Panel for table buttons
        JPanel tableButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tableButtonPanel.setBorder(BorderFactory.createTitledBorder("Products"));

        JButton addItemButton = new JButton("Add Product");
        addItemButton.setPreferredSize(new Dimension(120, 30));
        addItemButton.addActionListener(e -> showProductSelectionDialog());

        JButton removeItemButton = new JButton("Remove");
        removeItemButton.setPreferredSize(new Dimension(100, 30));
        removeItemButton.addActionListener(e -> removeSelectedProduct());

        JButton editItemButton = new JButton("Edit Quantity");
        editItemButton.setPreferredSize(new Dimension(120, 30));
        editItemButton.addActionListener(e -> editSelectedProduct());

        tableButtonPanel.add(addItemButton);
        tableButtonPanel.add(removeItemButton);
        tableButtonPanel.add(editItemButton);

        // Panel for totals
        JPanel totalsPanel = new JPanel();
        totalsPanel.setLayout(new BoxLayout(totalsPanel, BoxLayout.Y_AXIS));
        totalsPanel.setBorder(BorderFactory.createTitledBorder("Totals"));

        taxableAmountLabel = new JLabel("Taxable Amount: € 0.00");
        vatLabel = new JLabel("VAT: € 0.00");
        totalLabel = new JLabel("TOTAL: € 0.00");

        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 16f));

        JPanel totalsPanelInner = new JPanel(new GridLayout(3, 1, 5, 5));
        totalsPanelInner.add(taxableAmountLabel);
        totalsPanelInner.add(vatLabel);
        totalsPanelInner.add(totalLabel);
        totalsPanel.add(totalsPanelInner);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton saveButton = new JButton("Save Invoice");
        JButton cancelButton = new JButton("Cancel");

        saveButton.setPreferredSize(new Dimension(120, 35));
        cancelButton.setPreferredSize(new Dimension(100, 35));
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD));

        saveButton.addActionListener(e -> saveInvoice());
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        // Layout assembly
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.add(tableButtonPanel, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(itemsTable), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(totalsPanel, BorderLayout.EAST);
        centerPanel.add(bottomPanel, BorderLayout.SOUTH);

        mainPanel.add(invoicePanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    // Custom table model for type safety
    private class InvoiceTableModel extends DefaultTableModel {
        private final Class<?>[] columnTypes = {
            String.class,
            String.class,
            Integer.class,
            String.class,
            String.class,
            String.class
        };

        public InvoiceTableModel(String[] columnNames, int rowCount) {
            super(columnNames, rowCount);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex < columnTypes.length) {
                return columnTypes[columnIndex];
            }
            return Object.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 2;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 2) {
                try {
                    int intValue;
                    if (value instanceof String) {
                        intValue = Integer.parseInt(((String) value).trim());
                    } else if (value instanceof Integer) {
                        intValue = (Integer) value;
                    } else {
                        intValue = Integer.parseInt(value.toString().trim());
                    }

                    if (intValue < 0) {
                        throw new NumberFormatException("Negative quantity not allowed");
                    }

                    super.setValueAt(intValue, row, col);
                    SwingUtilities.invokeLater(() -> updateTotals());

                } catch (NumberFormatException e) {
                    super.setValueAt(1, row, col);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(InvoiceDialog.this,
                            "Invalid quantity. Please enter a positive integer.",
                            "Invalid Input", JOptionPane.WARNING_MESSAGE);
                    });
                }
            } else {
                super.setValueAt(value, row, col);
            }
        }
    }

    private void configureTableEditors() {
        itemsTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JTextField()) {
            @Override
            public boolean stopCellEditing() {
                try {
                    String value = (String) getCellEditorValue();
                    int intValue = Integer.parseInt(value.trim());
                    if (intValue < 0) {
                        throw new NumberFormatException("Quantity cannot be negative");
                    }
                    return super.stopCellEditing();
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(InvoiceDialog.this,
                        "Please enter a valid positive integer for quantity",
                        "Invalid Input", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        });
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

    private void showProductSelectionDialog() {
        ProductSelectionDialog dialog = new ProductSelectionDialog(this);
        dialog.setVisible(true);

        if (dialog.isProductSelected()) {
            Product product = dialog.getSelectedProduct();
            int quantity = dialog.getSelectedQuantity();
            double vatRate = dialog.getSelectedVatRate();

            // Check if product already exists
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                String existingCode = (String) itemsTableModel.getValueAt(i, 0);
                if (existingCode.equals(product.getCode())) {
                    int choice = JOptionPane.showConfirmDialog(this,
                        "This product is already in the invoice.\n" +
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

            // Add new product
            Vector<Object> row = new Vector<>();
            row.add(product.getCode());
            row.add(product.getName());
            row.add(quantity);
            row.add(String.format("%.2f", product.getPrice()));
            row.add(String.format("%.1f", vatRate));
            row.add(String.format("%.2f", quantity * product.getPrice()));
            itemsTableModel.addRow(row);

            updateTotals();
        }
    }

    private void editSelectedProduct() {
        int selectedRow = itemsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                "Please select a product to edit",
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String productName = (String) itemsTableModel.getValueAt(selectedRow, 1);
        int currentQuantity = parseInteger(itemsTableModel.getValueAt(selectedRow, 2));

        String input = JOptionPane.showInputDialog(this,
            "Enter new quantity for " + productName + ":",
            "Edit Quantity",
            JOptionPane.QUESTION_MESSAGE);

        if (input != null && !input.trim().isEmpty()) {
            try {
                int newQuantity = Integer.parseInt(input.trim());
                if (newQuantity <= 0) {
                    JOptionPane.showMessageDialog(this,
                        "Quantity must be greater than zero",
                        "Invalid Input", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                itemsTableModel.setValueAt(newQuantity, selectedRow, 2);
                updateTotals();

            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this,
                    "Please enter a valid number",
                    "Invalid Input", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void removeSelectedProduct() {
        int selectedRow = itemsTable.getSelectedRow();
        if (selectedRow != -1) {
            String productName = (String) itemsTableModel.getValueAt(selectedRow, 1);
            int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to remove " + productName + " from the invoice?",
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
                "Error while loading products: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setupNewInvoice() {
        try {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            String number = DatabaseManager.getInstance().getNextInvoiceNumber(year);
            numberField.setText(number);

            dateField.setText(dateFormat.format(cal.getTime()));
            statusCombo.setSelectedItem("Draft");

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error while generating invoice number: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadInvoiceData() {
        numberField.setText(invoice.getNumber());
        dateField.setText(dateFormat.format(invoice.getDate()));
        statusCombo.setSelectedItem(invoice.getStatus());
        previousStatus = invoice.getStatus(); // Store current status as previous

        // Load customer
        selectedCustomer = new Customer(
            invoice.getCustomerId(),
            "", "", "", "", ""
        );
        selectCustomerButton.setText(invoice.getCustomerName());

        // Load products
        for (InvoiceItem item : invoice.getItems()) {
            Vector<Object> row = new Vector<>();
            row.add(item.getProductCode());
            row.add(item.getProductName());
            row.add(item.getQuantity());
            row.add(String.format("%.2f", item.getUnitPrice()));
            row.add(String.format("%.2f", item.getVatRate()));
            row.add(String.format("%.2f", item.getTotal()));
            itemsTableModel.addRow(row);
        }

        updateTotals();
    }

    private synchronized void updateTotals() {
        if (isUpdatingTotals) return;

        isUpdatingTotals = true;
        try {
            double taxableAmount = 0;
            double totalVat = 0;

            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                try {
                    Object quantityObj = itemsTableModel.getValueAt(i, 2);
                    Object priceObj = itemsTableModel.getValueAt(i, 3);
                    Object vatRateObj = itemsTableModel.getValueAt(i, 4);

                    int quantity = parseInteger(quantityObj);
                    double price = parseDouble(priceObj);
                    double vatRate = parseDouble(vatRateObj);

                    double subtotal = quantity * price;
                    double vat = subtotal * (vatRate / 100);

                    taxableAmount += subtotal;
                    totalVat += vat;

                    String currentTotal = (String) itemsTableModel.getValueAt(i, 5);
                    String newTotal = String.format("%.2f", subtotal);
                    if (!newTotal.equals(currentTotal)) {
                        itemsTableModel.setValueAt(newTotal, i, 5);
                    }

                } catch (Exception e) {
                    System.err.println("Error processing row " + i + ": " + e.getMessage());
                    continue;
                }
            }

            double total = taxableAmount + totalVat;

            currentTaxableAmount = taxableAmount;
            currentVat = totalVat;
            currentTotal = total;

            taxableAmountLabel.setText(String.format("Taxable Amount: € %.2f", taxableAmount));
            vatLabel.setText(String.format("VAT: € %.2f", totalVat));
            totalLabel.setText(String.format("TOTAL: € %.2f", total));

        } finally {
            isUpdatingTotals = false;
        }
    }

    private int parseInteger(Object obj) {
        if (obj instanceof Integer) {
            return (Integer) obj;
        }
        try {
            return Integer.parseInt(obj.toString().trim());
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
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void saveInvoice() {
        try {
            // Validation
            if (selectedCustomer == null) {
                JOptionPane.showMessageDialog(this,
                    "Please select a customer",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (itemsTableModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this,
                    "Please add at least one product to the invoice",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Date invoiceDate;
            try {
                invoiceDate = dateFormat.parse(dateField.getText());
            } catch (ParseException e) {
                JOptionPane.showMessageDialog(this,
                    "Invalid date format. Use dd/MM/yyyy",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String newStatus = (String)statusCombo.getSelectedItem();
            String number = numberField.getText();

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

            // Check stock availability for Issued invoices
            if ("Issued".equals(newStatus) || "Paid".equals(newStatus)) {
                Map<String, StockManager.StockAvailability> insufficient =
                    StockManager.checkStockAvailability(
                        DatabaseManager.getInstance().getConnection(),
                        stockItems,
                        invoice != null ? invoice.getId() : null,
                        "INVOICE"
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
                int invoiceId;

                if (invoice == null) {
                    // New invoice
                    String invoiceQuery = """
                        INSERT INTO invoices (number, date, customer_id, taxable_amount, vat, total, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(invoiceQuery, Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setString(1, number);
                        pstmt.setDate(2, new java.sql.Date(invoiceDate.getTime()));
                        pstmt.setInt(3, selectedCustomer.getId());
                        pstmt.setDouble(4, currentTaxableAmount);
                        pstmt.setDouble(5, currentVat);
                        pstmt.setDouble(6, currentTotal);
                        pstmt.setString(7, newStatus);
                        pstmt.executeUpdate();

                        try (ResultSet rs = pstmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                invoiceId = rs.getInt(1);
                            } else {
                                throw new SQLException("Failed to get invoice ID");
                            }
                        }
                    }

                    insertInvoiceDetails(conn, invoiceId);

                    // Handle stock based on status
                    handleStockForNewStatus(conn, invoiceId, newStatus, stockItems, invoiceDate, number);

                } else {
                    // Update existing invoice
                    invoiceId = invoice.getId();

                    String invoiceQuery = """
                        UPDATE invoices
                        SET date = ?, customer_id = ?, taxable_amount = ?, vat = ?, total = ?, status = ?
                        WHERE id = ?
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(invoiceQuery)) {
                        pstmt.setDate(1, new java.sql.Date(invoiceDate.getTime()));
                        pstmt.setInt(2, selectedCustomer.getId());
                        pstmt.setDouble(3, currentTaxableAmount);
                        pstmt.setDouble(4, currentVat);
                        pstmt.setDouble(5, currentTotal);
                        pstmt.setString(6, newStatus);
                        pstmt.setInt(7, invoiceId);
                        pstmt.executeUpdate();
                    }

                    // Delete old details
                    String deleteDetailsQuery = "DELETE FROM invoice_details WHERE invoice_id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(deleteDetailsQuery)) {
                        pstmt.setInt(1, invoiceId);
                        pstmt.executeUpdate();
                    }

                    insertInvoiceDetails(conn, invoiceId);

                    // Handle status change
                    handleStatusChange(conn, invoiceId, previousStatus, newStatus, stockItems, invoiceDate, number);
                }

                conn.commit();
                invoiceSaved = true;
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
                "Error while saving the invoice: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleStockForNewStatus(Connection conn, int invoiceId, String status,
                                        List<StockManager.StockItem> items, Date invoiceDate, String invoiceNumber) throws SQLException {
        if ("Issued".equals(status) || "Paid".equals(status)) {
            // Decrement stock directly
            StockManager.decrementStockDirectly(conn, items, invoiceDate, invoiceNumber, "INVOICE");
        }
        // Draft and Canceled: no stock action
    }

    private void handleStatusChange(Connection conn, int invoiceId, String oldStatus, String newStatus,
                                    List<StockManager.StockItem> items, Date invoiceDate, String invoiceNumber) throws SQLException {
        // Handle transition from old status
        if (("Issued".equals(oldStatus) || "Paid".equals(oldStatus)) &&
            !("Issued".equals(newStatus) || "Paid".equals(newStatus))) {
            // Restore stock
            StockManager.restoreStockFromDocument(conn, invoiceId, "INVOICE");
        }

        // Handle transition to new status
        if (("Issued".equals(newStatus) || "Paid".equals(newStatus)) &&
            !("Issued".equals(oldStatus) || "Paid".equals(oldStatus))) {
            // Decrement stock
            StockManager.decrementStockDirectly(conn, items, invoiceDate, invoiceNumber, "INVOICE");
        }
    }

    private void insertInvoiceDetails(Connection conn, int invoiceId) throws SQLException {
        String detailQuery = """
            INSERT INTO invoice_details
            (invoice_id, product_id, quantity, unit_price, vat_rate, total)
            VALUES (?, ?, ?, ?, ?, ?)
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
                double vatRate = parseDouble(itemsTableModel.getValueAt(i, 4));
                double productTotal = quantity * unitPrice;

                pstmt.setInt(1, invoiceId);
                pstmt.setInt(2, productId);
                pstmt.setInt(3, quantity);
                pstmt.setDouble(4, unitPrice);
                pstmt.setDouble(5, vatRate);
                pstmt.setDouble(6, productTotal);
                pstmt.executeUpdate();
            }
        }
    }

    public boolean isInvoiceSaved() {
        return invoiceSaved;
    }
}
