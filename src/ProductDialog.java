import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductDialog extends JDialog {
    private static final String PHOTO_DIRECTORY_SETTING = "products.last_photo_directory";

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
    private JLabel lastPurchaseCostValueLabel;
    private JButton preferredSupplierCostButton;
    private Integer preferredSupplierRelationId;
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
        fitDialogToContent();
    }

    public ProductDialog(JDialog parent, Product product) {
        super(parent, I18n.tr(product == null ? "New Product" : "Edit Product"), true);
        this.product = product;
        setupDialog();
        initComponents();
        if (product != null) loadProductData();
        refreshPhotosPanel();
        fitDialogToContent();
    }

    private void setupDialog() {
        setLayout(new BorderLayout(5, 5));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    private void initComponents() {
        JPanel content = new ViewportWidthPanel(new BorderLayout(10, 10));
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
        descriptionScroll.setMinimumSize(new Dimension(120, 52));

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
        installLocalizedEditableCombo(categoryComboBox, categories);
        setFieldWidth(categoryComboBox, 235);

        alternativeSkuField = new JTextField(16);
        setFieldWidth(alternativeSkuField, 190);

        String[] units = {"pcs", "kg", "m", "liters", "box", "pack"};
        unitOfMeasureComboBox = new JComboBox<>(units);
        unitOfMeasureComboBox.setEditable(true);
        installLocalizedEditableCombo(unitOfMeasureComboBox, units);
        setFieldWidth(unitOfMeasureComboBox, 135);

        selectSupplierButton = new JButton(I18n.tr("Click to select supplier..."));
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

        addFormRow(productPanel, 0, "Code *:", codeField);
        addFormRow(productPanel, 1, "Name *:", nameField);
        addFormRow(productPanel, 2, "Description:", descriptionScroll);
        addFormRow(productPanel, 3, "Category:", categoryComboBox);
        addFormRow(productPanel, 4, "Alternative SKU:", alternativeSkuField);
        addFormRow(productPanel, 5, "Unit of Measure:", unitOfMeasureComboBox);
        addFormRow(productPanel, 6, "Preferred Supplier:", supplierPanel);
        addFormFiller(productPanel, 7);

        priceField = new JTextField(10);
        setFieldWidth(priceField, 115);

        if (product == null) {
            acquisitionCostField = new JTextField(10);
            setFieldWidth(acquisitionCostField, 115);
            acquisitionCostField.setText("");
        } else {
            lastPurchaseCostValueLabel = new JLabel("—");
            lastPurchaseCostValueLabel.putClientProperty("i18n.literal", true);

            preferredSupplierCostButton = new JButton("—");
            preferredSupplierCostButton.putClientProperty("i18n.literal", true);
            preferredSupplierCostButton.setBorderPainted(false);
            preferredSupplierCostButton.setContentAreaFilled(false);
            preferredSupplierCostButton.setFocusPainted(false);
            preferredSupplierCostButton.setHorizontalAlignment(SwingConstants.LEFT);
            preferredSupplierCostButton.setMargin(new Insets(0, 0, 0, 0));
            preferredSupplierCostButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            preferredSupplierCostButton.setToolTipText(I18n.tr("Click to edit the preferred supplier cost."));
            preferredSupplierCostButton.setEnabled(false);
            preferredSupplierCostButton.addActionListener(e -> showPreferredSupplierCostEditor());
        }
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

        addFormRow(stockPanel, 0, "Sale Price incl. VAT *:", withCurrencyUnit(priceField));
        int pricingRow=1;
        if (product == null) {
            addFormRow(stockPanel, pricingRow++, "Purchase Cost *:", withCurrencyUnit(acquisitionCostField));
        } else {
            addFormRow(stockPanel, pricingRow++, "Preferred Supplier Cost:", withCurrencyUnit(preferredSupplierCostButton));
            addFormRow(stockPanel, pricingRow++, "Last Purchase Cost:",
                withPricingHelp(withCurrencyUnit(lastPurchaseCostValueLabel)));
        }
        addFormRow(stockPanel, pricingRow++, "Quantity:", quantitySpinner);
        addFormRow(stockPanel, pricingRow++, "Minimum Quantity:", minimumQuantitySpinner);
        addFormRow(stockPanel, pricingRow++, "Weight (kg):", weightField);
        addFormRow(stockPanel, pricingRow++, "Warehouse Position:", warehousePositionField);
        addFormRow(stockPanel, pricingRow++, "VAT Rate (%):", vatRateField);
        addFormRow(stockPanel, pricingRow++, "Active:", activeCheckBox);
        addFormFiller(stockPanel, pricingRow);

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
        productPanel.setMinimumSize(new Dimension(340, productPanel.getMinimumSize().height));
        stockPanel.setMinimumSize(new Dimension(250, stockPanel.getMinimumSize().height));

        content.add(formsPanel, BorderLayout.NORTH);

        photosPanel = new JPanel(new BorderLayout(0, 4));
        photosPanel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Photos")));
        photosPanel.setPreferredSize(new Dimension(760, 185));
        content.add(photosPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        JButton saveButton = new JButton(I18n.tr("Save"));
        JButton cancelButton = new JButton(I18n.tr("Cancel"));
        saveButton.addActionListener(e -> saveProduct());
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        JScrollPane scroll = new JScrollPane(content,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(content.getBackground());

        JPanel footer = new JPanel(new BorderLayout());
        footer.add(Dialogs.requiredLegendPanel(), BorderLayout.WEST);
        footer.add(buttonPanel, BorderLayout.EAST);

        add(scroll, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        // Localize before measuring: translated labels can be substantially wider
        // than their English counterparts.
        I18n.localize(this);
    }

    private void fitDialogToContent() {
        pack();

        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getMaximumWindowBounds();
        Dimension preferred = getPreferredSize();

        int maxWidth = Math.max(620, usable.width - 60);
        int maxHeight = Math.max(480, usable.height - 60);
        int width = Math.min(maxWidth, Math.max(760, preferred.width));
        int height = Math.min(maxHeight, Math.max(560, preferred.height));

        setSize(width, height);
        setMinimumSize(new Dimension(
            Math.min(680, maxWidth),
            Math.min(500, maxHeight)));
        Dialogs.centerWindow(this);
    }

    private void addFormRow(JPanel panel, int row, String labelText, Component component) {
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.gridy = row;
        label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(4, 6, 4, 8);
        String labelKey = labelText != null && labelText.endsWith(":")
            ? labelText.substring(0, labelText.length() - 1) : labelText;
        JLabel labelComponent = new JLabel(I18n.tr(labelKey) + ":");
        labelComponent.putClientProperty("i18n.literal", true);
        panel.add(labelComponent, label);

        GridBagConstraints field = new GridBagConstraints();
        field.gridx = 1;
        field.gridy = row;
        field.weightx = 1.0;
        field.anchor = GridBagConstraints.WEST;
        field.fill = GridBagConstraints.HORIZONTAL;
        field.insets = new Insets(4, 0, 4, 6);
        panel.add(component, field);
    }

    private JPanel withCurrencyUnit(JComponent component) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setOpaque(false);
        panel.add(component);
        JLabel currency = new JLabel(SettingsPanel.getCurrencyCode());
        currency.setToolTipText(SettingsPanel.getCurrency());
        panel.add(currency);
        return panel;
    }

    private JPanel withPricingHelp(JComponent component) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setOpaque(false);
        panel.add(component);
        JButton help = new JButton("?");
        help.putClientProperty("i18n.literal", true);
        help.setFont(help.getFont().deriveFont(Font.BOLD));
        help.setMargin(new Insets(0, 6, 0, 6));
        help.setFocusable(false);
        help.setToolTipText(I18n.tr("Click for help"));
        help.addActionListener(e -> showPricingHelp());
        panel.add(help);
        return panel;
    }

    private void showPricingHelp() {
        String message = I18n.tr(
                "Preferred supplier cost is the current purchase cost saved for the preferred supplier. Click the value to edit it.")
            + "\n\n" + I18n.tr(
                "When a supplier order is received, its actual unit purchase price becomes the FIFO lot cost and updates that supplier's current cost.")
            + "\n\n" + I18n.tr(
                "Last purchase cost is the unit cost of the most recently received supplier purchase. It is updated automatically and cannot be edited here.")
            + "\n\n" + I18n.tr(
                "Historical FIFO lots and completed sales keep the costs recorded when they were created.");
        Dialogs.showMessageDialog(this, message, I18n.tr("Purchase Cost Help"),
            JOptionPane.INFORMATION_MESSAGE);
    }

    private String costDisplay(double cost) {
        return cost > 0 ? Numbers.unitText(cost) : "—";
    }

    private void setFieldWidth(JComponent component, int width) {
        Dimension preferred = component.getPreferredSize();
        component.setPreferredSize(new Dimension(width, preferred.height));
        component.setMinimumSize(new Dimension(Math.min(80, width), preferred.height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
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

        if (pendingPhotoFilenames == null) pendingPhotoFilenames = new ArrayList<>();
        if (originalPhotoFilenames == null) originalPhotoFilenames = new ArrayList<>();
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
        String lastDirectory = SettingsPanel.getGlobalSetting(
            PHOTO_DIRECTORY_SETTING, "").trim();
        if (!lastDirectory.isEmpty()) {
            File directory = new File(lastDirectory);
            if (directory.isDirectory()) chooser.setCurrentDirectory(directory);
        }

        chooser.setDialogTitle(I18n.tr("Add Photo"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            I18n.tr("Image files"), "jpg", "jpeg", "png", "gif", "bmp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File selectedPhoto = chooser.getSelectedFile();
        File parentDirectory = selectedPhoto == null ? null : selectedPhoto.getParentFile();
        if (parentDirectory != null && parentDirectory.isDirectory()) {
            SettingsPanel.setGlobalSetting(
                PHOTO_DIRECTORY_SETTING, parentDirectory.getAbsolutePath());
        }

        try {
            String filename =
                ProductImageStore.getInstance().storeOptimizedImage(selectedPhoto);
            pendingPhotos().add(filename);
            if (pendingPhotos().size() == 1) pendingPrimaryFilename = filename;
            refreshPhotosPanel();
        } catch (Exception e) {
            showPhotoError(e);
        }
    }

    private void showPhotoError(Exception e) {
        String message = photoErrorMessage(e);
        Dialogs.showMessageDialog(this, message,
            I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
    }

    private String photoErrorMessage(Exception e) {
        String technical = e == null ? null : e.getMessage();
        if ("Image file not found".equals(technical))
            return I18n.tr("The selected image file could not be found.");
        if ("Unsupported or invalid image".equals(technical))
            return I18n.tr("The selected file is not a supported or valid image.");
        if ("Unable to create product image directory".equals(technical))
            return I18n.tr("The product image folder could not be created.");
        return I18n.tr("Unable to update product photos");
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
        if (lastPurchaseCostValueLabel != null)
            lastPurchaseCostValueLabel.setText(costDisplay(product.getAcquisitionCost()));
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
                    SELECT id,supplier_id,purchase_price
                    FROM product_suppliers
                    WHERE product_id=? AND is_preferred=1 LIMIT 1
                    """)) {
                p.setInt(1,product.getId());
                try (ResultSet r=p.executeQuery()) {
                    if (r.next()) {
                        preferredSupplierRelationId=r.getInt("id");
                        loadSupplierById(r.getInt("supplier_id"));
                        updatePreferredSupplierCost(r.getDouble("purchase_price"));
                        return;
                    }
                }
            }
            preferredSupplierRelationId=null;
            updatePreferredSupplierCost(null);
        } catch (SQLException e) {
            resetSupplierButton();
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
            resetSupplierButton();
        }
    }

    private void showSupplierSelectionDialog() {
        SupplierSelectionDialog dialog = new SupplierSelectionDialog(this);
        dialog.setVisible(true);
        if (dialog.isSupplierSelected()) {
            selectedSupplier = dialog.getSelectedSupplier();
            updateSupplierButton();
            loadSelectedSupplierCost();
        }
    }

    private void loadSelectedSupplierCost() {
        preferredSupplierRelationId=null;
        if (preferredSupplierCostButton == null || product == null || selectedSupplier == null) return;
        try {
            Connection c=DatabaseManager.getInstance().getConnection();
            try(PreparedStatement p=c.prepareStatement("""
                    SELECT id,purchase_price FROM product_suppliers
                    WHERE product_id=? AND supplier_id=? LIMIT 1
                    """)) {
                p.setInt(1,product.getId());p.setInt(2,selectedSupplier.getId());
                try(ResultSet r=p.executeQuery()) {
                    if(r.next()) {
                        preferredSupplierRelationId=r.getInt("id");
                        updatePreferredSupplierCost(r.getDouble("purchase_price"));
                        return;
                    }
                }
            }
        } catch(SQLException ignored) {}
        updatePreferredSupplierCost(null);
    }

    private void updatePreferredSupplierCost(Double cost) {
        if (preferredSupplierCostButton == null) return;
        preferredSupplierCostButton.setText(cost == null ? "—" : costDisplay(cost));
        preferredSupplierCostButton.setEnabled(preferredSupplierRelationId != null);
    }

    private void showPreferredSupplierCostEditor() {
        if (product == null || preferredSupplierRelationId == null) return;
        ProductSuppliersDialog dialog = new ProductSuppliersDialog(
            this, product.getId(), product.getName(), preferredSupplierRelationId);
        dialog.setVisible(true);
        selectedSupplier=null;
        resetSupplierButton();
        loadPreferredSupplierRelation();
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
        resetSupplierButton();
        loadPreferredSupplierRelation();
    }

    private void updateSupplierButton() {
        if (selectedSupplier != null) {
            String buttonText = selectedSupplier.getCompanyName();
            if (selectedSupplier.getVatNumber() != null && !selectedSupplier.getVatNumber().isEmpty())
                buttonText += " (" + I18n.tr("VAT") + ": " + selectedSupplier.getVatNumber() + ")";
            selectSupplierButton.setText(buttonText);
            selectSupplierButton.setToolTipText(I18n.tr("Supplier") + ": " + buttonText);
        }
    }

    private void createNewSupplier() {
        SupplierDialog dialog = new SupplierDialog(this, null);
        dialog.setVisible(true);
        if (dialog.isSupplierSaved()) {
            Dialogs.showMessageDialog(this,
                I18n.tr("Supplier created successfully. Please select it from the list."),
                I18n.tr("Success"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private boolean photoDraftChanged() {
        return !originalPhotoFilenames.equals(pendingPhotoFilenames) ||
            !java.util.Objects.equals(originalPrimaryFilename, pendingPrimaryFilename);
    }

    private void saveProduct() {
        try {
            initializePhotoDraft();
            try {
                quantitySpinner.commitEdit();
            } catch (java.text.ParseException parseException) {
                throw new IllegalArgumentException(
                    I18n.tr("Quantity must be a whole number between 0 and 999999."));
            }
            try {
                minimumQuantitySpinner.commitEdit();
            } catch (java.text.ParseException parseException) {
                throw new IllegalArgumentException(
                    I18n.tr("Minimum quantity must be a whole number between 0 and 999999."));
            }

            String code = codeField.getText().trim();
            String name = nameField.getText().trim();
            String description = descriptionArea.getText().trim();

            if (code.isEmpty())
                throw new IllegalArgumentException(I18n.tr("Product code is required."));
            if (name.isEmpty())
                throw new IllegalArgumentException(I18n.tr("Product name is required."));

            double salePriceIncVat = readNonNegativeField(priceField, "Sale Price incl. VAT");
            int quantity = Numbers.quantity(quantitySpinner.getValue());
            String category = canonicalComboValue(categoryComboBox);
            String alternativeSku = alternativeSkuField.getText().trim();
            double weight = readNonNegativeField(weightField, "Weight (kg)");
            String unitOfMeasure = canonicalComboValue(unitOfMeasureComboBox);
            int minimumQuantity = Numbers.quantity(minimumQuantitySpinner.getValue());
            if (minimumQuantity < 0)
                throw new IllegalArgumentException(I18n.tr("Minimum stock cannot be negative."));
            boolean active = activeCheckBox.isSelected();
            Integer supplierId = selectedSupplier != null ? selectedSupplier.getId() : null;
            double acquisitionCost = 0.0;
            if (product == null) {
                if (acquisitionCostField == null || acquisitionCostField.getText().trim().isEmpty())
                    throw new IllegalArgumentException(I18n.tr("Purchase cost is required."));
                acquisitionCost = readNonNegativeField(acquisitionCostField, "Purchase Cost");
                if (acquisitionCost <= 0)
                    throw new IllegalArgumentException(I18n.tr("Purchase cost must be greater than zero."));
            }
            String warehousePosition = warehousePositionField.getText().trim();
            double vatRate = readVatRate();
            double salePriceExVat = SalePrice.exVatFromIncVat(salePriceIncVat, vatRate);

            if (product != null && supplierId != null &&
                    preferredSupplierChanged(supplierId)) {
                String message = I18n.format("New preferred supplier: {0}.",
                        selectedSupplier.getCompanyName())
                    + "\n" + I18n.tr("The last actual purchase cost, historical costs and existing FIFO lots will not change.")
                    + "\n\n" + I18n.tr("Confirm?");
                if (Dialogs.showConfirmDialog(this, message, I18n.tr("Set Preferred Supplier"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                        != JOptionPane.YES_OPTION) return;
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
                double previousAcquisitionCost = 0.0;

                if (productCodeExists(conn, code, productId))
                    throw new IllegalArgumentException(
                        I18n.tr("A product with this code already exists."));

                if (product != null) {
                    try (PreparedStatement previous = conn.prepareStatement(
                            "SELECT quantity,acquisition_cost FROM products WHERE id=?")) {
                        previous.setInt(1,productId);
                        try (ResultSet r = previous.executeQuery()) {
                            if (!r.next()) throw new IllegalArgumentException(
                                I18n.tr("The product no longer exists. It may have been deleted."));
                            oldQuantity = r.getInt("quantity");
                            previousAcquisitionCost = Numbers.nonNegative(r.getObject("acquisition_cost"));
                            acquisitionCost = previousAcquisitionCost;
                        }
                    }
                }

                if (product == null) {
                    String query = """
                        INSERT INTO products (code, name, description, sale_price_ex_vat, sale_price_inc_vat, quantity,
                            category, alternative_sku, weight, unit_of_measure,
                            acquisition_cost, active, warehouse_position, vat_rate)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setString(1, code); pstmt.setString(2, name); pstmt.setString(3, description);
                        pstmt.setDouble(4, salePriceExVat); pstmt.setDouble(5, salePriceIncVat);
                        pstmt.setInt(6, 0); pstmt.setString(7, category);
                        pstmt.setString(8, alternativeSku); pstmt.setDouble(9, weight);
                        pstmt.setString(10, unitOfMeasure); pstmt.setDouble(11, acquisitionCost);
                        pstmt.setInt(12, active ? 1 : 0);
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
                        SET code = ?, name = ?, description = ?, sale_price_ex_vat = ?, sale_price_inc_vat = ?, quantity = ?,
                            category = ?, alternative_sku = ?, weight = ?, unit_of_measure = ?,
                            acquisition_cost = ?, active = ?,
                            warehouse_position = ?, vat_rate = ?
                        WHERE id = ?
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                        pstmt.setString(1, code); pstmt.setString(2, name); pstmt.setString(3, description);
                        pstmt.setDouble(4, salePriceExVat); pstmt.setDouble(5, salePriceIncVat);
                        pstmt.setInt(6, oldQuantity); pstmt.setString(7, category);
                        pstmt.setString(8, alternativeSku); pstmt.setDouble(9, weight);
                        pstmt.setString(10, unitOfMeasure); pstmt.setDouble(11, acquisitionCost);
                        pstmt.setInt(12, active ? 1 : 0);
                        pstmt.setString(13, warehousePosition); pstmt.setDouble(14, vatRate);
                        pstmt.setInt(15, product.getId());
                        pstmt.executeUpdate();
                    }
                }

                CostIntegrity.updateUnpricedOpeningLots(
                    conn, productId, previousAcquisitionCost, acquisitionCost);

                long delta = (long)quantity - oldQuantity;
                if (delta != 0) {
                    int intDelta=Math.toIntExact(delta);
                    int change = Math.toIntExact(Math.abs(delta));
                    int movementId;
                    try (PreparedStatement movement = conn.prepareStatement("""
                            INSERT INTO warehouse_movements
                            (product_id,date,type,quantity,reason,document_type,notes)
                            VALUES(?,CURRENT_TIMESTAMP,?,?,?,?,?)
                            """, Statement.RETURN_GENERATED_KEYS)) {
                        movement.setInt(1,productId);
                        movement.setString(2,delta>0?"INWARD":"OUTWARD");
                        movement.setInt(3,change);
                        movement.setString(4,"Inventory adjustment");
                        movement.setString(5,"INVENTORY_ADJUSTMENT");
                        movement.setString(6,"Stock count: " + oldQuantity + " -> " + quantity);
                        movement.executeUpdate();
                        try (ResultSet keys = movement.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException(
                                "Unable to create inventory adjustment movement");
                            movementId = keys.getInt(1);
                        }
                    }
                    InventoryLotManager.adjustProductStock(
                        conn,movementId,productId,intDelta,new java.util.Date(),acquisitionCost);
                }

                try (PreparedStatement minimum = conn.prepareStatement("""
                        INSERT INTO minimum_stock(product_id, minimum_quantity, reorder_quantity) VALUES(?,?,0)
                        ON CONFLICT(product_id) DO UPDATE SET minimum_quantity=excluded.minimum_quantity
                        """)) {
                    minimum.setInt(1,productId); minimum.setInt(2,minimumQuantity); minimum.executeUpdate();
                }

                savePreferredSupplierRelation(conn,productId,supplierId);

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
        } catch (IllegalArgumentException e) {
            Dialogs.showMessageDialog(this, e.getMessage(),
                I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
        } catch (SQLException e) {
            e.printStackTrace();
            String message = isDuplicateProductCodeError(e)
                ? I18n.tr("A product with this code already exists.")
                : I18n.tr("The product could not be saved. No changes were applied.");
            Dialogs.showMessageDialog(this, message,
                I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                I18n.tr("The product could not be saved. No changes were applied."),
                I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resetSupplierButton() {
        selectedSupplier = null;
        preferredSupplierRelationId = null;
        updatePreferredSupplierCost(null);
        selectSupplierButton.setText(I18n.tr("Click to select supplier..."));
        selectSupplierButton.setToolTipText(I18n.tr("Click to select supplier..."));
    }

    private double readNonNegativeField(JTextField field, String fieldKey) {
        try {
            return Numbers.nonNegative(field.getText());
        } catch (IllegalArgumentException exception) {
            String label = I18n.tr(fieldKey);
            String message = exception.getMessage();
            if (message != null && message.startsWith("Value must be between 0 and "))
                throw new IllegalArgumentException(I18n.format(
                    "{0} must be between 0 and {1}.", label, "999999999999.99"));
            if ("The value has more precision than the database can preserve".equals(message))
                throw new IllegalArgumentException(I18n.format(
                    "{0} has too many decimal places to be stored safely.", label));
            throw new IllegalArgumentException(I18n.format(
                "{0} must be a valid number.", label));
        }
    }

    private double readVatRate() {
        try {
            return Numbers.vat(vatRateField.getText());
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            if ("VAT must be between 0 and 100".equals(message))
                throw new IllegalArgumentException(
                    I18n.tr("VAT rate must be between 0 and 100."));
            throw new IllegalArgumentException(
                I18n.tr("VAT rate must be a valid number between 0 and 100."));
        }
    }

    private boolean preferredSupplierChanged(int newSupplierId) throws SQLException {
        try (PreparedStatement statement = DatabaseManager.getInstance().getConnection().prepareStatement(
                "SELECT supplier_id FROM product_suppliers WHERE product_id=? AND is_preferred=1 LIMIT 1")) {
            statement.setInt(1, product.getId());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) != newSupplierId;
            }
        }
    }

    private boolean isDuplicateProductCodeError(SQLException exception) {
        for (SQLException current = exception; current != null;
                current = current.getNextException()) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT)
                    .contains("unique constraint failed: products.code"))
                return true;
        }
        return false;
    }

    private boolean productCodeExists(Connection conn, String code, int currentProductId)
            throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT 1 FROM products WHERE code=? AND id<>? LIMIT 1")) {
            statement.setString(1, code);
            statement.setInt(2, currentProductId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private String canonicalComboValue(JComboBox<String> comboBox) {
        Object item = comboBox.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    private void installLocalizedEditableCombo(JComboBox<String> comboBox,
            String[] canonicalValues) {
        ComboBoxEditor delegate = comboBox.getEditor();
        comboBox.setEditor(new ComboBoxEditor() {
            @Override public Component getEditorComponent() {
                return delegate.getEditorComponent();
            }

            @Override public void setItem(Object item) {
                if (item instanceof String text) delegate.setItem(I18n.tr(text));
                else delegate.setItem(item);
            }

            @Override public Object getItem() {
                Object item = delegate.getItem();
                if (item == null) return "";
                String text = item.toString();
                for (String canonical : canonicalValues) {
                    if (text.equals(canonical) || text.equals(I18n.tr(canonical)))
                        return canonical;
                }
                return text;
            }

            @Override public void selectAll() { delegate.selectAll(); }
            @Override public void addActionListener(java.awt.event.ActionListener listener) {
                delegate.addActionListener(listener);
            }
            @Override public void removeActionListener(java.awt.event.ActionListener listener) {
                delegate.removeActionListener(listener);
            }
        });
    }

    private void savePreferredSupplierRelation(Connection c,int productId,Integer supplierId)
            throws SQLException {
        if(supplierId==null)return;
        try(PreparedStatement clear=c.prepareStatement(
                "UPDATE product_suppliers SET is_preferred=0 WHERE product_id=? AND supplier_id<>?")){
            clear.setInt(1,productId);clear.setInt(2,supplierId);clear.executeUpdate();
        }
        try(PreparedStatement upsert=c.prepareStatement("""
                INSERT INTO product_suppliers(product_id,supplier_id,purchase_price,is_preferred)
                VALUES(?,?,0,1)
                ON CONFLICT(product_id,supplier_id) DO UPDATE SET
                    is_preferred=1
                """)){
            upsert.setInt(1,productId);upsert.setInt(2,supplierId);upsert.executeUpdate();
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

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        ViewportWidthPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    public boolean isProductSaved() { return productSaved; }
}

