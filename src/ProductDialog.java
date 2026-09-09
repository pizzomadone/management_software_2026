import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

public class ProductDialog extends JDialog {
    private JTextField codeField;
    private JTextField nameField;
    private JTextArea descriptionArea;
    private JTextField priceField;
    private JSpinner quantitySpinner;

    // New fields
    private JComboBox<String> categoryComboBox;
    private JTextField alternativeSkuField;
    private JTextField weightField;
    private JComboBox<String> unitOfMeasureComboBox;
    private JSpinner minimumQuantitySpinner;
    private JTextField acquisitionCostField;
    private JCheckBox activeCheckBox;
    private JButton selectSupplierButton;
    private Supplier selectedSupplier;
    private JTextField warehousePositionField;
    private JTextField vatRateField;

    private boolean productSaved = false;
    private Product product;
    
    // Constructor for JFrame parent
    public ProductDialog(JFrame parent, Product product) {
        super(parent, product == null ? "New Product" : "Edit Product", true);
        this.product = product;
        
        setupDialog();
        initComponents();
        if (product != null) {
            loadProductData();
        }
    }
    
    // Constructor for JDialog parent
    public ProductDialog(JDialog parent, Product product) {
        super(parent, product == null ? "New Product" : "Edit Product", true);
        this.product = product;
        
        setupDialog();
        initComponents();
        if (product != null) {
            loadProductData();
        }
    }
    
    private void setupDialog() {
         // Center on screen instead of parent window
        setLayout(new BorderLayout(5, 5));
    }
    
    private void initComponents() {
        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5); // Reduced vertical spacing for compactness
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Code
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Code:"), gbc);

        gbc.gridx = 1;
        codeField = new JTextField(12);
        formPanel.add(codeField, gbc);

        // Name
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Name:"), gbc);

        gbc.gridx = 1;
        nameField = new JTextField(12);
        formPanel.add(nameField, gbc);

        // Description
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Description:"), gbc);

        gbc.gridx = 1;
        descriptionArea = new JTextArea(3, 12);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        formPanel.add(new JScrollPane(descriptionArea), gbc);

        // Price
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Price:"), gbc);

        gbc.gridx = 1;
        priceField = new JTextField(12);
        formPanel.add(priceField, gbc);

