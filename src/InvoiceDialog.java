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
    private DatePickerField dateField;
    private JButton selectCustomerButton;
    private Customer selectedCustomer;
    private JComboBox<String> statusCombo;
    private JComboBox<String> paymentStatusCombo;
    private JLabel paidAmountLabel;
    private JTextField paidAmountField;
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
    private String documentCurrency;
    private boolean historicalForeignCurrencyLocked;
    private boolean linkedOrderLocked;
    private JCheckBox createCompletedOrderCheckBox;

    // Constructor for JFrame parent
    public InvoiceDialog(JFrame parent, Invoice invoice) {
        super(parent, invoice == null ? "New Invoice" : "Modify Invoice", true);
        this.invoice = invoice;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        this.productsCache = new HashMap<>();
        this.documentCurrency = resolveDocumentCurrency(invoice);
        this.historicalForeignCurrencyLocked = invoice != null && isFinalizedStatus(invoice.getStatus());
        this.linkedOrderLocked = invoice != null && hasLinkedOrder(invoice.getId());

        setupWindow();
        initComponents();
        loadProducts();
        if (invoice != null) {
            loadInvoiceData();
        } else {
            setupNewInvoice();
        }

        Dialogs.centerWindow(this);
    }

    // Constructor for JDialog parent
    public InvoiceDialog(JDialog parent, Invoice invoice) {
        super(parent, invoice == null ? "New Invoice" : "Modify Invoice", true);
        this.invoice = invoice;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        this.productsCache = new HashMap<>();
        this.documentCurrency = resolveDocumentCurrency(invoice);
        this.historicalForeignCurrencyLocked = invoice != null && isFinalizedStatus(invoice.getStatus());
        this.linkedOrderLocked = invoice != null && hasLinkedOrder(invoice.getId());

        setupWindow();
        initComponents();
        loadProducts();
        if (invoice != null) {
            loadInvoiceData();
        } else {
            setupNewInvoice();
        }

        Dialogs.centerWindow(this);
    }

    static boolean isFinalizedStatus(String status) {
        return status!=null && !"Draft".equals(status);
    }

    private boolean hasLinkedOrder(int invoiceId) {
        try (PreparedStatement p = DatabaseManager.getInstance().getConnection().prepareStatement(
                "SELECT order_id FROM invoices WHERE id=?")) {
            p.setInt(1, invoiceId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new IllegalStateException("Invoice no longer exists");
                return r.getObject(1) != null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                "Unable to load invoice order link: " + e.getMessage(), e);
        }
    }

    private boolean contentLocked() {
        return historicalForeignCurrencyLocked || linkedOrderLocked;
    }

    private String resolveDocumentCurrency(Invoice target) {
        if(target==null) return SettingsPanel.getCurrencyCode();
        try(PreparedStatement p=DatabaseManager.getInstance().getConnection().prepareStatement(
                "SELECT currency_code FROM invoices WHERE id=?")) {
            p.setInt(1,target.getId());
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw new IllegalStateException("Invoice no longer exists");
                String code=r.getString(1);
                if(code==null || code.isBlank()) throw new IllegalStateException("Invoice currency is missing");
                java.util.Currency.getInstance(code);
                return code;
            }
        } catch(SQLException e) {
            throw new IllegalStateException("Unable to load invoice currency: "+e.getMessage(),e);
        }
    }

    private String currencyCode() {
        return documentCurrency==null || documentCurrency.isBlank()
            ? SettingsPanel.getCurrencyCode() : documentCurrency;
    }

    private void setupWindow() {
        setSize(900, 600);
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
        dateField = new DatePickerField(10);
        dateField.setText(dateFormat.format(new Date()));
        invoicePanel.add(dateField, gbc);

        // Customer Selection
        gbc.gridx = 0; gbc.gridy = 1;
        invoicePanel.add(new JLabel("Customer *:"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 2;
        selectCustomerButton = new JButton("Click to select customer...");
        selectCustomerButton.putClientProperty("i18n.literal", true);
        selectCustomerButton.setPreferredSize(new Dimension(300, 35));
        selectCustomerButton.setHorizontalAlignment(SwingConstants.LEFT);
        selectCustomerButton.addActionListener(e -> showCustomerSelectionDialog());
        invoicePanel.add(selectCustomerButton, gbc);

        gbc.gridx = 3; gbc.gridwidth = 1;
        JButton newCustomerButton = new JButton("New Customer");
        newCustomerButton.addActionListener(e -> createNewCustomer());
        invoicePanel.add(newCustomerButton, gbc);

        // Document status
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        invoicePanel.add(new JLabel("Status:"), gbc);

        gbc.gridx = 1;
        statusCombo = new JComboBox<>(new String[]{"Draft", "Issued", "Canceled"});
        statusCombo.putClientProperty("i18n.values", true);
        invoicePanel.add(statusCombo, gbc);

        // Payment is separate from the document lifecycle.
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        invoicePanel.add(new JLabel("Payment Status:"), gbc);

        gbc.gridx = 1;
        paymentStatusCombo = new JComboBox<>(new String[]{"Not Paid", "Partially Paid", "Paid"});
        paymentStatusCombo.putClientProperty("i18n.values", true);
        paymentStatusCombo.addActionListener(e -> updatePaymentAmountField());
        invoicePanel.add(paymentStatusCombo, gbc);

        gbc.gridx = 2;
        paidAmountLabel = new JLabel("Paid Amount:");
        invoicePanel.add(paidAmountLabel, gbc);

        gbc.gridx = 3;
        paidAmountField = new JTextField("0.00");
        paidAmountField.setEnabled(false);
        invoicePanel.add(paidAmountField, gbc);

        if (invoice == null) {
            gbc.gridx = 0;
            gbc.gridy = 4;
            gbc.gridwidth = 4;

            JPanel stockOptionPanel = new JPanel();
            stockOptionPanel.setLayout(new BoxLayout(stockOptionPanel, BoxLayout.Y_AXIS));
            stockOptionPanel.setOpaque(false);

            createCompletedOrderCheckBox = new JCheckBox(
                I18n.tr("Create a completed order automatically and update warehouse stock"),
                true);
            createCompletedOrderCheckBox.setToolTipText(I18n.tr(
                "Creates a completed customer order with the same invoice products and updates stock."));
            createCompletedOrderCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            createCompletedOrderCheckBox.addActionListener(e -> updatePaymentAmountField());

            JLabel stockWarning = new JLabel(I18n.tr(
                "If this option is not selected, the invoice will not change warehouse quantities."));
            stockWarning.setFont(stockWarning.getFont().deriveFont(
                Math.max(10f, stockWarning.getFont().getSize2D() - 1f)));
            stockWarning.setAlignmentX(Component.LEFT_ALIGNMENT);

            stockOptionPanel.add(createCompletedOrderCheckBox);
            stockOptionPanel.add(stockWarning);
            invoicePanel.add(stockOptionPanel, gbc);

            gbc.gridwidth = 1;
        }

        // Products table
        String[] columns = {"Code", "Product", "Quantity *", "Unit Price", "VAT Rate %", "Total", "Product ID"};
        itemsTableModel = new InvoiceTableModel(columns, 0);
        itemsTable = new JTable(itemsTableModel);
        TableStyleUtil.setCurrencyCode(itemsTable, currencyCode());
        TableStyleUtil.applyCompact(itemsTable);
        itemsTable.removeColumn(itemsTable.getColumnModel().getColumn(6));

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

        taxableAmountLabel = new JLabel("Taxable Amount: " + SettingsPanel.formatCurrency(0,currencyCode()));
        vatLabel = new JLabel("VAT: " + SettingsPanel.formatCurrency(0,currencyCode()));
        totalLabel = new JLabel("TOTAL: " + SettingsPanel.formatCurrency(0,currencyCode()));

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

        JPanel footer = new JPanel(new BorderLayout());
        footer.add(Dialogs.requiredLegendPanel(), BorderLayout.WEST);
        footer.add(buttonPanel, BorderLayout.EAST);

        mainPanel.add(invoicePanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(footer, BorderLayout.SOUTH);

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

                    if (intValue <= 0) {
                        throw new NumberFormatException("Negative quantity not allowed");
                    }

                    super.setValueAt(intValue, row, col);
                    SwingUtilities.invokeLater(() -> updateTotals());

                } catch (NumberFormatException e) {
                    super.setValueAt(value, row, col);
                    SwingUtilities.invokeLater(() -> {
                        Dialogs.showMessageDialog(InvoiceDialog.this,
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
                    if (intValue <= 0) {
                        throw new NumberFormatException("Quantity cannot be negative");
                    }
                    return super.stopCellEditing();
                } catch (NumberFormatException e) {
                    Dialogs.showMessageDialog(InvoiceDialog.this,
                        "Please enter a valid positive integer for quantity",
                        "Invalid Input", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        });
    }

    private void showCustomerSelectionDialog() {
        if(blockContentEdit()) return;
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
        if(blockContentEdit()) return;
        CustomerDialog dialog = new CustomerDialog(this, null);
        dialog.setVisible(true);
        if (dialog.isCustomerSaved()) {
            Dialogs.showMessageDialog(this,
                "Customer created successfully. Please select it from the list.",
                "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void showProductSelectionDialog() {
        if(blockContentEdit()) return;
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
                    int choice = Dialogs.showConfirmDialog(this,
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
            row.add(product.getPrice());
            row.add(vatRate);
            row.add(Numbers.moneyText(Numbers.lineTotal(quantity, product.getPrice())));
            row.add(product.getId());
            itemsTableModel.addRow(row);

            updateTotals();
        }
    }

    private void editSelectedProduct() {
        if(blockContentEdit()) return;
        int selectedRow = itemsTable.getSelectedRow();
        if (selectedRow == -1) {
            Dialogs.showMessageDialog(this,
                "Please select a product to edit",
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String productName = (String) itemsTableModel.getValueAt(selectedRow, 1);
        int currentQuantity = parseInteger(itemsTableModel.getValueAt(selectedRow, 2));

        String input = Dialogs.showInputDialog(this,
            "Enter new quantity for " + productName + ":",
            "Edit Quantity",
            JOptionPane.QUESTION_MESSAGE);

        if (input != null && !input.trim().isEmpty()) {
            try {
                int newQuantity = Integer.parseInt(input.trim());
                if (newQuantity <= 0) {
                    Dialogs.showMessageDialog(this,
                        "Quantity must be greater than zero",
                        "Invalid Input", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                itemsTableModel.setValueAt(newQuantity, selectedRow, 2);
                updateTotals();

            } catch (NumberFormatException e) {
                Dialogs.showMessageDialog(this,
                    "Please enter a valid number",
                    "Invalid Input", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void removeSelectedProduct() {
        if(blockContentEdit()) return;
        int selectedRow = itemsTable.getSelectedRow();
        if (selectedRow != -1) {
            String productName = (String) itemsTableModel.getValueAt(selectedRow, 1);
            int result = Dialogs.showConfirmDialog(this,
                "Are you sure you want to remove " + productName + " from the invoice?",
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
            Dialogs.showMessageDialog(this,
                "Error while loading products: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setupNewInvoice() {
        Calendar cal = Calendar.getInstance();
        numberField.setText("Assigned when saved");
        dateField.setText(dateFormat.format(cal.getTime()));
        statusCombo.setSelectedItem("Draft");
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
            row.add(item.getUnitPrice());
            row.add(item.getVatRate());
            row.add(Numbers.moneyText(item.getTotal()));
            row.add(item.getProductId());
            itemsTableModel.addRow(row);
        }

        updateTotals();
        loadPaymentData();
        if (contentLocked()) {
            dateField.setEnabled(false);
            selectCustomerButton.setEnabled(false);
            itemsTable.setEnabled(false);

            if (historicalForeignCurrencyLocked) {
                statusCombo.setModel(new DefaultComboBoxModel<>(
                    new String[]{"Issued", "Canceled"}));
                statusCombo.putClientProperty("i18n.values", true);
                statusCombo.setSelectedItem(invoice.getStatus());
            }

            String notice;
            if (linkedOrderLocked) {
                notice = I18n.tr(
                    "Invoice linked to a customer order: document content is locked and payment is managed from the order.");
            } else if (!currencyCode().equals(SettingsPanel.getCurrencyCode())) {
                notice = I18n.tr(
                    "Historical invoice in a different currency: only status and payment can be changed.");
            } else {
                notice = I18n.tr("Finalized invoice: only status and payment can be changed.");
            }
            itemsTable.setToolTipText(notice);
            dateField.setToolTipText(notice);
            selectCustomerButton.setToolTipText(notice);
        }
    }

    private boolean blockContentEdit() {
        if (!contentLocked()) return false;

        String message;
        String title;
        if (linkedOrderLocked) {
            message = I18n.tr(
                "This invoice is linked to a customer order. Products, quantities, customer and date cannot be changed. Payment is managed from the order.");
            title = I18n.tr("Linked Invoice");
        } else if (!currencyCode().equals(SettingsPanel.getCurrencyCode())) {
            message = I18n.tr(
                "This historical invoice uses {OLD}. The current operating currency is {NEW}. Only status and payment can be changed.")
                .replace("{OLD}", currencyCode())
                .replace("{NEW}", SettingsPanel.getCurrencyCode());
            title = I18n.tr("Historical Invoice");
        } else {
            message = I18n.tr(
                "Issued and Canceled invoices are historical documents. Only status and payment can be changed.");
            title = I18n.tr("Historical Invoice");
        }

        Dialogs.showMessageDialog(
            this, message, title, JOptionPane.INFORMATION_MESSAGE);
        return true;
    }

    private String paymentStatusCode() {
        String selected = paymentStatusCombo == null
            ? "Not Paid" : String.valueOf(paymentStatusCombo.getSelectedItem());
        if ("Paid".equals(selected)) return "PAID";
        if ("Partially Paid".equals(selected)) return "PARTIALLY_PAID";
        return "NOT_PAID";
    }

    private double paymentTargetTotal() {
        if (invoice == null && createCompletedOrderCheckBox != null
                && createCompletedOrderCheckBox.isSelected()) {
            return currentTaxableAmount;
        }
        return currentTotal;
    }

    private double paymentAmountForSave(double targetTotal) {
        String status = paymentStatusCode();
        if ("NOT_PAID".equals(status)) return 0.0;
        if ("PAID".equals(status)) return Numbers.money(java.math.BigDecimal.valueOf(targetTotal));

        double amount = Numbers.money(Numbers.decimal(paidAmountField.getText()));
        if (amount <= 0 || amount >= targetTotal) {
            throw new IllegalArgumentException(I18n.tr(
                "Partial paid amount must be greater than zero and less than the total."));
        }
        return amount;
    }

    private void updatePaymentAmountField() {
        if (paymentStatusCombo == null || paidAmountField == null) return;
        String selected = String.valueOf(paymentStatusCombo.getSelectedItem());
        boolean linked = invoice != null && linkedOrderLocked;
        paymentStatusCombo.setEnabled(!linked);
        paidAmountLabel.setText("Partially Paid".equals(selected)
            ? I18n.tr("Paid Amount *:") : I18n.tr("Paid Amount:"));

        if (linked) {
            paidAmountField.setEnabled(false);
            return;
        }
        if ("Partially Paid".equals(selected)) {
            paidAmountField.setEnabled(true);
        } else {
            paidAmountField.setEnabled(false);
            paidAmountField.setText("Paid".equals(selected)
                ? Numbers.moneyText(paymentTargetTotal()) : "0.00");
        }
    }

    private void loadPaymentData() {
        if (invoice == null) {
            paymentStatusCombo.setSelectedItem("Not Paid");
            paidAmountField.setText("0.00");
            updatePaymentAmountField();
            return;
        }

        try (PreparedStatement p = DatabaseManager.getInstance().getConnection().prepareStatement("""
                SELECT i.order_id,
                       CASE WHEN i.order_id IS NOT NULL THEN o.payment_status ELSE i.payment_status END AS payment_status,
                       CASE WHEN i.order_id IS NOT NULL THEN o.paid_amount ELSE i.paid_amount END AS paid_amount
                FROM invoices i
                LEFT JOIN orders o ON o.id=i.order_id
                WHERE i.id=?
                """)) {
            p.setInt(1, invoice.getId());
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Invoice no longer exists");
                Object orderValue = r.getObject("order_id");
                linkedOrderLocked = orderValue != null;
                String status = r.getString("payment_status");
                if ("PAID".equals(status)) paymentStatusCombo.setSelectedItem("Paid");
                else if ("PARTIALLY_PAID".equals(status)) paymentStatusCombo.setSelectedItem("Partially Paid");
                else paymentStatusCombo.setSelectedItem("Not Paid");
                paidAmountField.setText(Numbers.moneyText(r.getDouble("paid_amount")));
                updatePaymentAmountField();
                if (linkedOrderLocked) {
                    String tooltip = I18n.tr(
                        "Payment is managed by the linked customer order and is read-only here.");
                    paymentStatusCombo.setToolTipText(tooltip);
                    paidAmountField.setToolTipText(tooltip);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load invoice payment: " + e.getMessage(), e);
        }
    }

    private void saveLockedStatusOnly() {
        String newStatus = (String) statusCombo.getSelectedItem();

        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            DatabaseManager.beginTransaction(conn);
            try {
                String currentStatus;
                Integer currentOrderId;
                String storedCurrency;

                try (PreparedStatement read = conn.prepareStatement(
                        "SELECT status,order_id,currency_code FROM invoices WHERE id=?")) {
                    read.setInt(1, invoice.getId());
                    try (ResultSet r = read.executeQuery()) {
                        if (!r.next())
                            throw new SQLException("Invoice changed or no longer exists");
                        currentStatus = r.getString("status");
                        Object orderValue = r.getObject("order_id");
                        currentOrderId = orderValue == null ? null : ((Number) orderValue).intValue();
                        storedCurrency = r.getString("currency_code");
                    }
                }

                if (!java.util.Objects.equals(storedCurrency, currencyCode()))
                    throw new SQLException("Invoice currency changed unexpectedly");

                if (!"Draft".equals(currentStatus) && "Draft".equals(newStatus)) {
                    Dialogs.showMessageDialog(
                        this,
                        I18n.tr("A finalized invoice cannot be returned to Draft."),
                        I18n.tr("Historical Invoice"),
                        JOptionPane.WARNING_MESSAGE);
                    conn.rollback();
                    return;
                }

                // A Draft reaches this path only because it is linked to an order.
                if ("Draft".equals(currentStatus) && currentOrderId == null)
                    throw new SQLException("Invoice is no longer linked to an order");

                if (currentOrderId != null) {
                    try (PreparedStatement p = conn.prepareStatement(
                            "UPDATE invoices SET status=? WHERE id=?")) {
                        p.setString(1, newStatus);
                        p.setInt(2, invoice.getId());
                        if (p.executeUpdate() != 1)
                            throw new SQLException("Invoice changed or no longer exists");
                    }
                } else {
                    String paymentStatus = paymentStatusCode();
                    double paidAmount = paymentAmountForSave(currentTotal);
                    try (PreparedStatement p = conn.prepareStatement(
                            "UPDATE invoices SET status=?,payment_status=?,paid_amount=? WHERE id=?")) {
                        p.setString(1, newStatus);
                        p.setString(2, paymentStatus);
                        p.setDouble(3, paidAmount);
                        p.setInt(4, invoice.getId());
                        if (p.executeUpdate() != 1)
                            throw new SQLException("Invoice changed or no longer exists");
                    }
                    invoice.setPaymentStatus(paymentStatus);
                    invoice.setPaidAmount(paidAmount);
                }

                InvoiceSnapshots.capture(conn, invoice.getId(), false);
                conn.commit();

                invoice.setStatus(newStatus);
                historicalForeignCurrencyLocked = isFinalizedStatus(newStatus);
                linkedOrderLocked = currentOrderId != null;
                invoiceSaved = true;
                dispose();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(
                this,
                I18n.tr("Error while saving the invoice") + ": " + e.getMessage(),
                I18n.tr("Error"),
                JOptionPane.ERROR_MESSAGE);
        }
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

                    double subtotal = Numbers.lineTotal(quantity, price);
                    double vat = Numbers.tax(subtotal, vatRate);

                    taxableAmount = Numbers.add(taxableAmount, subtotal);
                    totalVat = Numbers.add(totalVat, vat);

                    String currentTotal = (String) itemsTableModel.getValueAt(i, 5);
                    String newTotal = Numbers.moneyText(subtotal);
                    if (!newTotal.equals(currentTotal)) {
                        itemsTableModel.setValueAt(newTotal, i, 5);
                    }

                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid invoice row " + (i+1),e);
                }
            }

            double total = Numbers.add(taxableAmount, totalVat);

            currentTaxableAmount = taxableAmount;
            currentVat = totalVat;
            currentTotal = total;

            taxableAmountLabel.setText("Taxable Amount: " + SettingsPanel.formatCurrency(taxableAmount,currencyCode()));
            vatLabel.setText("VAT: " + SettingsPanel.formatCurrency(totalVat,currencyCode()));
            totalLabel.setText("TOTAL: " + SettingsPanel.formatCurrency(total,currencyCode()));
            if (paymentStatusCombo != null && "Paid".equals(paymentStatusCombo.getSelectedItem())) {
                updatePaymentAmountField();
            }

        } catch(IllegalArgumentException e) {
            taxableAmountLabel.setText("Invalid invoice row");vatLabel.setText("");totalLabel.setText("Correct quantity, price and VAT before saving");
        } finally {
            isUpdatingTotals = false;
        }
    }

    private int parseInteger(Object value) { return Numbers.positiveQuantity(value); }
    private double parseDouble(Object value) { return Numbers.nonNegative(value); }

    private void validateRows() {
        Numbers.commitEdits(itemsTable);
        double taxable = 0, tax = 0;
        for (int i=0; i<itemsTableModel.getRowCount(); i++) {
            double line = Numbers.lineTotal(Numbers.positiveQuantity(itemsTableModel.getValueAt(i,2)),
                Numbers.nonNegative(itemsTableModel.getValueAt(i,3)));
            taxable = Numbers.add(taxable, line);
            tax = Numbers.add(tax, Numbers.tax(line, Numbers.vat(itemsTableModel.getValueAt(i,4))));
        }
        currentTaxableAmount = taxable; currentVat = tax; currentTotal = Numbers.add(taxable, tax);
        updateTotals();
    }

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

    private List<StockManager.StockItem> invoiceStockItems(Connection conn) throws SQLException {
        List<StockManager.StockItem> items = new ArrayList<>();
        for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
            int productId = productIdForRow(conn, i);
            String productName = String.valueOf(itemsTableModel.getValueAt(i, 1));
            int quantity = parseInteger(itemsTableModel.getValueAt(i, 2));
            items.add(new StockManager.StockItem(productId, productName, quantity));
        }
        return items;
    }

    private int createCompletedOrder(Connection conn, Date invoiceDate,
            List<StockManager.StockItem> stockItems, String paymentStatus,
            double paidAmount) throws SQLException {
        int orderId;
        try (PreparedStatement p = conn.prepareStatement("""
                INSERT INTO orders
                    (customer_id, order_date, status, total, payment_status, paid_amount)
                VALUES (?, ?, 'Completed', ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            p.setInt(1, selectedCustomer.getId());
            p.setTimestamp(2, DateUtils.toSqlTimestamp(invoiceDate));
            p.setDouble(3, currentTaxableAmount);
            p.setString(4, paymentStatus);
            p.setDouble(5, paidAmount);
            p.executeUpdate();
            try (ResultSet keys = p.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Failed to get order ID");
                orderId = keys.getInt(1);
            }
        }

        try (PreparedStatement p = conn.prepareStatement("""
                INSERT INTO order_details(order_id, product_id, quantity, unit_price)
                VALUES (?, ?, ?, ?)
                """)) {
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                p.setInt(1, orderId);
                p.setInt(2, productIdForRow(conn, i));
                p.setInt(3, parseInteger(itemsTableModel.getValueAt(i, 2)));
                p.setDouble(4, parseDouble(itemsTableModel.getValueAt(i, 3)));
                p.addBatch();
            }
            p.executeBatch();
        }

        for (StockManager.StockItem item : stockItems) {
            StockManager.createOrUpdateReservation(conn, item.getProductId(),
                "ORDER", orderId, item.getQuantity(), "Order #" + orderId);
        }
        StockManager.completeReservationAndDecrementStock(
            conn, "ORDER", orderId, invoiceDate, String.valueOf(orderId));
        return orderId;
    }

    private void saveInvoice() {
        if (contentLocked()) {
            saveLockedStatusOnly();
            return;
        }
        try {
            validateRows();
            // Validation
            if (selectedCustomer == null) {
                Dialogs.showMessageDialog(this,
                    "Please select a customer",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (itemsTableModel.getRowCount() == 0) {
                Dialogs.showMessageDialog(this,
                    "Please add at least one product to the invoice",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Date invoiceDate;
            try {
                invoiceDate = DateUtils.parseDate(dateField.getText(), dateFormat);
                if (invoiceDate == null) throw new ParseException("Date is required",0);
            } catch (ParseException e) {
                Dialogs.showMessageDialog(this,
                    "Invalid date format. Use dd/MM/yyyy",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String newStatus = (String)statusCombo.getSelectedItem();
            String number = numberField.getText();

            boolean createCompletedOrder = invoice == null
                && createCompletedOrderCheckBox != null
                && createCompletedOrderCheckBox.isSelected();
            String paymentStatus = paymentStatusCode();
            double paidAmount;
            try {
                paidAmount = paymentAmountForSave(
                    createCompletedOrder ? currentTaxableAmount : currentTotal);
            } catch (IllegalArgumentException e) {
                Dialogs.showMessageDialog(this, e.getMessage(),
                    I18n.tr("Invalid Payment"), JOptionPane.WARNING_MESSAGE);
                return;
            }

            Connection conn = DatabaseManager.getInstance().getConnection();
            DatabaseManager.beginTransaction(conn);
            try {
                int invoiceId;

                if (invoice == null) {
                    Integer sourceOrderId = null;
                    if (createCompletedOrder) {
                        List<StockManager.StockItem> stockItems = invoiceStockItems(conn);
                        java.util.List<Integer> productIds = new java.util.ArrayList<>();
                        for (StockManager.StockItem item : stockItems)
                            productIds.add(item.getProductId());
                        String missingCost = CostIntegrity.missingPurchaseCostMessage(conn, productIds);
                        if (missingCost != null) {
                            conn.rollback();
                            Dialogs.showMessageDialog(this, missingCost,
                                I18n.tr("Purchase Cost Required"), JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                        Map<String, StockManager.StockAvailability> insufficient =
                            StockManager.checkStockAvailability(conn, stockItems, null, "ORDER");

                        if (!insufficient.isEmpty()) {
                            StringBuilder message = new StringBuilder(
                                I18n.tr("Insufficient stock for the following products:") + "\n\n");
                            for (Map.Entry<String, StockManager.StockAvailability> entry
                                    : insufficient.entrySet()) {
                                message.append("- ").append(entry.getKey()).append(": ")
                                    .append(entry.getValue().getFormattedMessage()).append("\n");
                            }
                            message.append("\n")
                                .append(I18n.tr(
                                    "The completed customer order cannot be created because stock cannot go below zero. Reduce the quantities or save the invoice without creating the order."));

                            Dialogs.showMessageDialog(this,
                                message.toString(),
                                I18n.tr("Insufficient Stock"),
                                JOptionPane.WARNING_MESSAGE);
                            conn.rollback();
                            return;
                        }

                        sourceOrderId = createCompletedOrder(
                            conn, invoiceDate, stockItems, paymentStatus, paidAmount);
                    }
                    Calendar invoiceCalendar = Calendar.getInstance();
                    invoiceCalendar.setTime(invoiceDate);
                    number = DatabaseManager.getInstance().getNextInvoiceNumber(
                        conn, invoiceCalendar.get(Calendar.YEAR));
                    // New invoice
                    String invoiceQuery = """
                        INSERT INTO invoices
                            (number, date, customer_id, taxable_amount, vat, total, status,
                             payment_status, paid_amount, order_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(invoiceQuery, Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setString(1, number);
                        pstmt.setDate(2, new java.sql.Date(invoiceDate.getTime()));
                        pstmt.setInt(3, selectedCustomer.getId());
                        pstmt.setDouble(4, currentTaxableAmount);
                        pstmt.setDouble(5, currentVat);
                        pstmt.setDouble(6, currentTotal);
                        pstmt.setString(7, newStatus);
                        // A linked invoice always reads payment from its order.
                        pstmt.setString(8, sourceOrderId == null ? paymentStatus : "NOT_PAID");
                        pstmt.setDouble(9, sourceOrderId == null ? paidAmount : 0.0);
                        if (sourceOrderId != null) pstmt.setInt(10, sourceOrderId);
                        else pstmt.setNull(10, Types.INTEGER);
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
                    numberField.setText(number);

                } else {
                    // Update existing invoice
                    invoiceId = invoice.getId();
                    try(PreparedStatement draft=conn.prepareStatement("UPDATE invoices SET customer_snapshot=NULL,company_snapshot=NULL,snapshot_version=NULL WHERE id=? AND status='Draft'")) {
                        draft.setInt(1,invoiceId);draft.executeUpdate();
                    }
                    try (PreparedStatement p=conn.prepareStatement("UPDATE invoices SET customer_snapshot=NULL " +
                            "WHERE id=? AND customer_id<>?")) {
                        p.setInt(1,invoiceId);p.setInt(2,selectedCustomer.getId());p.executeUpdate();
                    }

                    String invoiceQuery = """
                        UPDATE invoices
                        SET date = ?, customer_id = ?, taxable_amount = ?, vat = ?, total = ?,
                            status = ?, payment_status = ?, paid_amount = ?
                        WHERE id = ?
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(invoiceQuery)) {
                        pstmt.setDate(1, new java.sql.Date(invoiceDate.getTime()));
                        pstmt.setInt(2, selectedCustomer.getId());
                        pstmt.setDouble(3, currentTaxableAmount);
                        pstmt.setDouble(4, currentVat);
                        pstmt.setDouble(5, currentTotal);
                        pstmt.setString(6, newStatus);
                        pstmt.setString(7, paymentStatus);
                        pstmt.setDouble(8, paidAmount);
                        pstmt.setInt(9, invoiceId);
                        pstmt.executeUpdate();
                    }

                    // Delete old details
                    String deleteDetailsQuery = "DELETE FROM invoice_details WHERE invoice_id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(deleteDetailsQuery)) {
                        pstmt.setInt(1, invoiceId);
                        pstmt.executeUpdate();
                    }

                    insertInvoiceDetails(conn, invoiceId);
                }

                InvoiceSnapshots.capture(conn,invoiceId,false);
                conn.commit();
                invoiceSaved = true;
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
                "Error while saving the invoice: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void insertInvoiceDetails(Connection conn, int invoiceId) throws SQLException {
        String detailQuery = """
            INSERT INTO invoice_details
            (invoice_id, product_id, quantity, unit_price, vat_rate, total, product_code, product_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement pstmt = conn.prepareStatement(detailQuery)) {
            for (int i = 0; i < itemsTableModel.getRowCount(); i++) {
                int productId = productIdForRow(conn, i);

                int quantity = parseInteger(itemsTableModel.getValueAt(i, 2));
                double unitPrice = parseDouble(itemsTableModel.getValueAt(i, 3));
                double vatRate = Numbers.vat(itemsTableModel.getValueAt(i, 4));
                double productTotal = Numbers.lineTotal(quantity, unitPrice);

                pstmt.setInt(1, invoiceId);
                pstmt.setInt(2, productId);
                pstmt.setInt(3, quantity);
                pstmt.setDouble(4, unitPrice);
                pstmt.setDouble(5, vatRate);
                pstmt.setDouble(6, productTotal);
                pstmt.setString(7,String.valueOf(itemsTableModel.getValueAt(i,0)));
                pstmt.setString(8,String.valueOf(itemsTableModel.getValueAt(i,1)));
                pstmt.executeUpdate();
            }
        }
    }

    public boolean isInvoiceSaved() {
        return invoiceSaved;
    }
}
