import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.Vector;
import javax.swing.Timer;

public class ProductSelectionDialog extends JDialog {
    private JTextField searchField;
    private JTable productsTable;
    private DefaultTableModel tableModel;
    private Product selectedProduct;
    private boolean productSelected = false;
    private JSpinner quantitySpinner;
    private JTextField vatRateField;
    private JLabel noteLabel;

    // Selected product data for the order
    private int selectedQuantity = 1;
    private double selectedVatRate = 22.0;
    private Timer searchTimer;
    
    public ProductSelectionDialog(JDialog parent) {
        super(parent, "Select Product", true);
        
        setupWindow();
        initComponents();
        loadAllProducts();
    }
    
    private void setupWindow() {
        setSize(950, 580); // Reduced height for better screen fit
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(10, 10));
    }
    
    private void initComponents() {
        // Main panel with padding
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Improved search panel
        JPanel searchPanel = new JPanel(new BorderLayout(10, 10));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Product"));
        
        JPanel searchInputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchField = new JTextField(30);
        searchField.setToolTipText("Search by code, name or description");
        
        JButton searchButton = new JButton("Search");
        JButton clearButton = new JButton("Clear");
        JButton newProductButton = new JButton("New Product");

        // Real-time search with delay
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { scheduleSearch(); }
        });

        // Enter key for search
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performSearch();
                }
            }
        });
        
        searchButton.addActionListener(e -> performSearch());
        clearButton.addActionListener(e -> {
            searchField.setText("");
            loadAllProducts();
        });
        newProductButton.addActionListener(e -> createNewProduct());
        
        searchInputPanel.add(new JLabel("Search:"));
        searchInputPanel.add(searchField);
        searchInputPanel.add(searchButton);
        searchInputPanel.add(clearButton);
        searchInputPanel.add(newProductButton);


        searchPanel.add(searchInputPanel, BorderLayout.CENTER);

        // Products table with detailed information
        String[] columns = {"ID", "Code", "Name", "Description", "Price €", "Stock", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        productsTable = new JTable(tableModel);

        // Hide ID column
        productsTable.getColumnModel().getColumn(0).setMinWidth(0);
        productsTable.getColumnModel().getColumn(0).setMaxWidth(0);
        productsTable.getColumnModel().getColumn(0).setPreferredWidth(0);

        // Set column widths
        productsTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // Code
        productsTable.getColumnModel().getColumn(2).setPreferredWidth(200); // Name
        productsTable.getColumnModel().getColumn(3).setPreferredWidth(250); // Description
        productsTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Price
        productsTable.getColumnModel().getColumn(5).setPreferredWidth(60);  // Stock
        productsTable.getColumnModel().getColumn(6).setPreferredWidth(80);  // Status

        // Row selection listener to update details
        productsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateProductDetails();
            }
        });

        // Double-click for selection (left button only)
        productsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    selectProduct();
                }
            }
        });

        // Selection with Enter
        productsTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    selectProduct();
                }
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(productsTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Products (Double-click to select)"));
        tableScrollPane.setPreferredSize(new Dimension(900, 290)); // Reduced height

        // Selected product details panel - FIXED LAYOUT
        JPanel detailsPanel = createDetailsPanel();

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton selectButton = new JButton("Add to Order/Invoice");
        JButton cancelButton = new JButton("Cancel");
        
        selectButton.addActionListener(e -> selectProduct());
        cancelButton.addActionListener(e -> dispose());

        // Button style
        selectButton.setPreferredSize(new Dimension(150, 30));
        cancelButton.setPreferredSize(new Dimension(100, 30));
        selectButton.setFont(selectButton.getFont().deriveFont(Font.BOLD));

        buttonPanel.add(selectButton);
        buttonPanel.add(cancelButton);

        // Main layout - FIXED: Better proportions
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        mainPanel.add(tableScrollPane, BorderLayout.CENTER);
        mainPanel.add(detailsPanel, BorderLayout.SOUTH);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Initial focus on search field
        SwingUtilities.invokeLater(() -> searchField.requestFocus());
    }

    // FIXED: Improved details panel layout
    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Selected Product Details"));
        panel.setPreferredSize(new Dimension(900, 120)); // Fixed height

        // Product information panel with improved layout
        JPanel infoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;

        // First row: Quantity and VAT Rate
        gbc.gridx = 0; gbc.gridy = 0;
        infoPanel.add(new JLabel("Quantity:"), gbc);

        gbc.gridx = 1;
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(1, 1, 9999, 1);
        quantitySpinner = new JSpinner(spinnerModel);
        quantitySpinner.setPreferredSize(new Dimension(80, 25));
        infoPanel.add(quantitySpinner, gbc);

        // Space between controls
        gbc.gridx = 2;
        infoPanel.add(Box.createHorizontalStrut(30), gbc);

        gbc.gridx = 3;
        infoPanel.add(new JLabel("VAT Rate %:"), gbc);

        gbc.gridx = 4;
        vatRateField = new JTextField(String.valueOf(SettingsPanel.getDefaultVatRate()));
        vatRateField.setPreferredSize(new Dimension(100, 25)); // Increased width
        vatRateField.setHorizontalAlignment(JTextField.CENTER);
        vatRateField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(2, 5, 2, 5)
        ));
        infoPanel.add(vatRateField, gbc);

        // Second row: Notes/info about selected product
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 5;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        
        noteLabel = new JLabel("Select a product from the table above to see details and set quantity");
        noteLabel.setFont(noteLabel.getFont().deriveFont(Font.PLAIN, 12f));
        noteLabel.setForeground(new Color(60, 60, 60));
        noteLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        noteLabel.setOpaque(true);
        noteLabel.setBackground(new Color(248, 248, 248));
        noteLabel.setPreferredSize(new Dimension(800, 35)); // Fixed height for note
        infoPanel.add(noteLabel, gbc);
        
        panel.add(infoPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void scheduleSearch() {
        if (searchTimer != null) {
            searchTimer.stop();
        }
        searchTimer = new Timer(300, e -> performSearch());
        searchTimer.setRepeats(false);
        searchTimer.start();
    }
    
    private void loadAllProducts() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT id, code, name, description, price, quantity
                FROM products
                ORDER BY name
                LIMIT 1000
            """;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    addProductRow(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading products: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void performSearch() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadAllProducts();
            return;
        }
        
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT id, code, name, description, price, quantity
                FROM products
                WHERE LOWER(code) LIKE LOWER(?)
                   OR LOWER(name) LIKE LOWER(?)
                   OR LOWER(description) LIKE LOWER(?)
                ORDER BY
                    CASE
                        WHEN LOWER(code) LIKE LOWER(?) THEN 1
                        WHEN LOWER(name) LIKE LOWER(?) THEN 2
                        ELSE 3
                    END,
                    name
                LIMIT 500
            """;
            
            String searchPattern = "%" + searchTerm + "%";
            String exactPattern = searchTerm + "%";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, searchPattern);
                pstmt.setString(2, searchPattern);
                pstmt.setString(3, searchPattern);
                pstmt.setString(4, exactPattern); // For sorting
                pstmt.setString(5, exactPattern); // For sorting
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        addProductRow(rs);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error searching products: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    private void addProductRow(ResultSet rs) throws SQLException {
        Vector<Object> row = new Vector<>();
        row.add(rs.getInt("id")); // Hidden ID
        row.add(rs.getString("code"));
        row.add(rs.getString("name"));

        String description = rs.getString("description");
        // Truncate description if too long
        if (description != null && description.length() > 50) {
            description = description.substring(0, 47) + "...";
        }
        row.add(description);

        row.add(String.format("%.2f", rs.getDouble("price")));

        int stock = rs.getInt("quantity");
        row.add(stock);

        // Status based on availability
        String status;
        if (stock <= 0) {
            status = "Out of Stock";
        } else if (stock < 10) {
            status = "Low Stock";
        } else {
            status = "Available";
        }
        row.add(status);

        tableModel.addRow(row);
    }

    // FIXED: Better product details display
    private void updateProductDetails() {
        int selectedRow = productsTable.getSelectedRow();
        if (selectedRow != -1) {
            int productId = (int)tableModel.getValueAt(selectedRow, 0);
            String code = (String)tableModel.getValueAt(selectedRow, 1);
            String name = (String)tableModel.getValueAt(selectedRow, 2);
            String priceStr = (String)tableModel.getValueAt(selectedRow, 4);
            int stock = (int)tableModel.getValueAt(selectedRow, 5);
            String status = (String)tableModel.getValueAt(selectedRow, 6);

            // Load product VAT rate from database
            try {
                Connection conn = DatabaseManager.getInstance().getConnection();
                String query = "SELECT vat_rate FROM products WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, productId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            double productVatRate = rs.getDouble("vat_rate");
                            // Use product VAT if set (> 0), otherwise use default
                            if (productVatRate > 0) {
                                vatRateField.setText(String.format("%.1f", productVatRate));
                            } else {
                                vatRateField.setText(String.valueOf(SettingsPanel.getDefaultVatRate()));
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                // If error loading VAT, use default
                vatRateField.setText(String.valueOf(SettingsPanel.getDefaultVatRate()));
            }

            // Update note with detailed and better formatted information
            if (noteLabel != null) {
                String info = String.format(
                    "Selected: %s - %s | Price: €%s | Stock: %d units (%s)",
                    code, name, priceStr, stock, status
                );
                noteLabel.setText(info);

                // Change color based on status
                if ("Out of Stock".equals(status)) {
                    noteLabel.setForeground(new Color(180, 50, 50));
                    noteLabel.setBackground(new Color(255, 245, 245));
                } else if ("Low Stock".equals(status)) {
                    noteLabel.setForeground(new Color(180, 120, 50));
                    noteLabel.setBackground(new Color(255, 250, 240));
                } else {
                    noteLabel.setForeground(new Color(50, 120, 50));
                    noteLabel.setBackground(new Color(245, 255, 245));
                }
            }
        } else {
            if (noteLabel != null) {
                noteLabel.setText("Select a product from the table above to see details and set quantity");
                noteLabel.setForeground(new Color(60, 60, 60));
                noteLabel.setBackground(new Color(248, 248, 248));
            }
        }
    }
    
    private void selectProduct() {
        int selectedRow = productsTable.getSelectedRow();
        if (selectedRow != -1) {
            selectedProduct = new Product(
                (int)tableModel.getValueAt(selectedRow, 0),
                (String)tableModel.getValueAt(selectedRow, 1),
                (String)tableModel.getValueAt(selectedRow, 2),
                null, // Full description if needed
                Double.parseDouble(((String)tableModel.getValueAt(selectedRow, 4)).replace(",", ".")),
                (int)tableModel.getValueAt(selectedRow, 5)
            );

            selectedQuantity = (int)quantitySpinner.getValue();

            try {
                selectedVatRate = Double.parseDouble(vatRateField.getText().replace(",", "."));
                if (selectedVatRate < 0 || selectedVatRate > 100) {
                    throw new NumberFormatException("VAT rate out of range");
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this,
                    "Please enter a valid VAT rate (0-100%)",
                    "Invalid VAT Rate", JOptionPane.WARNING_MESSAGE);
                vatRateField.requestFocus();
                vatRateField.selectAll();
                return;
            }

            // Verify availability
            if (selectedProduct.getQuantity() < selectedQuantity) {
                int result = JOptionPane.showConfirmDialog(this,
                    String.format("Warning: Requested quantity (%d) exceeds available stock (%d).\n" +
                                 "Do you want to continue anyway?",
                                 selectedQuantity, selectedProduct.getQuantity()),
                    "Stock Warning",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
                    
                if (result != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            
            productSelected = true;
            dispose();
        } else {
            JOptionPane.showMessageDialog(this,
                "Please select a product from the list",
                "No Selection", JOptionPane.WARNING_MESSAGE);
        }
    }
    
    private void createNewProduct() {
        ProductDialog dialog = new ProductDialog(this, null);
        dialog.setVisible(true);
        if (dialog.isProductSaved()) {
            // Reload the list
            loadAllProducts();
        }
    }

    // Getters
    public Product getSelectedProduct() { return selectedProduct; }
    public boolean isProductSelected() { return productSelected; }
    public int getSelectedQuantity() { return selectedQuantity; }
    public double getSelectedVatRate() { return selectedVatRate; }
}