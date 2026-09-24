import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SupplierPriceListDialog extends JDialog {
    private int supplierId;
    private String supplierName;
    private SupplierPriceList priceList;
    private boolean priceSaved = false;

    private JComboBox<ProductDisplay> productCombo;
    private JTextField supplierCodeField;
    private JTextField priceField;
    private JSpinner minimumQuantitySpinner;
    private DatePickerField startDateField;
    private DatePickerField endDateField;
    private JTextArea notesArea;
    private SimpleDateFormat dateFormat;

    public SupplierPriceListDialog(JDialog parent, int supplierId, String supplierName, SupplierPriceList priceList) {
        super(parent, priceList == null ? "New Price" : "Edit Price", true);
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.priceList = priceList;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");

        setupWindow();
        initComponents();
        loadProducts();
        if (priceList != null) {
            loadPriceData();
        }
    }

    private void setupWindow() {
        
        setLayout(new BorderLayout(3, 3));
    }

    private void initComponents() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 3, 2, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.0;

        // Supplier
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Supplier:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField supplierField = new JTextField(supplierName);
        supplierField.setEditable(false);
        supplierField.setPreferredSize(new Dimension(300, 28));
        formPanel.add(supplierField, gbc);

        // Product
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("* Product:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        productCombo = new JComboBox<>();
        productCombo.setPreferredSize(new Dimension(300, 28));
        if (priceList != null) {
            productCombo.setEnabled(false); // Do not allow product change in edit mode
        }
        formPanel.add(productCombo, gbc);

        // Supplier Code
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Supplier Code:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        supplierCodeField = new JTextField(20);
        supplierCodeField.setPreferredSize(new Dimension(300, 28));
        formPanel.add(supplierCodeField, gbc);

        // Price
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("* Price " + SettingsPanel.getCurrencySymbol() + ":"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        priceField = new JTextField(10);
        priceField.setPreferredSize(new Dimension(300, 28));
        formPanel.add(priceField, gbc);

        // Minimum quantity
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Minimum Quantity:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(1, 1, 9999, 1);
        minimumQuantitySpinner = new JSpinner(spinnerModel);
        minimumQuantitySpinner.setPreferredSize(new Dimension(300, 28));
        formPanel.add(minimumQuantitySpinner, gbc);

        // Validity start date
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("* Validity Start Date:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        startDateField = new DatePickerField(10);
        startDateField.setText(DateUtils.formatDate(new Date(), dateFormat));
        startDateField.setToolTipText("Format: dd/MM/yyyy");
        startDateField.setPreferredSize(new Dimension(300, 28));
        formPanel.add(startDateField, gbc);

        // Validity end date
        gbc.gridx = 0; gbc.gridy = 6;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Validity End Date:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        endDateField = new DatePickerField(10, true);
        endDateField.setToolTipText("Format: dd/MM/yyyy (optional)");
        endDateField.setPreferredSize(new Dimension(300, 28));
        formPanel.add(endDateField, gbc);

        // Notes
        gbc.gridx = 0; gbc.gridy = 7;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        formPanel.add(new JLabel("Notes:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        notesArea = new JTextArea(3, 25);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setPreferredSize(new Dimension(300, 80));
        notesScroll.setMinimumSize(new Dimension(300, 60));
        formPanel.add(notesScroll, gbc);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.setPreferredSize(new Dimension(90, 32));
        cancelButton.setPreferredSize(new Dimension(90, 32));
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD));

        saveButton.addActionListener(e -> savePrice());
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        // Legend
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JLabel legendLabel = new JLabel("* Required fields");
        legendLabel.setFont(legendLabel.getFont().deriveFont(Font.ITALIC));
        legendLabel.setForeground(Color.GRAY);
        legendPanel.add(legendLabel);

        // Main layout
        add(legendPanel, BorderLayout.NORTH);
        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        Dialogs.centerWindow(this);
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
                    productCombo.addItem(new ProductDisplay(product));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error loading products: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static class ProductDisplay {
        private Product product;

        public ProductDisplay(Product product) {
            this.product = product;
        }

        public Product getProduct() { return product; }

        @Override
        public String toString() {
            return String.format("%s - %s", product.getCode(), product.getName());
        }
    }

    private void loadPriceData() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT l.*, p.name as product_name
                FROM supplier_price_lists l
                JOIN products p ON l.product_id = p.id
                WHERE l.id = ?
            """;

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, priceList.getId());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        // Select product
                        for (int i = 0; i < productCombo.getItemCount(); i++) {
                            ProductDisplay item = (ProductDisplay)productCombo.getItemAt(i);
                            if (item.getProduct().getId() == rs.getInt("product_id")) {
                                productCombo.setSelectedIndex(i);
                                break;
                            }
                        }

                        supplierCodeField.setText(rs.getString("supplier_product_code"));
                        priceField.setText(String.valueOf(rs.getDouble("price")));
                        minimumQuantitySpinner.setValue(rs.getInt("minimum_quantity"));

                        // FIXED: Use DateUtils for proper date parsing
                        Date startDate = DateUtils.parseDate(rs, "validity_start_date");
                        if (startDate != null) {
                            startDateField.setText(DateUtils.formatDate(startDate, dateFormat));
                        }

                        Date endDate = DateUtils.parseDate(rs, "validity_end_date");
                        if (endDate != null) {
                            endDateField.setText(DateUtils.formatDate(endDate, dateFormat));
                        }

                        notesArea.setText(rs.getString("notes"));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error loading price: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void savePrice() {
        try {
            // Validation
            if (productCombo.getSelectedItem() == null) {
                Dialogs.showMessageDialog(this,
                    "Select a product",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String priceText = priceField.getText().trim().replace(",", ".");
            if (priceText.isEmpty()) {
                Dialogs.showMessageDialog(this,
                    "Enter a price",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            double price;
            try {
                price = Numbers.nonNegative(priceText);
                if (price <= 0) throw new NumberFormatException();
            } catch (IllegalArgumentException e) {
                Dialogs.showMessageDialog(this,
                    "Price must be a positive number",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // FIXED: Use DateUtils for date parsing with proper validation
            Date startDate;
            Date endDate = null;
            try {
                startDate = DateUtils.parseDate(startDateField.getText(), dateFormat);
                if (startDate == null) {
                    Dialogs.showMessageDialog(this,
                        "Please enter a valid start date (dd/MM/yyyy)",
                        "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String endDateText = endDateField.getText().trim();
                if (!endDateText.isEmpty()) {
                    endDate = DateUtils.parseDate(endDateText, dateFormat);
                    if (endDate == null) {
                        Dialogs.showMessageDialog(this,
                            "Invalid end date format. Use dd/MM/yyyy",
                            "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    if (endDate.before(startDate)) {
                        Dialogs.showMessageDialog(this,
                            "The end validity date must be after the start date",
                            "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }
            } catch (Exception e) {
                Dialogs.showMessageDialog(this,
                    "Invalid date format. Use dd/MM/yyyy format",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            ProductDisplay selectedProduct = (ProductDisplay)productCombo.getSelectedItem();
            int productId = selectedProduct.getProduct().getId();

            minimumQuantitySpinner.commitEdit();
            int minimum = Numbers.positiveQuantity(minimumQuantitySpinner.getValue());
            Connection conn = DatabaseManager.getInstance().getConnection();
            DatabaseManager.beginTransaction(conn);
            try {
                String overlap = "SELECT id FROM supplier_price_lists WHERE supplier_id=? AND product_id=? AND minimum_quantity=? AND id<>? " +
                    "AND (validity_end_date IS NULL OR " + DateUtils.sqlDate("validity_end_date") + ">=?) " +
                    "AND (? IS NULL OR " + DateUtils.sqlDate("validity_start_date") + "<=?)";
                try (PreparedStatement check = conn.prepareStatement(overlap)) {
                    check.setInt(1,supplierId);check.setInt(2,productId);check.setInt(3,minimum);check.setInt(4,priceList==null?-1:priceList.getId());
                    SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd");
                    check.setString(5,iso.format(startDate));check.setString(6,endDate==null?null:iso.format(endDate));check.setString(7,endDate==null?null:iso.format(endDate));
                    try (ResultSet r = check.executeQuery()) {
                        if (r.next()) throw new SQLException("Another price for this quantity overlaps the selected validity period");
                    }
                }
            if (priceList == null) {
                // Insert new price
                String insertQuery = """
                    INSERT INTO supplier_price_lists (
                        supplier_id, product_id, supplier_product_code,
                        price, minimum_quantity, validity_start_date,
                        validity_end_date, notes
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

                try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
                    pstmt.setInt(1, supplierId);
                    pstmt.setInt(2, productId);
                    pstmt.setString(3, supplierCodeField.getText().trim());
                    pstmt.setDouble(4, price);
                    pstmt.setInt(5, minimum);
                    pstmt.setTimestamp(6, DateUtils.toSqlTimestamp(startDate));
                    pstmt.setTimestamp(7, endDate != null ? DateUtils.toSqlTimestamp(endDate) : null);
                    pstmt.setString(8, notesArea.getText().trim());
                    pstmt.executeUpdate();
                }

            } else {
                // Update existing price
                String updateQuery = """
                    UPDATE supplier_price_lists SET
                        supplier_product_code = ?,
                        price = ?, minimum_quantity = ?,
                        validity_start_date = ?, validity_end_date = ?,
                        notes = ?
                    WHERE id = ?
                """;

                try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                    pstmt.setString(1, supplierCodeField.getText().trim());
                    pstmt.setDouble(2, price);
                    pstmt.setInt(3, minimum);
                    pstmt.setTimestamp(4, DateUtils.toSqlTimestamp(startDate));
                    pstmt.setTimestamp(5, endDate != null ? DateUtils.toSqlTimestamp(endDate) : null);
                    pstmt.setString(6, notesArea.getText().trim());
                    pstmt.setInt(7, priceList.getId());
                    pstmt.executeUpdate();
                }
            }

            conn.commit();
            priceSaved = true;
            } catch (Exception e) { conn.rollback(); throw e; }
            finally { conn.setAutoCommit(true); }
            dispose();

        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error saving price: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isPriceSaved() {
        return priceSaved;
    }
}