        // Quantity
        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("Quantity:"), gbc);

        gbc.gridx = 1;
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(0, 0, 999999, 1);
        quantitySpinner = new JSpinner(spinnerModel);
        formPanel.add(quantitySpinner, gbc);

        // Category
        gbc.gridx = 0; gbc.gridy = 5;
        formPanel.add(new JLabel("Category:"), gbc);

        gbc.gridx = 1;
        String[] categories = {"", "Electronics", "Computers & Accessories", "Smartphones & Tablets",
                               "Furniture", "Office Furniture", "Home Furniture",
                               "Clothing", "Men's Clothing", "Women's Clothing", "Children's Clothing",
                               "Food & Beverages", "Fresh Food", "Packaged Food", "Beverages",
                               "Tools & Hardware", "Power Tools", "Hand Tools", "Hardware",
                               "Office Supplies", "Stationery", "Paper Products", "Writing Instruments",
                               "Books & Media", "Books", "Music", "Movies",
                               "Sports & Outdoors", "Fitness Equipment", "Outdoor Gear",
                               "Health & Beauty", "Personal Care", "Cosmetics", "Health Products",
                               "Automotive", "Car Parts", "Car Accessories",
                               "Home & Garden", "Kitchen", "Bathroom", "Garden",
                               "Toys & Games", "Toys", "Board Games", "Video Games",
                               "Pet Supplies", "Pet Food", "Pet Accessories",
                               "Industrial & Scientific", "Industrial Equipment", "Lab Supplies",
                               "Other"};
        categoryComboBox = new JComboBox<>(categories);
        categoryComboBox.setEditable(true);
        formPanel.add(categoryComboBox, gbc);

        // Alternative SKU
        gbc.gridx = 0; gbc.gridy = 6;
        formPanel.add(new JLabel("Alternative SKU:"), gbc);

        gbc.gridx = 1;
        alternativeSkuField = new JTextField(12);
        formPanel.add(alternativeSkuField, gbc);

        // Weight
        gbc.gridx = 0; gbc.gridy = 7;
        formPanel.add(new JLabel("Weight (kg):"), gbc);

        gbc.gridx = 1;
        weightField = new JTextField(12);
        weightField.setText("0.0");
        formPanel.add(weightField, gbc);

        // Unit of Measure
        gbc.gridx = 0; gbc.gridy = 8;
        formPanel.add(new JLabel("Unit of Measure:"), gbc);

        gbc.gridx = 1;
        String[] units = {"pcs", "kg", "m", "liters", "box", "pack"};
        unitOfMeasureComboBox = new JComboBox<>(units);
        unitOfMeasureComboBox.setEditable(true);
        formPanel.add(unitOfMeasureComboBox, gbc);

        // Minimum Quantity
        gbc.gridx = 0; gbc.gridy = 9;
        formPanel.add(new JLabel("Minimum Quantity:"), gbc);

        gbc.gridx = 1;
        SpinnerNumberModel minQtyModel = new SpinnerNumberModel(0, 0, 999999, 1);
        minimumQuantitySpinner = new JSpinner(minQtyModel);
        formPanel.add(minimumQuantitySpinner, gbc);

        // Acquisition Cost
        gbc.gridx = 0; gbc.gridy = 10;
        formPanel.add(new JLabel("Acquisition Cost:"), gbc);

        gbc.gridx = 1;
        acquisitionCostField = new JTextField(12);
        acquisitionCostField.setText("0.0");
        formPanel.add(acquisitionCostField, gbc);

        // Active
        gbc.gridx = 0; gbc.gridy = 11;
        formPanel.add(new JLabel("Active:"), gbc);

        gbc.gridx = 1;
        activeCheckBox = new JCheckBox("", true);
        formPanel.add(activeCheckBox, gbc);

        // Supplier
        gbc.gridx = 0; gbc.gridy = 12;
        formPanel.add(new JLabel("Supplier:"), gbc);

        gbc.gridx = 1;
        selectSupplierButton = new JButton("Click to select supplier...");
        selectSupplierButton.putClientProperty("i18n.literal", true);
        selectSupplierButton.setPreferredSize(new Dimension(250, 35));
        selectSupplierButton.setHorizontalAlignment(SwingConstants.LEFT);
        selectSupplierButton.addActionListener(e -> showSupplierSelectionDialog());
        formPanel.add(selectSupplierButton, gbc);

        gbc.gridx = 2;
        JButton newSupplierButton = new JButton("New Supplier");
        newSupplierButton.addActionListener(e -> createNewSupplier());
        formPanel.add(newSupplierButton, gbc);

        // Warehouse Position
        gbc.gridx = 0; gbc.gridy = 13;
        formPanel.add(new JLabel("Warehouse Position:"), gbc);

        gbc.gridx = 1;
        warehousePositionField = new JTextField(12);
        formPanel.add(warehousePositionField, gbc);

        // VAT Rate
        gbc.gridx = 0; gbc.gridy = 14;
        formPanel.add(new JLabel("VAT Rate (%):"), gbc);

        gbc.gridx = 1;
        vatRateField = new JTextField(12);
        vatRateField.setText(String.valueOf(SettingsPanel.getDefaultVatRate()));
        formPanel.add(vatRateField, gbc);

        // Button panel
        JPanel buttonPanel = new JPanel();
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        
        saveButton.addActionListener(e -> saveProduct());
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        
        // Main layout
        add(new JScrollPane(formPanel), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }
    
    private void loadProductData() {
        codeField.setText(product.getCode());
        nameField.setText(product.getName());
        descriptionArea.setText(product.getDescription());
        priceField.setText(String.valueOf(product.getPrice()));
        quantitySpinner.setValue(product.getQuantity());

        // Load new fields
        categoryComboBox.setSelectedItem(product.getCategory());
        alternativeSkuField.setText(product.getAlternativeSku());
        weightField.setText(String.valueOf(product.getWeight()));
        unitOfMeasureComboBox.setSelectedItem(product.getUnitOfMeasure());
        minimumQuantitySpinner.setValue(product.getMinimumQuantity());
        acquisitionCostField.setText(String.valueOf(product.getAcquisitionCost()));
        activeCheckBox.setSelected(product.isActive());

        // Load supplier if present
        Integer supplierId = product.getSupplierId();
        if (supplierId != null && supplierId > 0) {
            loadSupplierById(supplierId);
        }

        // Load new fields
        warehousePositionField.setText(product.getWarehousePosition());
        vatRateField.setText(String.valueOf(product.getVatRate()));
    }

    private void loadSupplierById(Integer supplierId) {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = "SELECT * FROM suppliers WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, supplierId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        selectedSupplier = new Supplier(
                            rs.getInt("id"),
                            rs.getString("company_name"),
                            rs.getString("vat_number"),
                            rs.getString("tax_code"),
                            rs.getString("address"),
                            rs.getString("phone"),
                            rs.getString("email"),
                            rs.getString("certified_email"),
                            rs.getString("website"),
                            rs.getString("notes")
                        );
                        updateSupplierButton();
                    }
                }
            }
        } catch (SQLException e) {
            // If supplier not found, clear the button
            selectSupplierButton.setText("Click to select supplier...");
        }
    }

    private void showSupplierSelectionDialog() {
        SupplierSelectionDialog dialog = new SupplierSelectionDialog(this);
        dialog.setVisible(true);

        if (dialog.isSupplierSelected()) {
            selectedSupplier = dialog.getSelectedSupplier();
            updateSupplierButton();
        }
    }

    private void updateSupplierButton() {
        if (selectedSupplier != null) {
            String buttonText = selectedSupplier.getCompanyName();

            if (selectedSupplier.getVatNumber() != null && !selectedSupplier.getVatNumber().isEmpty()) {
                buttonText += " (VAT: " + selectedSupplier.getVatNumber() + ")";
            }

            selectSupplierButton.setText(buttonText);
            selectSupplierButton.setToolTipText("Supplier: " + buttonText);
        }
    }

    private void createNewSupplier() {
        SupplierDialog dialog = new SupplierDialog(this, null);
        dialog.setVisible(true);
        if (dialog.isSupplierSaved()) {
            JOptionPane.showMessageDialog(this,
                "Supplier created successfully. Please select it from the list.",
                "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void saveProduct() {
        try {
            quantitySpinner.commitEdit();
            minimumQuantitySpinner.commitEdit();
            // Validation
            String code = codeField.getText().trim();
            String name = nameField.getText().trim();
            String description = descriptionArea.getText().trim();
            double price = Numbers.nonNegative(priceField.getText());
            int quantity = (int)quantitySpinner.getValue();

            // New fields
            String category = (String)categoryComboBox.getSelectedItem();
            String alternativeSku = alternativeSkuField.getText().trim();
            double weight = Numbers.nonNegative(weightField.getText());
            String unitOfMeasure = (String)unitOfMeasureComboBox.getSelectedItem();
            int minimumQuantity = Numbers.quantity(minimumQuantitySpinner.getValue());
            if (minimumQuantity < 0) throw new IllegalArgumentException("Minimum stock cannot be negative");
            double acquisitionCost = Numbers.nonNegative(acquisitionCostField.getText());
            boolean active = activeCheckBox.isSelected();
            Integer supplierId = selectedSupplier != null ? selectedSupplier.getId() : null;
            String warehousePosition = warehousePositionField.getText().trim();
            double vatRate = Numbers.vat(vatRateField.getText());

            if (code.isEmpty() || name.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "Code and Name fields are required",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Connection conn = DatabaseManager.getInstance().getConnection();
            DatabaseManager.beginTransaction(conn);
            try {
            int productId = product == null ? 0 : product.getId();
            int oldQuantity = 0;
            if (product != null) {
                try (PreparedStatement previous = conn.prepareStatement("SELECT quantity FROM products WHERE id=?")) {
                    previous.setInt(1,productId);
                    try (ResultSet r = previous.executeQuery()) {
                        if (!r.next()) throw new SQLException("Product no longer exists");
                        oldQuantity = r.getInt(1);
                    }
                }
            }
            if (product == null) { // New product
                String query = """
                    INSERT INTO products (code, name, description, price, quantity,
                        category, alternative_sku, weight, unit_of_measure,
                        acquisition_cost, active, supplier_id, warehouse_position, vat_rate)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setString(1, code);
                    pstmt.setString(2, name);
                    pstmt.setString(3, description);
                    pstmt.setDouble(4, price);
                    pstmt.setInt(5, quantity);
                    pstmt.setString(6, category);
                    pstmt.setString(7, alternativeSku);
                    pstmt.setDouble(8, weight);
                    pstmt.setString(9, unitOfMeasure);
                    pstmt.setDouble(10, acquisitionCost);
                    pstmt.setInt(11, active ? 1 : 0);
                    if (supplierId != null) {
                        pstmt.setInt(12, supplierId);
                    } else {
                        pstmt.setNull(12, java.sql.Types.INTEGER);
                    }
                    pstmt.setString(13, warehousePosition);
                    pstmt.setDouble(14, vatRate);
                    pstmt.executeUpdate();
                    try (ResultSet keys = pstmt.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Missing product identifier");
                        productId = keys.getInt(1);
                    }
                }
            } else { // Edit product
                String query = """
                    UPDATE products
                    SET code = ?, name = ?, description = ?, price = ?, quantity = ?,
                        category = ?, alternative_sku = ?, weight = ?, unit_of_measure = ?,
                        acquisition_cost = ?, active = ?, supplier_id = ?,
                        warehouse_position = ?, vat_rate = ?
                    WHERE id = ?
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setString(1, code);
                    pstmt.setString(2, name);
                    pstmt.setString(3, description);
                    pstmt.setDouble(4, price);
                    pstmt.setInt(5, quantity);
                    pstmt.setString(6, category);
                    pstmt.setString(7, alternativeSku);
                    pstmt.setDouble(8, weight);
                    pstmt.setString(9, unitOfMeasure);
                    pstmt.setDouble(10, acquisitionCost);
                    pstmt.setInt(11, active ? 1 : 0);
                    if (supplierId != null) {
                        pstmt.setInt(12, supplierId);
                    } else {
                        pstmt.setNull(12, java.sql.Types.INTEGER);
                    }
                    pstmt.setString(13, warehousePosition);
                    pstmt.setDouble(14, vatRate);
                    pstmt.setInt(15, product.getId());
                    pstmt.executeUpdate();
                }
            }

            long delta = (long)quantity - oldQuantity;
            if (delta != 0) {
                int change = Math.toIntExact(Math.abs(delta));
                try (PreparedStatement movement = conn.prepareStatement("INSERT INTO warehouse_movements " +
                        "(product_id,type,quantity,reason,document_type,notes) VALUES(?,?,?,?,?,?)")) {
                    movement.setInt(1,productId); movement.setString(2,delta>0?"INWARD":"OUTWARD");
                    movement.setInt(3,change); movement.setString(4,"Inventory adjustment");
                    movement.setString(5,"INVENTORY_ADJUSTMENT");
                    movement.setString(6,"Stock count: " + oldQuantity + " -> " + quantity); movement.executeUpdate();
                }
            }
            try (PreparedStatement minimum = conn.prepareStatement("""
                    INSERT INTO minimum_stock(product_id, minimum_quantity, reorder_quantity) VALUES(?,?,0)
                    ON CONFLICT(product_id) DO UPDATE SET minimum_quantity=excluded.minimum_quantity
                    """)) {
                minimum.setInt(1,productId);minimum.setInt(2,minimumQuantity);minimum.executeUpdate();
            }
            conn.commit();
            productSaved = true;
            } catch (Exception e) { conn.rollback(); throw e; }
            finally { conn.setAutoCommit(true); }
            dispose();

        } catch (IllegalArgumentException | java.text.ParseException e) {
            JOptionPane.showMessageDialog(this,
                e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error while saving product: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public boolean isProductSaved() {
        return productSaved;
    }
}
