import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductDialog extends JDialog {
    private JTextField codeField;
    private JTextField nameField;
    private JTextArea descriptionArea;
    private JTextField priceField;
    private JSpinner quantitySpinner;
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
    private JPanel photosPanel;
    private List<String> pendingPhotoFilenames = new ArrayList<>();
    private String pendingPrimaryFilename;

    private boolean productSaved = false;
    private Product product;

    public ProductDialog(JFrame parent, Product product) {
        super(parent, product == null ? "New Product" : "Edit Product", true);
        this.product = product;
        setupDialog();
        initComponents();
        if (product != null) loadProductData();
        refreshPhotosPanel();
    }

    public ProductDialog(JDialog parent, Product product) {
        super(parent, product == null ? "New Product" : "Edit Product", true);
        this.product = product;
        setupDialog();
        initComponents();
        if (product != null) loadProductData();
        refreshPhotosPanel();
    }

    private void setupDialog() {
        setLayout(new BorderLayout(5, 5));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    private void initComponents() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Code:"), gbc);
        gbc.gridx = 1;
        codeField = new JTextField(12);
        formPanel.add(codeField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        nameField = new JTextField(12);
        formPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1;
        descriptionArea = new JTextArea(3, 12);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        formPanel.add(new JScrollPane(descriptionArea), gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Price:"), gbc);
        gbc.gridx = 1;
        priceField = new JTextField(12);
        formPanel.add(priceField, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("Quantity:"), gbc);
        gbc.gridx = 1;
        quantitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 999999, 1));
        formPanel.add(quantitySpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        formPanel.add(new JLabel("Category:"), gbc);
        gbc.gridx = 1;
        String[] categories = {"", "Electronics", "Computers & Accessories", "Smartphones & Tablets",
            "Furniture", "Office Furniture", "Home Furniture", "Clothing", "Men's Clothing",
            "Women's Clothing", "Children's Clothing", "Food & Beverages", "Fresh Food",
            "Packaged Food", "Beverages", "Tools & Hardware", "Power Tools", "Hand Tools",
            "Hardware", "Office Supplies", "Stationery", "Paper Products", "Writing Instruments",
            "Books & Media", "Books", "Music", "Movies", "Sports & Outdoors", "Fitness Equipment",
            "Outdoor Gear", "Health & Beauty", "Personal Care", "Cosmetics", "Health Products",
            "Automotive", "Car Parts", "Car Accessories", "Home & Garden", "Kitchen", "Bathroom",
            "Garden", "Toys & Games", "Toys", "Board Games", "Video Games", "Pet Supplies",
            "Pet Food", "Pet Accessories", "Industrial & Scientific", "Industrial Equipment",
            "Lab Supplies", "Other"};
        categoryComboBox = new JComboBox<>(categories);
        categoryComboBox.setEditable(true);
        formPanel.add(categoryComboBox, gbc);

        gbc.gridx = 0; gbc.gridy = 6;
        formPanel.add(new JLabel("Alternative SKU:"), gbc);
        gbc.gridx = 1;
        alternativeSkuField = new JTextField(12);
        formPanel.add(alternativeSkuField, gbc);

        gbc.gridx = 0; gbc.gridy = 7;
        formPanel.add(new JLabel("Weight (kg):"), gbc);
        gbc.gridx = 1;
        weightField = new JTextField(12);
        weightField.setText("0.0");
        formPanel.add(weightField, gbc);

        gbc.gridx = 0; gbc.gridy = 8;
        formPanel.add(new JLabel("Unit of Measure:"), gbc);
        gbc.gridx = 1;
        String[] units = {"pcs", "kg", "m", "liters", "box", "pack"};
        unitOfMeasureComboBox = new JComboBox<>(units);
        unitOfMeasureComboBox.setEditable(true);
        formPanel.add(unitOfMeasureComboBox, gbc);

        gbc.gridx = 0; gbc.gridy = 9;
        formPanel.add(new JLabel("Minimum Quantity:"), gbc);
        gbc.gridx = 1;
        minimumQuantitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 999999, 1));
        formPanel.add(minimumQuantitySpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 10;
        formPanel.add(new JLabel("Acquisition Cost:"), gbc);
        gbc.gridx = 1;
        acquisitionCostField = new JTextField(12);
        acquisitionCostField.setText("0.0");
        formPanel.add(acquisitionCostField, gbc);

        gbc.gridx = 0; gbc.gridy = 11;
        formPanel.add(new JLabel("Active:"), gbc);
        gbc.gridx = 1;
        activeCheckBox = new JCheckBox("", true);
        formPanel.add(activeCheckBox, gbc);

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

        gbc.gridx = 0; gbc.gridy = 13;
        formPanel.add(new JLabel("Warehouse Position:"), gbc);
        gbc.gridx = 1;
        warehousePositionField = new JTextField(12);
        formPanel.add(warehousePositionField, gbc);

        gbc.gridx = 0; gbc.gridy = 14;
        formPanel.add(new JLabel("VAT Rate (%):"), gbc);
        gbc.gridx = 1;
        vatRateField = new JTextField(12);
        vatRateField.setText(String.valueOf(SettingsPanel.getDefaultVatRate()));
        formPanel.add(vatRateField, gbc);

        gbc.gridx = 0; gbc.gridy = 15; gbc.gridwidth = 3; gbc.weightx = 1;
        photosPanel = new JPanel();
        photosPanel.setLayout(new BoxLayout(photosPanel, BoxLayout.Y_AXIS));
        photosPanel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Photos")));
        formPanel.add(photosPanel, gbc);
        gbc.gridwidth = 1;

        JPanel buttonPanel = new JPanel();
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        saveButton.addActionListener(e -> saveProduct());
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        JScrollPane scroll = new JScrollPane(formPanel);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(720, 780));
        pack();
        setLocationRelativeTo(null);
    }

    private void refreshPhotosPanel() {
        if (photosPanel == null) return;
        photosPanel.removeAll();
        try {
            List<ProductImageStore.ProductImage> existing =
                product == null ? List.of() : ProductImageStore.getInstance().list(product.getId());

            JPanel gallery = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
            for (int i = 0; i < existing.size(); i++) {
                ProductImageStore.ProductImage image = existing.get(i);
                gallery.add(existingPhotoCard(image, i, existing.size()));
            }
            List<String> pending = pendingPhotos();
            for (int i = 0; i < pending.size(); i++) {
                gallery.add(pendingPhotoCard(pending.get(i), i, pending.size()));
            }

            if (existing.isEmpty() && pending.isEmpty()) gallery.add(new JLabel(I18n.tr("No photos")));
            photosPanel.add(gallery);

            int count = existing.size() + pending.size();
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton addPhoto = new JButton(I18n.tr("Add Photo"));
            addPhoto.setEnabled(count < ProductImageStore.MAX_IMAGES_PER_PRODUCT);
            addPhoto.addActionListener(e -> addPhoto());
            actions.add(addPhoto);
            actions.add(new JLabel(count + "/" + ProductImageStore.MAX_IMAGES_PER_PRODUCT));
            photosPanel.add(actions);
        } catch (Exception e) {
            photosPanel.add(new JLabel(I18n.tr("Unable to load product photos")));
        }
        I18n.localize(photosPanel);
        photosPanel.revalidate();
        photosPanel.repaint();
    }

    private JPanel existingPhotoCard(ProductImageStore.ProductImage image, int index, int total) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBorder(BorderFactory.createEtchedBorder());
        ImageIcon icon = ProductImageStore.getInstance().icon(image.filename(), 120);
        JLabel preview = new JLabel(icon);
        preview.setPreferredSize(new Dimension(130, 100));
        preview.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(preview, BorderLayout.CENTER);

        JPanel controls = new JPanel(new GridLayout(0, 1, 2, 2));
        JButton primary = new JButton(I18n.tr(image.primary() ? "Primary" : "Set as Primary"));
        primary.setEnabled(!image.primary());
        primary.addActionListener(e -> runPhotoAction(() ->
            ProductImageStore.getInstance().setPrimary(image.productId(), image.id())));
        controls.add(primary);

        JPanel order = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        JButton left = new JButton("←");
        JButton right = new JButton("→");
        left.setToolTipText(I18n.tr("Move Left"));
        right.setToolTipText(I18n.tr("Move Right"));
        left.setEnabled(index > 0);
        right.setEnabled(index < total - 1);
        left.addActionListener(e -> runPhotoAction(() ->
            ProductImageStore.getInstance().move(image.productId(), image.id(), -1)));
        right.addActionListener(e -> runPhotoAction(() ->
            ProductImageStore.getInstance().move(image.productId(), image.id(), 1)));
        order.add(left); order.add(right);
        controls.add(order);

        JButton delete = new JButton("Delete Photo");
        delete.addActionListener(e -> runPhotoAction(() ->
            ProductImageStore.getInstance().deleteImage(image.productId(), image.id())));
        controls.add(delete);
        card.add(controls, BorderLayout.SOUTH);
        return card;
    }

    private JPanel pendingPhotoCard(String filename, int index, int total) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBorder(BorderFactory.createEtchedBorder());
        ImageIcon icon = ProductImageStore.getInstance().icon(filename, 120);
        JLabel preview = new JLabel(icon);
        preview.setPreferredSize(new Dimension(130, 100));
        preview.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(preview, BorderLayout.CENTER);

        JPanel controls = new JPanel(new GridLayout(0, 1, 2, 2));
        boolean primary = filename.equals(pendingPrimaryFilename) ||
            (pendingPrimaryFilename == null && index == 0);
        JButton makePrimary = new JButton(I18n.tr(primary ? "Primary" : "Set as Primary"));
        makePrimary.setEnabled(!primary);
        makePrimary.addActionListener(e -> {
            pendingPrimaryFilename = filename;
            refreshPhotosPanel();
        });
        controls.add(makePrimary);

        JPanel order = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        JButton left = new JButton("←");
        JButton right = new JButton("→");
        left.setToolTipText("Move Left");
        right.setToolTipText("Move Right");
        left.setEnabled(index > 0);
        right.setEnabled(index < total - 1);
        left.addActionListener(e -> movePending(index, -1));
        right.addActionListener(e -> movePending(index, 1));
        order.add(left); order.add(right);
        controls.add(order);

        JButton delete = new JButton("Delete Photo");
        delete.addActionListener(e -> {
            pendingPhotos().remove(filename);
            ProductImageStore.getInstance().deleteFiles(filename);
            if (filename.equals(pendingPrimaryFilename)) pendingPrimaryFilename =
                pendingPhotos().isEmpty() ? null : pendingPhotos().get(0);
            refreshPhotosPanel();
        });
        controls.add(delete);
        card.add(controls, BorderLayout.SOUTH);
        return card;
    }

    private void movePending(int index, int direction) {
        int other = index + direction;
        List<String> pending = pendingPhotos();
        if (other < 0 || other >= pending.size()) return;
        String value = pending.remove(index);
        pending.add(other, value);
        refreshPhotosPanel();
    }

    private void addPhoto() {
        int existing = 0;
        try { if (product != null) existing = ProductImageStore.getInstance().list(product.getId()).size(); }
        catch (SQLException e) { showPhotoError(e); return; }
        if (existing + pendingPhotos().size() >= ProductImageStore.MAX_IMAGES_PER_PRODUCT) {
            Dialogs.showMessageDialog(this, I18n.tr("Maximum three product photos"),
                I18n.tr("Photos"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.tr("Add Photo"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            I18n.tr("Image files"), "jpg", "jpeg", "png", "gif", "bmp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try {
            if (product == null) {
                String filename = ProductImageStore.getInstance().storeOptimizedImage(chooser.getSelectedFile());
                pendingPhotos().add(filename);
                if (pendingPhotos().size() == 1) pendingPrimaryFilename = filename;
            } else {
                ProductImageStore.getInstance().addImage(product.getId(), chooser.getSelectedFile(), false);
            }
            refreshPhotosPanel();
        } catch (Exception e) { showPhotoError(e); }
    }

    @FunctionalInterface
    private interface PhotoAction { void run() throws Exception; }

    private void runPhotoAction(PhotoAction action) {
        try {
            action.run();
            refreshPhotosPanel();
        } catch (Exception e) { showPhotoError(e); }
    }

    private void showPhotoError(Exception e) {
        Dialogs.showMessageDialog(this,
            I18n.tr("Unable to update product photos") + ": " + e.getMessage(),
            I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
    }

    private List<String> pendingPhotos() {
        if (pendingPhotoFilenames == null) pendingPhotoFilenames = new ArrayList<>();
        return pendingPhotoFilenames;
    }

    private void loadProductData() {
        codeField.setText(product.getCode());
        nameField.setText(product.getName());
        descriptionArea.setText(product.getDescription());
        priceField.setText(String.valueOf(product.getPrice()));
        quantitySpinner.setValue(product.getQuantity());
        categoryComboBox.setSelectedItem(product.getCategory());
        alternativeSkuField.setText(product.getAlternativeSku());
        weightField.setText(String.valueOf(product.getWeight()));
        unitOfMeasureComboBox.setSelectedItem(product.getUnitOfMeasure());
        minimumQuantitySpinner.setValue(product.getMinimumQuantity());
        acquisitionCostField.setText(String.valueOf(product.getAcquisitionCost()));
        activeCheckBox.setSelected(product.isActive());

        Integer supplierId = product.getSupplierId();
        if (supplierId != null && supplierId > 0) loadSupplierById(supplierId);

        warehousePositionField.setText(product.getWarehousePosition());
        vatRateField.setText(String.valueOf(product.getVatRate()));
    }

    private void loadSupplierById(Integer supplierId) {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM suppliers WHERE id = ?")) {
                pstmt.setInt(1, supplierId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        selectedSupplier = new Supplier(
                            rs.getInt("id"), rs.getString("company_name"), rs.getString("vat_number"),
                            rs.getString("tax_code"), rs.getString("address"), rs.getString("phone"),
                            rs.getString("email"), rs.getString("certified_email"), rs.getString("website"),
                            rs.getString("notes"));
                        updateSupplierButton();
                    }
                }
            }
        } catch (SQLException e) {
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
            if (selectedSupplier.getVatNumber() != null && !selectedSupplier.getVatNumber().isEmpty())
                buttonText += " (VAT: " + selectedSupplier.getVatNumber() + ")";
            selectSupplierButton.setText(buttonText);
            selectSupplierButton.setToolTipText("Supplier: " + buttonText);
        }
    }

    private void createNewSupplier() {
        SupplierDialog dialog = new SupplierDialog(this, null);
        dialog.setVisible(true);
        if (dialog.isSupplierSaved()) {
            Dialogs.showMessageDialog(this,
                "Supplier created successfully. Please select it from the list.",
                "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void saveProduct() {
        try {
            quantitySpinner.commitEdit();
            minimumQuantitySpinner.commitEdit();

            String code = codeField.getText().trim();
            String name = nameField.getText().trim();
            String description = descriptionArea.getText().trim();
            double price = Numbers.nonNegative(priceField.getText());
            int quantity = (int)quantitySpinner.getValue();
            String category = String.valueOf(categoryComboBox.getSelectedItem());
            String alternativeSku = alternativeSkuField.getText().trim();
            double weight = Numbers.nonNegative(weightField.getText());
            String unitOfMeasure = String.valueOf(unitOfMeasureComboBox.getSelectedItem());
            int minimumQuantity = Numbers.quantity(minimumQuantitySpinner.getValue());
            if (minimumQuantity < 0) throw new IllegalArgumentException("Minimum stock cannot be negative");
            double acquisitionCost = Numbers.nonNegative(acquisitionCostField.getText());
            boolean active = activeCheckBox.isSelected();
            Integer supplierId = selectedSupplier != null ? selectedSupplier.getId() : null;
            String warehousePosition = warehousePositionField.getText().trim();
            double vatRate = Numbers.vat(vatRateField.getText());

            if (code.isEmpty() || name.isEmpty()) {
                Dialogs.showMessageDialog(this, "Code and Name fields are required",
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

                if (product == null) {
                    String query = """
                        INSERT INTO products (code, name, description, price, quantity,
                            category, alternative_sku, weight, unit_of_measure,
                            acquisition_cost, active, supplier_id, warehouse_position, vat_rate)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setString(1, code); pstmt.setString(2, name); pstmt.setString(3, description);
                        pstmt.setDouble(4, price); pstmt.setInt(5, quantity); pstmt.setString(6, category);
                        pstmt.setString(7, alternativeSku); pstmt.setDouble(8, weight);
                        pstmt.setString(9, unitOfMeasure); pstmt.setDouble(10, acquisitionCost);
                        pstmt.setInt(11, active ? 1 : 0);
                        if (supplierId != null) pstmt.setInt(12, supplierId);
                        else pstmt.setNull(12, Types.INTEGER);
                        pstmt.setString(13, warehousePosition); pstmt.setDouble(14, vatRate);
                        pstmt.executeUpdate();
                        try (ResultSet keys = pstmt.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Missing product identifier");
                            productId = keys.getInt(1);
                        }
                    }
                } else {
                    String query = """
                        UPDATE products
                        SET code = ?, name = ?, description = ?, price = ?, quantity = ?,
                            category = ?, alternative_sku = ?, weight = ?, unit_of_measure = ?,
                            acquisition_cost = ?, active = ?, supplier_id = ?,
                            warehouse_position = ?, vat_rate = ?
                        WHERE id = ?
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                        pstmt.setString(1, code); pstmt.setString(2, name); pstmt.setString(3, description);
                        pstmt.setDouble(4, price); pstmt.setInt(5, quantity); pstmt.setString(6, category);
                        pstmt.setString(7, alternativeSku); pstmt.setDouble(8, weight);
                        pstmt.setString(9, unitOfMeasure); pstmt.setDouble(10, acquisitionCost);
                        pstmt.setInt(11, active ? 1 : 0);
                        if (supplierId != null) pstmt.setInt(12, supplierId);
                        else pstmt.setNull(12, Types.INTEGER);
                        pstmt.setString(13, warehousePosition); pstmt.setDouble(14, vatRate);
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
                        movement.setString(6,"Stock count: " + oldQuantity + " -> " + quantity);
                        movement.executeUpdate();
                    }
                }

                try (PreparedStatement minimum = conn.prepareStatement("""
                        INSERT INTO minimum_stock(product_id, minimum_quantity, reorder_quantity) VALUES(?,?,0)
                        ON CONFLICT(product_id) DO UPDATE SET minimum_quantity=excluded.minimum_quantity
                        """)) {
                    minimum.setInt(1,productId); minimum.setInt(2,minimumQuantity); minimum.executeUpdate();
                }

                if (product == null && pendingPhotoFilenames != null && !pendingPhotoFilenames.isEmpty()) {
                    ProductImageStore.getInstance().attachStoredImages(
                        conn, productId, pendingPhotoFilenames, pendingPrimaryFilename);
                }

                conn.commit();
                productSaved = true;
                if (pendingPhotoFilenames != null) pendingPhotoFilenames.clear();
                pendingPrimaryFilename = null;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally { conn.setAutoCommit(true); }

            dispose();
        } catch (IllegalArgumentException | java.text.ParseException e) {
            Dialogs.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            Dialogs.showMessageDialog(this,
                "Error while saving product: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void dispose() {
        if (!productSaved && product == null && pendingPhotoFilenames != null) {
            ProductImageStore.getInstance().deleteNamedFiles(new ArrayList<>(pendingPhotoFilenames));
            pendingPhotoFilenames.clear();
        }
        super.dispose();
    }

    public boolean isProductSaved() { return productSaved; }
}
