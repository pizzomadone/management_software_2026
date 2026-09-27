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
    private JTextField preferredSupplierPriceField;
    private JCheckBox activeCheckBox;
    private JButton selectSupplierButton;
    private JButton manageSuppliersButton;
    private Supplier selectedSupplier;
    private JTextField warehousePositionField;
    private JTextField vatRateField;
    private JPanel photosPanel;
    // Working photo draft. Existing product images are not changed until Save.
    private List<String> pendingPhotoFilenames = new ArrayList<>();
    private List<String> originalPhotoFilenames = new ArrayList<>();
    private String pendingPrimaryFilename;
    private String originalPrimaryFilename;
    private boolean photosInitialized;

    private boolean productSaved = false;
    private Product product;

    public ProductDialog(JFrame parent, Product product) {
        super(parent, I18n.tr(product == null ? "New Product" : "Edit Product"), true);
        this.product = product;
        setupDialog();
        initComponents();
        if (product != null) loadProductData();
        refreshPhotosPanel();
    }

    public ProductDialog(JDialog parent, Product product) {
        super(parent, I18n.tr(product == null ? "New Product" : "Edit Product"), true);
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

        JPanel formsPanel = new JPanel(new GridBagLayout());

        JPanel productPanel = new JPanel(new GridBagLayout());
        productPanel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Product")));
        JPanel stockPanel = new JPanel(new GridBagLayout());
        stockPanel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Inventory & Pricing")));

        codeField = new JTextField(16);
        setFieldWidth(codeField, 190);
        nameField = new JTextField(16);
        setFieldWidth(nameField, 285);

        descriptionArea = new JTextArea(2, 16);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        descriptionScroll.setPreferredSize(new Dimension(285, 52));

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
        setFieldWidth(categoryComboBox, 235);

        alternativeSkuField = new JTextField(16);
        setFieldWidth(alternativeSkuField, 190);

        String[] units = {"pcs", "kg", "m", "liters", "box", "pack"};
        unitOfMeasureComboBox = new JComboBox<>(units);
        unitOfMeasureComboBox.setEditable(true);
        setFieldWidth(unitOfMeasureComboBox, 135);

        selectSupplierButton = new JButton("Click to select supplier...");
        selectSupplierButton.putClientProperty("i18n.literal", true);
        selectSupplierButton.setHorizontalAlignment(SwingConstants.LEFT);
        setFieldWidth(selectSupplierButton, 235);
        selectSupplierButton.addActionListener(e -> showSupplierSelectionDialog());

        JButton newSupplierButton = new JButton("+");
        newSupplierButton.setToolTipText(I18n.tr("New Supplier"));
        newSupplierButton.setMargin(new Insets(2, 9, 2, 9));
        newSupplierButton.addActionListener(e -> createNewSupplier());

        manageSuppliersButton = new JButton("...");
        manageSuppliersButton.setToolTipText(I18n.tr("Manage all suppliers for this product"));
        manageSuppliersButton.setMargin(new Insets(2, 9, 2, 9));
        manageSuppliersButton.setEnabled(product != null);
        manageSuppliersButton.addActionListener(e -> showProductSuppliersDialog());

        JPanel supplierActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        supplierActions.add(newSupplierButton);
        supplierActions.add(manageSuppliersButton);

        JPanel supplierPanel = new JPanel(new BorderLayout(5, 0));
        supplierPanel.add(selectSupplierButton, BorderLayout.CENTER);
        supplierPanel.add(supplierActions, BorderLayout.EAST);
        setFieldWidth(supplierPanel, 330);

        addFormRow(productPanel, 0, "Code:", codeField);
        addFormRow(productPanel, 1, "Name:", nameField);
        addFormRow(productPanel, 2, "Description:", descriptionScroll);
        addFormRow(productPanel, 3, "Category:", categoryComboBox);
        addFormRow(productPanel, 4, "Alternative SKU:", alternativeSkuField);
        addFormRow(productPanel, 5, "Unit of Measure:", unitOfMeasureComboBox);
        addFormRow(productPanel, 6, "Preferred Supplier:", supplierPanel);
        addFormFiller(productPanel, 7);

        priceField = new JTextField(10);
        setFieldWidth(priceField, 115);
        preferredSupplierPriceField = new JTextField(10);
        setFieldWidth(preferredSupplierPriceField, 115);
        preferredSupplierPriceField.setText("0.0");
        preferredSupplierPriceField.setToolTipText(I18n.tr("Current reference price from the preferred supplier. It is proposed in purchase orders but can be changed."));

        acquisitionCostField = new JTextField(10);
        setFieldWidth(acquisitionCostField, 115);
        acquisitionCostField.setText("0.0");
        acquisitionCostField.setEditable(false);
        acquisitionCostField.setToolTipText(I18n.tr("Last actual purchase cost. Historical sales retain their original FIFO cost."));
        quantitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 999999, 1));
        setFieldWidth(quantitySpinner, 110);
        minimumQuantitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 999999, 1));
        setFieldWidth(minimumQuantitySpinner, 110);
        weightField = new JTextField(9);
        setFieldWidth(weightField, 105);
        weightField.setText("0.0");
        warehousePositionField = new JTextField(12);
        setFieldWidth(warehousePositionField, 155);
        vatRateField = new JTextField(8);
        setFieldWidth(vatRateField, 90);
        vatRateField.setText(String.valueOf(SettingsPanel.getDefaultVatRate()));
        activeCheckBox = new JCheckBox("", true);

        addFormRow(stockPanel, 0, "Price:", priceField);
        addFormRow(stockPanel, 1, "Preferred Supplier Price:", withHelp(preferredSupplierPriceField,
            "Current reference price from the preferred supplier. It is proposed in purchase orders but can be changed."));
        addFormRow(stockPanel, 2, "Last Purchase Cost:", withHelp(acquisitionCostField,
            "Last actual purchase cost. Historical sales retain their original FIFO cost."));
        addFormRow(stockPanel, 3, "Quantity:", quantitySpinner);
        addFormRow(stockPanel, 4, "Minimum Quantity:", minimumQuantitySpinner);
        addFormRow(stockPanel, 5, "Weight (kg):", weightField);
        addFormRow(stockPanel, 6, "Warehouse Position:", warehousePositionField);
        addFormRow(stockPanel, 7, "VAT Rate (%):", vatRateField);
        addFormRow(stockPanel, 8, "Active:", activeCheckBox);
        addFormFiller(stockPanel, 9);

        GridBagConstraints leftSection = new GridBagConstraints();
        leftSection.gridx = 0;
        leftSection.gridy = 0;
        leftSection.weightx = 0.62;
        leftSection.weighty = 1.0;
        leftSection.fill = GridBagConstraints.BOTH;
        leftSection.insets = new Insets(0, 0, 0, 5);
        formsPanel.add(productPanel, leftSection);

        GridBagConstraints rightSection = new GridBagConstraints();
        rightSection.gridx = 1;
        rightSection.gridy = 0;
        rightSection.weightx = 0.38;
        rightSection.weighty = 1.0;
        rightSection.fill = GridBagConstraints.BOTH;
        rightSection.insets = new Insets(0, 5, 0, 0);
        formsPanel.add(stockPanel, rightSection);

        // Allow both sections to shrink with the dialog instead of clipping the
        // right side when their preferred widths are larger than the viewport.
        productPanel.setMinimumSize(new Dimension(390, productPanel.getMinimumSize().height));
        stockPanel.setMinimumSize(new Dimension(280, stockPanel.getMinimumSize().height));

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
        int width = Math.min(840, Math.max(720, usable.width - 80));
        int height = Math.min(620, Math.max(500, usable.height - 80));
        setPreferredSize(new Dimension(width, height));
        pack();
        setSize(width, height);
        setMinimumSize(new Dimension(
            Math.min(720, width), Math.min(520, height)));
        Dialogs.centerWindow(this);
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

    private JPanel withHelp(JComponent component, String tooltip) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);
        panel.add(component);
        JLabel help = new JLabel("?");
        help.setToolTipText(I18n.tr(tooltip));
        panel.add(help);
        return panel;
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

    private void initializePhotoDraft() throws SQLException {
        if (photosInitialized) return;

        pendingPhotoFilenames.clear();
        originalPhotoFilenames.clear();
        pendingPrimaryFilename = null;
        originalPrimaryFilename = null;

        if (product != null) {
            List<ProductImageStore.ProductImage> images =
                ProductImageStore.getInstance().list(product.getId());
            for (ProductImageStore.ProductImage image : images) {
                pendingPhotoFilenames.add(image.filename());
                originalPhotoFilenames.add(image.filename());
                if (image.primary()) {
                    pendingPrimaryFilename = image.filename();
                    originalPrimaryFilename = image.filename();
                }
            }
            if (pendingPrimaryFilename == null && !pendingPhotoFilenames.isEmpty()) {
                pendingPrimaryFilename = pendingPhotoFilenames.get(0);
                originalPrimaryFilename = pendingPrimaryFilename;
            }
        }
        photosInitialized = true;
    }

    private void refreshPhotosPanel() {
        if (photosPanel == null) return;
        photosPanel.removeAll();
        try {
            initializePhotoDraft();
            List<String> photos = pendingPhotos();

            JPanel gallery = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            gallery.setOpaque(false);

            for (int i = 0; i < photos.size(); i++) {
                gallery.add(photoDraftCard(photos.get(i), i, photos.size()));
            }

            if (photos.isEmpty()) {
                JLabel empty = new JLabel(I18n.tr("No photos"));
                empty.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
                gallery.add(empty);
            }

            JPanel galleryHolder = new JPanel(new BorderLayout());
            galleryHolder.setOpaque(false);
            galleryHolder.add(gallery, BorderLayout.WEST);
            photosPanel.add(galleryHolder, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            actions.setOpaque(false);
            JButton addPhoto = new JButton(I18n.tr("Add Photo"));
            addPhoto.setEnabled(photos.size() < ProductImageStore.MAX_IMAGES_PER_PRODUCT);
            addPhoto.addActionListener(e -> addPhoto());
            actions.add(addPhoto);
            actions.add(new JLabel(photos.size() + "/" + ProductImageStore.MAX_IMAGES_PER_PRODUCT));
            photosPanel.add(actions, BorderLayout.SOUTH);
        } catch (Exception e) {
            photosPanel.add(new JLabel(I18n.tr("Unable to load product photos")),
                BorderLayout.CENTER);
        }
        I18n.localize(photosPanel);
        photosPanel.revalidate();
        photosPanel.repaint();
    }

    private JPanel photoDraftCard(String filename, int index, int total) {
        JPanel card = createPhotoCardShell();
        ImageIcon icon = ProductImageStore.getInstance().icon(filename, 95);
        JLabel preview = createPhotoPreview(icon);
        card.add(preview, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        controls.setOpaque(false);

        boolean primary = filename.equals(pendingPrimaryFilename) ||
            (pendingPrimaryFilename == null && index == 0);
        if (primary) {
            JLabel primaryLabel = new JLabel("★");
            primaryLabel.setToolTipText(I18n.tr("Primary"));
            primaryLabel.setFont(primaryLabel.getFont().deriveFont(Font.BOLD));
            controls.add(primaryLabel);
        } else {
            JButton makePrimary = photoActionButton("☆", I18n.tr("Set as Primary"));
            makePrimary.addActionListener(e -> {
                pendingPrimaryFilename = filename;
                refreshPhotosPanel();
            });
            controls.add(makePrimary);
        }

        JButton left = photoActionButton("←", I18n.tr("Move Left"));
        JButton right = photoActionButton("→", I18n.tr("Move Right"));
        left.setEnabled(index > 0);
        right.setEnabled(index < total - 1);
        left.addActionListener(e -> movePending(index, -1));
        right.addActionListener(e -> movePending(index, 1));
        controls.add(left);
        controls.add(right);

        JButton delete = photoActionButton("×", I18n.tr("Delete Photo"));
        delete.getAccessibleContext().setAccessibleName(I18n.tr("Delete Photo"));
        delete.addActionListener(e -> {
            pendingPhotos().remove(filename);

            // A newly selected image is only a temporary file until Save.
            // Existing image files stay untouched so Cancel can restore the draft.
            if (!originalPhotoFilenames.contains(filename)) {
                ProductImageStore.getInstance().deleteFiles(filename);
            }

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

    private JButton photoActionButton(String symbol, String tooltip) {
        JButton button = new JButton(symbol);
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(30, 24));
        button.setMinimumSize(new Dimension(30, 24));
        button.setMaximumSize(new Dimension(30, 24));
        return button;
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
        try {
            initializePhotoDraft();
        } catch (SQLException e) {
            showPhotoError(e);
            return;
        }

        if (pendingPhotos().size() >= ProductImageStore.MAX_IMAGES_PER_PRODUCT) {
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
            String filename =
                ProductImageStore.getInstance().storeOptimizedImage(chooser.getSelectedFile());
            pendingPhotos().add(filename);
            if (pendingPhotos().size() == 1) pendingPrimaryFilename = filename;
            refreshPhotosPanel();
        } catch (Exception e) {
            showPhotoError(e);
        }
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

        loadPreferredSupplierRelation();

        warehousePositionField.setText(product.getWarehousePosition());
        vatRateField.setText(String.valueOf(product.getVatRate()));
    }

    private void loadPreferredSupplierRelation() {
        if (product == null) return;
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            try (PreparedStatement p = conn.prepareStatement("""
                    SELECT supplier_id,purchase_price
                    FROM product_suppliers
                    WHERE product_id=? AND is_preferred=1 LIMIT 1
                    """)) {
                p.setInt(1,product.getId());
                try (ResultSet r=p.executeQuery()) {
                    if (r.next()) {
                        loadSupplierById(r.getInt("supplier_id"));
                        preferredSupplierPriceField.setText(Numbers.unitText(r.getDouble("purchase_price")));
                        return;
                    }
                }
            }
        } catch (SQLException e) {
            selectSupplierButton.setText("Click to select supplier...");
        }
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
            loadSelectedSupplierPrice();
        }
    }

    private void loadSelectedSupplierPrice() {
        if (selectedSupplier == null) return;
        if (product == null) {
            if (preferredSupplierPriceField.getText().trim().isEmpty())
                preferredSupplierPriceField.setText("0.0");
            return;
        }
        try {
            Connection c=DatabaseManager.getInstance().getConnection();
            try(PreparedStatement p=c.prepareStatement("""
                    SELECT purchase_price FROM product_suppliers
                    WHERE product_id=? AND supplier_id=?
                    """)) {
                p.setInt(1,product.getId());p.setInt(2,selectedSupplier.getId());
                try(ResultSet r=p.executeQuery()) {
                    preferredSupplierPriceField.setText(r.next()
                        ? Numbers.unitText(r.getDouble(1))
                        : Numbers.unitText(product.getAcquisitionCost()));
                }
            }
        } catch(SQLException e) {
            preferredSupplierPriceField.setText(Numbers.unitText(product.getAcquisitionCost()));
        }
    }

    private void showProductSuppliersDialog() {
        if (product == null) {
            Dialogs.showMessageDialog(this,
                I18n.tr("Save the product first, then you can add additional suppliers."),
                I18n.tr("Product Suppliers"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ProductSuppliersDialog dialog=new ProductSuppliersDialog(this,product.getId(),product.getName());
        dialog.setVisible(true);
        selectedSupplier=null;
        selectSupplierButton.setText("Click to select supplier...");
        loadPreferredSupplierRelation();
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

    private boolean photoDraftChanged() {
        return !originalPhotoFilenames.equals(pendingPhotoFilenames) ||
            !java.util.Objects.equals(originalPrimaryFilename, pendingPrimaryFilename);
    }

    private void saveProduct() {
        try {
            initializePhotoDraft();
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
            boolean active = activeCheckBox.isSelected();
            Integer supplierId = selectedSupplier != null ? selectedSupplier.getId() : null;
            double preferredSupplierPrice = supplierId != null
                ? Numbers.nonNegative(preferredSupplierPriceField.getText()) : 0.0;
            // A supplier reference price is not an actual purchase. New products have
            // no last purchase cost until a supplier order is completed.
            double acquisitionCost = product == null
                ? 0.0 : Numbers.nonNegative(acquisitionCostField.getText());
            String warehousePosition = warehousePositionField.getText().trim();
            double vatRate = Numbers.vat(vatRateField.getText());

            if (code.isEmpty() || name.isEmpty()) {
                Dialogs.showMessageDialog(this, "Code and Name fields are required",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            List<String> removedOriginalPhotos = new ArrayList<>();
            if (product != null && photoDraftChanged()) {
                for (String filename : originalPhotoFilenames) {
                    if (!pendingPhotoFilenames.contains(filename)) removedOriginalPhotos.add(filename);
                }
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
                            acquisition_cost, active, warehouse_position, vat_rate)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setString(1, code); pstmt.setString(2, name); pstmt.setString(3, description);
                        pstmt.setDouble(4, price); pstmt.setInt(5, 0); pstmt.setString(6, category);
                        pstmt.setString(7, alternativeSku); pstmt.setDouble(8, weight);
                        pstmt.setString(9, unitOfMeasure); pstmt.setDouble(10, acquisitionCost);
                        pstmt.setInt(11, active ? 1 : 0);
                        pstmt.setString(12, warehousePosition); pstmt.setDouble(13, vatRate);
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
                            acquisition_cost = ?, active = ?,
                            warehouse_position = ?, vat_rate = ?
                        WHERE id = ?
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                        pstmt.setString(1, code); pstmt.setString(2, name); pstmt.setString(3, description);
                        pstmt.setDouble(4, price); pstmt.setInt(5, oldQuantity); pstmt.setString(6, category);
                        pstmt.setString(7, alternativeSku); pstmt.setDouble(8, weight);
                        pstmt.setString(9, unitOfMeasure); pstmt.setDouble(10, acquisitionCost);
                        pstmt.setInt(11, active ? 1 : 0);
                        pstmt.setString(12, warehousePosition); pstmt.setDouble(13, vatRate);
                        pstmt.setInt(14, product.getId());
                        pstmt.executeUpdate();
                    }
                }

                long delta = (long)quantity - oldQuantity;
                if (delta != 0) {
                    int intDelta=Math.toIntExact(delta);
                    InventoryLotManager.adjustProductStock(
                        conn,productId,intDelta,new java.util.Date(),acquisitionCost);
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

                savePreferredSupplierRelation(conn,productId,supplierId,preferredSupplierPrice);

                if (product == null) {
                    if (pendingPhotoFilenames != null && !pendingPhotoFilenames.isEmpty()) {
                        ProductImageStore.getInstance().attachStoredImages(
                            conn, productId, pendingPhotoFilenames, pendingPrimaryFilename);
                    }
                } else if (photoDraftChanged()) {
                    ProductImageStore.getInstance().replaceStoredImages(
                        conn, productId, pendingPhotoFilenames, pendingPrimaryFilename);
                }

                conn.commit();
                productSaved = true;

                // Database references are committed first. Files removed from an
                // existing product are deleted only now, never while editing.
                if (!removedOriginalPhotos.isEmpty()) {
                    ProductImageStore.getInstance().deleteNamedFiles(removedOriginalPhotos);
                }

                if (pendingPhotoFilenames != null) pendingPhotoFilenames.clear();
                originalPhotoFilenames.clear();
                pendingPrimaryFilename = null;
                originalPrimaryFilename = null;
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

    private void savePreferredSupplierRelation(Connection c,int productId,Integer supplierId,
                                               double purchasePrice) throws SQLException {
        if(supplierId==null)return;
        try(PreparedStatement clear=c.prepareStatement(
                "UPDATE product_suppliers SET is_preferred=0 WHERE product_id=? AND supplier_id<>?")){
            clear.setInt(1,productId);clear.setInt(2,supplierId);clear.executeUpdate();
        }
        try(PreparedStatement upsert=c.prepareStatement("""
                INSERT INTO product_suppliers(product_id,supplier_id,purchase_price,is_preferred)
                VALUES(?,?,?,1)
                ON CONFLICT(product_id,supplier_id) DO UPDATE SET
                    purchase_price=excluded.purchase_price,is_preferred=1
                """)){
            upsert.setInt(1,productId);upsert.setInt(2,supplierId);
            upsert.setDouble(3,purchasePrice);upsert.executeUpdate();
        }
    }

    @Override
    public void dispose() {
        if (!productSaved && pendingPhotoFilenames != null) {
            List<String> temporary = new ArrayList<>();
            for (String filename : pendingPhotoFilenames) {
                if (!originalPhotoFilenames.contains(filename)) temporary.add(filename);
            }
            ProductImageStore.getInstance().deleteNamedFiles(temporary);
            pendingPhotoFilenames.clear();
        }
        super.dispose();
    }

    public boolean isProductSaved() { return productSaved; }
}
