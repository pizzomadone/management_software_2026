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
    private JComboBox<String> reasonCombo;
    private JTextField documentNumberField;
    private JComboBox<String> documentTypeCombo;
    private JTextArea notesArea;
    private JLabel availabilityLabel;
    private SimpleDateFormat dateFormat;

    // Constructor for JFrame parent
    public WarehouseMovementDialog(JFrame parent, WarehouseMovement movement) {
        super(parent, movement == null ? "New Movement" : "Edit Movement", true);
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
        super(parent, movement == null ? "New Movement" : "Edit Movement", true);
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
        setLocationRelativeTo(null);
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
        formPanel.add(new JLabel("* Product:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        selectProductButton = new JButton("Click to select product...");
        selectProductButton.setPreferredSize(new Dimension(300, 35));
        selectProductButton.setHorizontalAlignment(SwingConstants.LEFT);
        selectProductButton.addActionListener(e -> showProductSelectionDialog());
        formPanel.add(selectProductButton, gbc);

        // Movement type
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("* Type:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        typeCombo = new JComboBox<>(new String[]{"INWARD", "OUTWARD"});
        typeCombo.setPreferredSize(new Dimension(300, 28));
        typeCombo.addActionListener(e -> updateAvailabilityCheck());
        formPanel.add(typeCombo, gbc);

        // Quantity
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("* Quantity:"), gbc);

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

        // Availability info label
        gbc.gridx = 0; gbc.gridy = 3;
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
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("* Reason:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        reasonCombo = new JComboBox<>(new String[]{
            "PURCHASE", "SALE", "CUSTOMER RETURN", "SUPPLIER RETURN",
            "INVENTORY", "GIFT", "THEFT/LOSS", "OTHER"
        });
        reasonCombo.setPreferredSize(new Dimension(300, 28));
        formPanel.add(reasonCombo, gbc);

        // Document number
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Document N°:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        documentNumberField = new JTextField();
        documentNumberField.setPreferredSize(new Dimension(300, 28));
        formPanel.add(documentNumberField, gbc);

        // Document type
        gbc.gridx = 0; gbc.gridy = 6;
        gbc.weightx = 0.0;
        formPanel.add(new JLabel("Document Type:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        documentTypeCombo = new JComboBox<>(new String[]{
            "", "DDT", "INVOICE", "ORDER", "INVENTORY"
        });
        documentTypeCombo.setPreferredSize(new Dimension(300, 28));
        formPanel.add(documentTypeCombo, gbc);

        // Notes
        gbc.gridx = 0; gbc.gridy = 7;
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

        // Legend panel
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel legendLabel = new JLabel("* Required fields");
        legendLabel.setFont(legendLabel.getFont().deriveFont(Font.ITALIC));
        legendLabel.setForeground(Color.GRAY);
        legendPanel.add(legendLabel);

        // Assembly with proper layout
        JScrollPane formScrollPane = new JScrollPane(formPanel);
        formScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        formScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScrollPane.setBorder(null);

        mainPanel.add(legendPanel, BorderLayout.NORTH);
        mainPanel.add(formScrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    // Method to show ProductSelectionDialog
    private void showProductSelectionDialog() {
        ProductSelectionDialog dialog = new ProductSelectionDialog(this);
        dialog.setVisible(true);

        if (dialog.isProductSelected()) {
            selectedProduct = dialog.getSelectedProduct();
            updateProductButton();
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
            selectProductButton.setToolTipText("Selected: " + buttonText + " (Price: €" +
                String.format("%.2f", selectedProduct.getPrice()) + ")");
        }
    }

    // Method to check availability with selected product
    private void updateAvailabilityCheck() {
        try {
            if (availabilityLabel == null || selectedProduct == null) {
                if (availabilityLabel != null) {
                    availabilityLabel.setText("Select a product to see availability information");
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
                if (movement != null && "OUTWARD".equals(movement.getType()) &&
                    movement.getProductId() == selectedProduct.getId()) {
                    // If editing an outward movement for the same product, add back the original quantity
                    currentStock += movement.getQuantity();
                }

                if (requestedQuantity > currentStock) {
                    availabilityLabel.setText(String.format(
                        "⚠️ Insufficient stock! Available: %d, Requested: %d",
                        currentStock, requestedQuantity));
                    availabilityLabel.setForeground(Color.RED);
                    availabilityLabel.setBackground(new Color(255, 240, 240));
                } else {
                    availabilityLabel.setText(String.format(
                        "✓ Available: %d, After movement: %d",
                        currentStock, currentStock - requestedQuantity));
                    availabilityLabel.setForeground(new Color(0, 120, 0));
                    availabilityLabel.setBackground(new Color(240, 255, 240));
                }
            } else {
                availabilityLabel.setText(String.format(
                    "➕ Current: %d, After movement: %d",
                    currentStock, currentStock + requestedQuantity));
                availabilityLabel.setForeground(new Color(0, 120, 0));
                availabilityLabel.setBackground(new Color(240, 255, 240));
            }
        } catch (Exception e) {
            if (availabilityLabel != null) {
                availabilityLabel.setText("Error checking availability");
                availabilityLabel.setForeground(Color.RED);
            }
        }
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

    // Method to load movement data for editing
    private void loadMovementData() {
        // Load product by ID from movement
        selectedProduct = loadProductById(movement.getProductId());
        if (selectedProduct != null) {
            updateProductButton();
        }

        typeCombo.setSelectedItem(movement.getType());
        quantitySpinner.setValue(movement.getQuantity());
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
            // Validation
            if (selectedProduct == null) {
                JOptionPane.showMessageDialog(this,
                    "Select a product",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String type = (String)typeCombo.getSelectedItem();
            int quantity = (Integer)quantitySpinner.getValue();
            String reason = (String)reasonCombo.getSelectedItem();

            // Check availability for outward movements
            if ("OUTWARD".equals(type)) {
                int availability = getCurrentStock(selectedProduct.getId());
                if (movement != null && "OUTWARD".equals(movement.getType()) &&
                    movement.getProductId() == selectedProduct.getId()) {
                    availability += movement.getQuantity(); // Restore original quantity
                }
                if (quantity > availability) {
                    int choice = JOptionPane.showConfirmDialog(this,
                        String.format("Insufficient quantity. Current availability: %d\n" +
                                     "Requested: %d\n\n" +
                                     "Do you want to proceed anyway?\n" +
                                     "This will result in negative stock.",
                                     availability, quantity),
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
                if (movement == null) {
                    // Insert new movement
                    String insertQuery = """
                        INSERT INTO warehouse_movements (
                            product_id, date, type, quantity, reason,
                            document_number, document_type, notes
                        ) VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?)
                    """;

                    try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
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
                    }

                } else {
                    // Update existing movement (product can now be changed)
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

                        pstmt.setInt(8, movement.getId());
                        pstmt.executeUpdate();
                    }

                    // Restore original quantity before applying new changes
                    String resetQuery = """
                        UPDATE products
                        SET quantity = quantity + ?
                        WHERE id = ?
                    """;
                    try (PreparedStatement pstmt = conn.prepareStatement(resetQuery)) {
                        int quantityDelta = "INWARD".equals(movement.getType()) ?
                            -movement.getQuantity() : movement.getQuantity();
                        pstmt.setInt(1, quantityDelta);
                        pstmt.setInt(2, movement.getProductId());
                        pstmt.executeUpdate();
                    }
                }

                // Update product quantity
                String updateProductQuery = """
                    UPDATE products
                    SET quantity = quantity + ?
                    WHERE id = ?
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(updateProductQuery)) {
                    int quantityDelta = "INWARD".equals(type) ? quantity : -quantity;
                    pstmt.setInt(1, quantityDelta);
                    pstmt.setInt(2, selectedProduct.getId());
                    pstmt.executeUpdate();
                }

                conn.commit();
                movementSaved = true;

                JOptionPane.showMessageDialog(this,
                    "Movement saved successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);

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
                "Error saving movement: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isMovementSaved() {
        return movementSaved;
    }
}
