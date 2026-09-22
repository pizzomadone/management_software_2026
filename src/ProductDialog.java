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
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

        JPanel formsPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        JPanel productPanel = new JPanel(new GridBagLayout());
        productPanel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Product")));
        JPanel stockPanel = new JPanel(new GridBagLayout());
        stockPanel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Inventory & Pricing")));

        codeField = new JTextField(16);\n        setFieldWidth(codeField, 190);
        nameField = new JTextField(16);\n        setFieldWidth(nameField, 315);

        descriptionArea = new JTextArea(2, 16);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        descriptionScroll.setPreferredSize(new Dimension(315, 52));

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
        categoryComboBox.setEditable(true);\n        setFieldWidth(categoryComboBox, 260);

        alternativeSkuField = new JTextField(16);\n        setFieldWidth(alternativeSkuField, 190);

        String[] units = {"pcs", "kg", "m", "liters", "box", "pack"};
        unitOfMeasureComboBox = new JComboBox<>(units);
        unitOfMeasureComboBox.setEditable(true);\n        setFieldWidth(unitOfMeasureComboBox, 135);

        selectSupplierButton = new JButton("Click to select supplier...");
        selectSupplierButton.putClientProperty("i18n.literal", true);
        selectSupplierButton.setHorizontalAlignment(SwingConstants.LEFT);\n        setFieldWidth(selectSupplierButton, 260);
        selectSupplierButton.addActionListener(e -> showSupplierSelectionDialog());

        JButton newSupplierButton = new JButton("+");
        newSupplierButton.setToolTipText(I18n.tr("New Supplier"));
        newSupplierButton.setMargin(new Insets(2, 9, 2, 9));
        newSupplierButton.addActionListener(e -> createNewSupplier());

        JPanel supplierPanel = new JPanel(new BorderLayout(5, 0));\n        setFieldWidth(supplierPanel, 310);
        supplierPanel.add(selectSupplierButton, BorderLayout.CENTER);
        supplierPanel.add(newSupplierButton, BorderLayout.EAST);

        addFormRow(productPanel, 0, "Code:", codeField);
        addFormRow(productPanel, 1, "Name:", nameField);
        addFormRow(productPanel, 2, "Description:", descriptionScroll);
        addFormRow(productPanel, 3, "Category:", categoryComboBox);
        addFormRow(productPanel, 4, "Alternative SKU:", alternativeSkuField);
        addFormRow(productPanel, 5, "Unit of Measure:", unitOfMeasureComboBox);
        addFormRow(productPanel, 6, "Supplier:", supplierPanel);
        addFormFiller(productPanel, 7);

        priceField = new JTextField(10);\n        setFieldWidth(priceField, 115);
        acquisitionCostField = new JTextField(10);\n        setFieldWidth(acquisitionCostField, 115);
        acquisitionCostField.setText("0.0");
        quantitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 999999, 1));\n        setFieldWidth(quantitySpinner, 110);
        minimumQuantitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 999999, 1));\n        setFieldWidth(minimumQuantitySpinner, 110);
        weightField = new JTextField(9);\n        setFieldWidth(weightField, 105);
        weightField.setText("0.0");
        warehousePositionField = new JTextField(12);\n        setFieldWidth(warehousePositionField, 155);
        vatRateField = new JTextField(8);\n        setFieldWidth(vatRateField, 90);
        vatRateField.setText(String.valueOf(SettingsPanel.getDefaultVatRate()));
        activeCheckBox = new JCheckBox("", true);

        addFormRow(stockPanel, 0, "Price:", priceField);
        addFormRow(stockPanel, 1, "Acquisition Cost:", acquisitionCostField);
        addFormRow(stockPanel, 2, "Quantity:", quantitySpinner);
        addFormRow(stockPanel, 3, "Minimum Quantity:", minimumQuantitySpinner);
        addFormRow(stockPanel, 4, "Weight (kg):", weightField);
        addFormRow(stockPanel, 5, "Warehouse Position:", warehousePositionField);
        addFormRow(stockPanel, 6, "VAT Rate (%):", vatRateField);
        addFormRow(stockPanel, 7, "Active:", activeCheckBox);
        addFormFiller(stockPanel, 8);

        formsPanel.add(productPanel);
        formsPanel.add(stockPanel);
        content.add(formsPanel, BorderLayout.NORTH);

        photosPanel = new JPanel(new BorderLayout(0, 4));
        photosPanel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Photos")));
        photosPanel.setPreferredSize(new Dimension(760, 185));
        content.add(photosPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        saveButton.addActionListener(e -> saveProduct());
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        JScrollPane scroll = new JScrollPane(content,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(scroll, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getMaximumWindowBounds();
        int width = Math.min(820, Math.max(700, usable.width - 80));
        int height = Math.min(620, Math.max(500, usable.height - 80));
        setPreferredSize(new Dimension(width, height));
        pack();
        setSize(width, height);
        setMinimumSize(new Dimension(
            Math.min(700, width), Math.min(520, height)));
        setLocationRelativeTo(getOwner());
        I18n.localize(this);
    }

    private void addFormRow(JPanel panel, int row, String labelText, Component component) {
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.gridy = row;
        label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(4, 6, 4, 8);
        panel.add(new JLabel(labelText), label);

        GridBagConstraints field = new GridBagConstraints();
        field.gridx = 1;
        field.gridy = row;
        field.weightx = 1.0;
        field.anchor = GridBagConstraints.WEST;
        field.fill = GridBagConstraints.NONE;
        field.insets = new Insets(4, 0, 4, 6);
        panel.add(component, field);
    }

    private void setFieldWidth(JComponent component, int width) {
        Dimension preferred = component.getPreferredSize();
        component.setPreferredSize(new Dimension(width, preferred.height));
    }

    private void addFormFiller(JPanel panel, int row) {
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = row;
        filler.gridwidth = 2;
        filler.weighty = 1.0;
        filler.fill = GridBagConstraints.VERTICAL;
        panel.add(Box.createVerticalGlue(), filler);
    }

    private void refreshPhotosPanel() {
        if (photosPanel == null) return;
        photosPanel.removeAll();
        try {
            List<ProductImageStore.ProductImage> existing =
                product == null ? List.of() : ProductImageStore.getInstance().list(product.getId());
            List<String> pending = pendingPhotos();

            JPanel gallery = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            gallery.setOpaque(false);

            for (int i = 0; i < existing.size(); i++) {
                ProductImageStore.ProductImage image = existing.get(i);
                gallery.add(existingPhotoCard(image, i, existing.size()));
            }
            for (int i = 0; i < pending.size(); i++) {
                gallery.add(pendingPhotoCard(pending.get(i), i, pending.size()));
            }

            if (existing.isEmpty() && pending.isEmpty()) {
                JLabel empty = new JLabel(I18n.tr("No photos"));
                empty.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
                gallery.add(empty);
            }

            JPanel galleryHolder = new JPanel(new BorderLayout());
            galleryHolder.setOpaque(false);
            galleryHolder.add(gallery, BorderLayout.WEST);
            photosPanel.add(galleryHolder, BorderLayout.CENTER);

            int count = existing.size() + pending.size();
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            actions.setOpaque(false);
            JButton addPhoto = new JButton(I18n.tr("Add Photo"));
            addPhoto.setEnabled(count < ProductImageStore.MAX_IMAGES_PER_PRODUCT);
            addPhoto.addActionListener(e -> addPhoto());
            actions.add(addPhoto);
            actions.add(new JLabel(count + "/" + ProductImageStore.MAX_IMAGES_PER_PRODUCT));
            photosPanel.add(actions, BorderLayout.SOUTH);
        } catch (Exception e) {
            photosPanel.add(new JLabel(I18n.tr("Unable to load product photos")),
                BorderLayout.CENTER);
        }
        I18n.localize(photosPanel);
        photosPanel.revalidate();
        photosPanel.repaint();
    }

    private JPanel existingPhotoCard(ProductImageStore.ProductImage image, int index, int total) {
        JPanel card = createPhotoCardShell();
        ImageIcon icon = ProductImageStore.getInstance().icon(image.filename(), 95);
        JLabel preview = createPhotoPreview(icon);
        card.add(preview, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 2));
        controls.setOpaque(false);

        if (image.primary()) {
            JLabel primaryLabel = new JLabel(I18n.tr("Primary"));
            primaryLabel.setFont(primaryLabel.getFont().deriveFont(Font.BOLD));
            controls.add(primaryLabel);
        } else {
            JButton primary = compactButton(I18n.tr("Set as Primary"));
            primary.addActionListener(e -> runPhotoAction(() ->
                ProductImageStore.getInstance().setPrimary(image.productId(), image.id())));
            controls.add(primary);
        }

        JButton left = compactButton("←");
        JButton right = compactButton("→");
        left.setToolTipText(I18n.tr("Move Left"));
        right.setToolTipText(I18n.tr("Move Right"));
        left.setEnabled(index > 0);
        right.setEnabled(index < total - 1);
        left.addActionListener(e -> runPhotoAction(() ->
            ProductImageStore.getInstance().move(image.productId(), image.id(), -1)));
        right.addActionListener(e -> runPhotoAction(() ->
            ProductImageStore.getInstance().move(image.productId(), image.id(), 1)));
        controls.add(left);
        controls.add(right);

        JButton delete = compactButton(I18n.tr("Delete"));
        delete.setToolTipText(I18n.tr("Delete Photo"));
        delete.addActionListener(e -> runPhotoAction(() ->
            ProductImageStore.getInstance().deleteImage(image.productId(), image.id())));
        controls.add(delete);

        card.add(controls, BorderLayout.SOUTH);
        return card;
    }

    private JPanel pendingPhotoCard(String filename, int index, int total) {
        JPanel card = createPhotoCardShell();
        ImageIcon icon = ProductImageStore.getInstance().icon(filename, 95);
        JLabel preview = createPhotoPreview(icon);
        card.add(preview, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 2));
        controls.setOpaque(false);

        boolean primary = filename.equals(pendingPrimaryFilename) ||
            (pendingPrimaryFilename == null && index == 0);
        if (primary) {
            JLabel primaryLabel = new JLabel(I18n.tr("Primary"));
            primaryLabel.setFont(primaryLabel.getFont().deriveFont(Font.BOLD));
            controls.add(primaryLabel);
        } else {
            JButton makePrimary = compactButton(I18n.tr("Set as Primary"));
            makePrimary.addActionListener(e -> {
                pendingPrimaryFilename = filename;
                refreshPhotosPanel();
            });
            controls.add(makePrimary);
        }

        JButton left = compactButton("←");
        JButton right = compactButton("→");
        left.setToolTipText(I18n.tr("Move Left"));
        right.setToolTipText(I18n.tr("Move Right"));
        left.setEnabled(index > 0);
        right.setEnabled(index < total - 1);
        left.addActionListener(e -> movePending(index, -1));
        right.addActionListener(e -> movePending(index, 1));
        controls.add(left);
        controls.add(right);

        JButton delete = compactButton(I18n.tr("Delete"));
        delete.setToolTipText(I18n.tr("Delete Photo"));
        delete.addActionListener(e -> {
            pendingPhotos().remove(filename);
            ProductImageStore.getInstance().deleteFiles(filename);
            if (filename.equals(pendingPrimaryFilename)) {
                pendingPrimaryFilename = pendingPhotos().isEmpty()
                    ? null : pendingPhotos().get(0);
            }
            refreshPhotosPanel();
        });
        controls.add(delete);

        card.add(controls, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createPhotoCardShell() {
        JPanel card = new JPanel(new BorderLayout(3, 3));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEtchedBorder(),
            BorderFactory.createEmptyBorder(4, 4, 3, 4)));
        card.setPreferredSize(new Dimension(225, 125));
        return card;
    }

    private JLabel createPhotoPreview(ImageIcon icon) {
        JLabel preview = new JLabel(icon);
        preview.setPreferredSize(new Dimension(205, 82));
        preview.setHorizontalAlignment(SwingConstants.CENTER);
        preview.setVerticalAlignment(SwingConstants.CENTER);
        return preview;
    }

    private JButton compactButton(String text) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(2, 6, 2, 6));
        return button;
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
