import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WarehouseMovementDialog extends JDialog {
    private WarehouseMovement movement;
    private boolean movementSaved = false;

    private JButton selectProductButton;
    private Product selectedProduct;
    private JComboBox<String> typeCombo;
    private JSpinner quantitySpinner;
    private JLabel inboundCostLabel;
    private JTextField inboundCostField;
    private JComboBox<String> reasonCombo;
    private JTextField documentNumberField;
    private JComboBox<String> documentTypeCombo;
    private JTextArea notesArea;
    private JLabel availabilityLabel;
    private SimpleDateFormat dateFormat;

    // Constructor for JFrame parent
    public WarehouseMovementDialog(JFrame parent, WarehouseMovement movement) {
        super(parent, I18n.tr(movement == null ? "New Movement" : "Edit Movement"), true);
        this.movement = movement;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        setupWindow();
        initComponents();
        if (movement != null) {
            loadMovementData();
        }

        SwingUtilities.invokeLater(this::updateAvailabilityCheck);
    }

    // Constructor for JDialog parent
    public WarehouseMovementDialog(JDialog parent, WarehouseMovement movement) {
        super(parent, I18n.tr(movement == null ? "New Movement" : "Edit Movement"), true);
        this.movement = movement;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        setupWindow();
        initComponents();
        if (movement != null) {
            loadMovementData();
        }

        SwingUtilities.invokeLater(this::updateAvailabilityCheck);
    }

    private void setupWindow() {
        setSize(550, 580);
        Dialogs.centerWindow(this);
        setLayout(new BorderLayout(8, 8));
        setMinimumSize(new Dimension(500, 550));
    }

    private void initComponents() {
        // Main panel with padding
        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // Form panel with improved layout
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Product Selection - Using button instead of combo
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Product *:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        selectProductButton = new JButton(I18n.tr("Click to select product..."));
        selectProductButton.putClientProperty("i18n.literal", true);
        selectProductButton.setPreferredSize(new Dimension(300, 35));
        selectProductButton.setHorizontalAlignment(SwingConstants.LEFT);
        selectProductButton.addActionListener(e -> showProductSelectionDialog());
        formPanel.add(selectProductButton, gbc);

        // Movement type
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Type *:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        typeCombo = new JComboBox<>(new String[]{"INWARD", "OUTWARD"});
        typeCombo.putClientProperty("i18n.values", true);
        typeCombo.setPreferredSize(new Dimension(300, 28));
        typeCombo.addActionListener(e -> {
            updateInboundCostVisibility();
            updateAvailabilityCheck();
        });
        formPanel.add(typeCombo, gbc);

        // Quantity
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Quantity *:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(1, 1, 99999, 1);
        quantitySpinner = new JSpinner(spinnerModel);
        quantitySpinner.setPreferredSize(new Dimension(300, 28));
        quantitySpinner.addChangeListener(e -> updateAvailabilityCheck());

        // Make spinner editor wider
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) quantitySpinner.getEditor();
        editor.getTextField().setColumns(10);

        formPanel.add(quantitySpinner, gbc);

        // Unit cost applies only to manual inward movements.
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.weightx = 0.0;
        inboundCostLabel = new JLabel("Inbound Unit Cost *:");
        formPanel.add(inboundCostLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        inboundCostField = new JTextField(12);
        inboundCostField.setPreferredSize(new Dimension(300, 28));
        inboundCostField.setToolTipText(I18n.tr(
            "Unit cost of this inward movement. It becomes the cost of the FIFO lot and can be changed before saving."));
        formPanel.add(inboundCostField, gbc);

        // Availability info label
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        availabilityLabel = new JLabel("Select a product to see availability information");
        availabilityLabel.setOpaque(true);
        availabilityLabel.setBackground(new Color(245, 245, 245));
        availabilityLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY, 1),
            BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
        availabilityLabel.setPreferredSize(new Dimension(450, 28));
        formPanel.add(availabilityLabel, gbc);
        gbc.gridwidth = 1; // Reset

        // Reason
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Reason *:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        reasonCombo = new JComboBox<>(new String[]{
            "PURCHASE", "SALE", "CUSTOMER RETURN", "SUPPLIER RETURN",
            "INVENTORY", "GIFT", "THEFT/LOSS", "OTHER"
        });
        reasonCombo.putClientProperty("i18n.values", true);
        reasonCombo.setPreferredSize(new Dimension(300, 28));
        formPanel.add(reasonCombo, gbc);

        // Document number
        gbc.gridx = 0; gbc.gridy = 6;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Document N°:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        documentNumberField = new JTextField();
        documentNumberField.setPreferredSize(new Dimension(300, 28));
        formPanel.add(documentNumberField, gbc);

        // Document type
        gbc.gridx = 0; gbc.gridy = 7;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Document Type:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        documentTypeCombo = new JComboBox<>(new String[]{
            "", "DDT", "INVOICE", "OTHER", "INVENTORY"
        });
        documentTypeCombo.putClientProperty("i18n.values", true);
        documentTypeCombo.setPreferredSize(new Dimension(300, 28));
        formPanel.add(documentTypeCombo, gbc);

        // Notes
        gbc.gridx = 0; gbc.gridy = 8;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        formPanel.add(new JLabel("Notes:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.CENTER;
        notesArea = new JTextArea(3, 25);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setPreferredSize(new Dimension(300, 65));
        notesScroll.setMinimumSize(new Dimension(300, 60));
        notesScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        formPanel.add(notesScroll, gbc);

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 8));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.setPreferredSize(new Dimension(90, 32));
        cancelButton.setPreferredSize(new Dimension(90, 32));
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD));

        saveButton.addActionListener(e -> saveMovement());
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        // Assembly with proper layout
        JScrollPane formScrollPane = new JScrollPane(formPanel);
        formScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        formScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScrollPane.setBorder(null);

        mainPanel.add(Dialogs.requiredLegendPanel(), BorderLayout.NORTH);
        mainPanel.add(formScrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
        updateInboundCostVisibility();
        I18n.localize(this);
    }

    // Method to show ProductSelectionDialog
    private void showProductSelectionDialog() {
        ProductSelectionDialog dialog = new ProductSelectionDialog(this);
        dialog.setVisible(true);

        if (dialog.isProductSelected()) {
            selectedProduct = dialog.getSelectedProduct();
            updateProductButton();
            loadCurrentPurchaseCost();
            updateAvailabilityCheck();
        }
    }

    // Method to update product button text
    private void updateProductButton() {
        if (selectedProduct != null) {
            String buttonText = String.format("%s - %s",
                selectedProduct.getCode(),
                selectedProduct.getName());

            selectProductButton.setText(buttonText);
            selectProductButton.setToolTipText(I18n.tr("Selected") + ": " + buttonText + " (" + I18n.tr("Price") + ": " +
                SettingsPanel.formatCurrency(selectedProduct.getPrice()) + ")");
        }
    }

    // Method to check availability with selected product
    private void updateAvailabilityCheck() {
        try {
            if (availabilityLabel == null || selectedProduct == null) {
                if (availabilityLabel != null) {
                    availabilityLabel.setText(I18n.tr("Select a product to see availability information"));
                    availabilityLabel.setForeground(Color.GRAY);
                    availabilityLabel.setBackground(new Color(245, 245, 245));
                }
                return;
            }

            String type = (String)typeCombo.getSelectedItem();
            int requestedQuantity = (Integer)quantitySpinner.getValue();

            // Get current stock from database (fresh data)
            int currentStock = getCurrentStock(selectedProduct.getId());

            if ("OUTWARD".equals(type)) {
                int available=availableForOutward(DatabaseManager.getInstance().getConnection(),selectedProduct.getId());
                if (requestedQuantity > available) {
                    availabilityLabel.setText(I18n.format(
                        "Insufficient stock! Available: {0}, Requested: {1}",
                        available, requestedQuantity));
                    availabilityLabel.setForeground(Color.RED);
                    availabilityLabel.setBackground(new Color(255, 240, 240));
                } else {
                    availabilityLabel.setText(I18n.format(
                        "Available: {0}, After movement: {1}",
                        available, available - requestedQuantity));
                    availabilityLabel.setForeground(new Color(0, 120, 0));
                    availabilityLabel.setBackground(new Color(240, 255, 240));
                }
            } else {
                availabilityLabel.setText(I18n.format(
                    "Current: {0}, After movement: {1}",
                    currentStock, currentStock + requestedQuantity));
                availabilityLabel.setForeground(new Color(0, 120, 0));
                availabilityLabel.setBackground(new Color(240, 255, 240));
            }
        } catch (Exception e) {
            if (availabilityLabel != null) {
                availabilityLabel.setText(I18n.tr("Error checking availability"));
                availabilityLabel.setForeground(Color.RED);
            }
        }
    }

    private void updateInboundCostVisibility() {
        if (inboundCostLabel == null || inboundCostField == null || typeCombo == null) return;
        boolean inward = "INWARD".equals(typeCombo.getSelectedItem());
        inboundCostLabel.setVisible(inward);
        inboundCostField.setVisible(inward);
        revalidate();
        repaint();
    }

    private void loadCurrentPurchaseCost() {
        if (selectedProduct == null || inboundCostField == null) return;
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            try (PreparedStatement p = conn.prepareStatement(
                    "SELECT acquisition_cost FROM products WHERE id=?")) {
                p.setInt(1, selectedProduct.getId());
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) {
                        double cost = Numbers.nonNegative(r.getObject(1));
                        inboundCostField.setText(cost > 0 ? Numbers.unitText(cost) : "");
                    }
                }
            }
        } catch (Exception e) {
            inboundCostField.setText("");
        }
    }

    private void loadMovementUnitCost() {
        if (movement == null || inboundCostField == null) return;
        if (!"INWARD".equals(movement.getType())) {
            inboundCostField.setText("");
            updateInboundCostVisibility();
            return;
        }
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            try (PreparedStatement p = conn.prepareStatement(
                    "SELECT unit_cost FROM inventory_lots WHERE warehouse_movement_id=? LIMIT 1")) {
                p.setInt(1, movement.getId());
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) inboundCostField.setText(Numbers.unitText(r.getDouble(1)));
                    else loadCurrentPurchaseCost();
                }
            }
        } catch (Exception e) {
            loadCurrentPurchaseCost();
        }
        updateInboundCostVisibility();
    }

    // Method to get current stock from database
    private int getCurrentStock(int productId) {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = "SELECT quantity FROM products WHERE id = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, productId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("quantity");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private int availableForOutward(Connection conn,int productId) throws SQLException {
        int available=StockManager.getAvailableStock(conn,productId);
        if (movement==null || movement.getProductId()!=productId) return available;
        try {
            if ("OUTWARD".equals(movement.getType())) return Math.addExact(available,movement.getQuantity());
            if ("INWARD".equals(movement.getType())) return Math.subtractExact(available,movement.getQuantity());
        } catch (ArithmeticException e) { throw new SQLException("Stock availability exceeds the supported range",e); }
        throw new SQLException("Unsupported existing movement type: "+movement.getType());
    }

    // Method to load movement data for editing
    private void loadMovementData() {
        // Load product by ID from movement
        selectedProduct = loadProductById(movement.getProductId());
        if (selectedProduct != null) {
            updateProductButton();
        }

        typeCombo.setSelectedItem(movement.getType());
        quantitySpinner.setValue(movement.getQuantity());
        loadMovementUnitCost();
        reasonCombo.setSelectedItem(movement.getReason());
        documentNumberField.setText(movement.getDocumentNumber() != null ? movement.getDocumentNumber() : "");
        documentTypeCombo.setSelectedItem(movement.getDocumentType() != null ? movement.getDocumentType() : "");
        notesArea.setText(movement.getNotes() != null ? movement.getNotes() : "");

        // Product can be modified even for existing movements
        updateProductButton();

        // Update availability info
        updateAvailabilityCheck();
    }

    // Method to load product by ID
    private Product loadProductById(int productId) {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = "SELECT * FROM products WHERE id = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, productId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return new Product(
                        rs.getInt("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getDouble("price"),
                        rs.getInt("quantity")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Save movement method
    private void saveMovement() {
        try {
            quantitySpinner.commitEdit();
            // Validation
            if (selectedProduct == null) {
                Dialogs.showMessageDialog(this,
                    I18n.tr("Select a product"),
                    I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            String type = (String)typeCombo.getSelectedItem();
            int quantity = Numbers.positiveQuantity(quantitySpinner.getValue());
            String reason = (String)reasonCombo.getSelectedItem();

            Connection conn = DatabaseManager.getInstance().getConnection();
            if (!CostIntegrity.hasPurchaseCost(conn, selectedProduct.getId())) {
                Dialogs.showMessageDialog(this,
                    CostIntegrity.missingPurchaseCostMessage(
                        conn, java.util.List.of(selectedProduct.getId())),
                    I18n.tr("Purchase Cost Required"), JOptionPane.WARNING_MESSAGE);
                return;
            }

            double inboundUnitCost = 0.0;
            if ("INWARD".equals(type)) {
                if (inboundCostField.getText().trim().isEmpty()) {
                    Dialogs.showMessageDialog(this, I18n.tr("Inbound unit cost is required."),
                        I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                try {
                    inboundUnitCost = Numbers.nonNegative(inboundCostField.getText());
                } catch (IllegalArgumentException invalidCost) {
                    Dialogs.showMessageDialog(this,
                        I18n.tr("Inbound unit cost must be a valid non-negative number."),
                        I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            DatabaseManager.beginTransaction(conn);
            try {
                if (movement != null) StockManager.requireManualMovement(conn, movement.getId());
            // Check availability for outward movements
            if ("OUTWARD".equals(type)) {
                int availability = availableForOutward(conn,selectedProduct.getId());
                if (quantity > availability) {
                    Dialogs.showMessageDialog(this,
                        I18n.format("Insufficient quantity. Current availability: {0}", availability)
                            + "\n" + I18n.format("Requested: {0}", quantity)
                            + "\n\n" + I18n.tr("This will result in negative stock."),
                        I18n.tr("Insufficient Stock"),
                        JOptionPane.WARNING_MESSAGE);
                    conn.rollback();
                    return;
                }
            }

                int movementId;
                if (movement == null) {
                    String insertQuery = """
                        INSERT INTO warehouse_movements (
                            product_id, date, type, quantity, reason,
                            document_number, document_type, notes
                        ) VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?)
                    """;

                    try (PreparedStatement pstmt = conn.prepareStatement(
                            insertQuery, Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setInt(1, selectedProduct.getId());
                        pstmt.setString(2, type);
                        pstmt.setInt(3, quantity);
                        pstmt.setString(4, reason);

                        String docNumber = documentNumberField.getText().trim();
                        pstmt.setString(5, docNumber.isEmpty() ? null : docNumber);

                        String docType = (String)documentTypeCombo.getSelectedItem();
                        pstmt.setString(6, (docType == null || docType.trim().isEmpty()) ? null : docType);

                        String notes = notesArea.getText().trim();
                        pstmt.setString(7, notes.isEmpty() ? null : notes);
                        pstmt.executeUpdate();

                        try(ResultSet keys=pstmt.getGeneratedKeys()){
                            if(!keys.next())throw new SQLException("Unable to create warehouse movement");
                            movementId=keys.getInt(1);
                        }
                    }
                } else {
                    movementId=movement.getId();

                    // Undo the exact FIFO effect of the old manual movement first.
                    InventoryLotManager.reverseManualMovement(
                        conn,movement.getId(),movement.getProductId(),
                        movement.getType(),movement.getQuantity());

                    String updateQuery = """
                        UPDATE warehouse_movements SET
                            product_id = ?, type = ?, quantity = ?, reason = ?,
                            document_number = ?, document_type = ?, notes = ?
                        WHERE id = ?
                    """;

                    try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                        pstmt.setInt(1, selectedProduct.getId());
                        pstmt.setString(2, type);
                        pstmt.setInt(3, quantity);
                        pstmt.setString(4, reason);

                        String docNumber = documentNumberField.getText().trim();
                        pstmt.setString(5, docNumber.isEmpty() ? null : docNumber);

                        String docType = (String)documentTypeCombo.getSelectedItem();
                        pstmt.setString(6, (docType == null || docType.trim().isEmpty()) ? null : docType);

                        String notes = notesArea.getText().trim();
                        pstmt.setString(7, notes.isEmpty() ? null : notes);
                        pstmt.setInt(8, movementId);
                        pstmt.executeUpdate();
                    }
                }

                InventoryLotManager.applyManualMovement(
                    conn,movementId,selectedProduct.getId(),type,quantity,
                    movement == null ? new Date() : movement.getDate(),inboundUnitCost);

                conn.commit();
                movementSaved = true;

                Dialogs.showMessageDialog(this,
                    I18n.tr("Movement saved successfully!"),
                    I18n.tr("Success"), JOptionPane.INFORMATION_MESSAGE);

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
                I18n.tr("Error saving movement") + ": " + I18n.tr(e.getMessage()),
                I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isMovementSaved() {
        return movementSaved;
    }
}

