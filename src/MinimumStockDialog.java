import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class MinimumStockDialog extends JDialog {
    private MinimumStock minStock;
    private boolean stockSaved = false;

    private JLabel productLabel;
    private JSpinner minQuantitySpinner;
    private JSpinner reorderQuantitySpinner;
    private JSpinner leadTimeSpinner;
    private JComboBox<SupplierComboItem> supplierCombo;
    private JTextArea notesArea;

    // Constructor for JFrame parent
    public MinimumStockDialog(JFrame parent, MinimumStock minStock) {
        super(parent, "Set Minimum Stock", true);
        this.minStock = minStock;

        setupWindow();
        initComponents();
        loadSuppliers();
        if (minStock != null) {
            loadStockData();
        }
    }

    // Constructor for JDialog parent
    public MinimumStockDialog(JDialog parent, MinimumStock minStock) {
        super(parent, "Set Minimum Stock", true);
        this.minStock = minStock;

        setupWindow();
        initComponents();
        loadSuppliers();
        if (minStock != null) {
            loadStockData();
        }
    }

    private void setupWindow() {
        setSize(450, 400);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(10, 10));
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Product (read-only)
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Product:"), gbc);

        gbc.gridx = 1;
        productLabel = new JLabel(minStock != null ? minStock.getProductName() : "N/A");
        productLabel.setFont(productLabel.getFont().deriveFont(Font.BOLD));
        formPanel.add(productLabel, gbc);

        // Minimum quantity
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("* Minimum Quantity:"), gbc);

        gbc.gridx = 1;
        SpinnerNumberModel minModel = new SpinnerNumberModel(0, 0, 99999, 1);
        minQuantitySpinner = new JSpinner(minModel);
        formPanel.add(minQuantitySpinner, gbc);

        // Reorder quantity
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("* Reorder Quantity:"), gbc);

        gbc.gridx = 1;
        SpinnerNumberModel reorderModel = new SpinnerNumberModel(0, 0, 99999, 1);
        reorderQuantitySpinner = new JSpinner(reorderModel);
        formPanel.add(reorderQuantitySpinner, gbc);

        // Lead time
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Lead Time (days):"), gbc);

        gbc.gridx = 1;
        SpinnerNumberModel leadModel = new SpinnerNumberModel(0, 0, 365, 1);
        leadTimeSpinner = new JSpinner(leadModel);
        formPanel.add(leadTimeSpinner, gbc);

        // Preferred supplier
        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("Preferred Supplier:"), gbc);

        gbc.gridx = 1;
        supplierCombo = new JComboBox<>();
        supplierCombo.addItem(new SupplierComboItem(null, "-- None --"));
        formPanel.add(supplierCombo, gbc);

        // Notes
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        formPanel.add(new JLabel("Notes:"), gbc);

        gbc.gridx = 1;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        notesArea = new JTextArea(4, 30);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        JScrollPane notesScroll = new JScrollPane(notesArea);
        formPanel.add(notesScroll, gbc);

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> saveStock());
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        // Legend panel
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        legendPanel.add(new JLabel("* Required fields"));

        // Assembly
        mainPanel.add(legendPanel, BorderLayout.NORTH);
        mainPanel.add(formPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void loadSuppliers() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = "SELECT id, company_name FROM suppliers ORDER BY company_name";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    supplierCombo.addItem(new SupplierComboItem(
                        rs.getInt("id"),
                        rs.getString("company_name")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading suppliers: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadStockData() {
        if (minStock == null) return;

        productLabel.setText(minStock.getProductName());
        minQuantitySpinner.setValue(minStock.getMinimumQuantity());
        reorderQuantitySpinner.setValue(minStock.getReorderQuantity());
        leadTimeSpinner.setValue(minStock.getLeadTimeDays());

        // Select supplier
        if (minStock.getPreferredSupplier() != null) {
            for (int i = 0; i < supplierCombo.getItemCount(); i++) {
                SupplierComboItem item = supplierCombo.getItemAt(i);
                if (item.getId() != null && item.getId().equals(minStock.getPreferredSupplier())) {
                    supplierCombo.setSelectedIndex(i);
                    break;
                }
            }
        }

        notesArea.setText(minStock.getNotes() != null ? minStock.getNotes() : "");
    }

    private void saveStock() {
        try {
            if (minStock == null) {
                JOptionPane.showMessageDialog(this,
                    "No product selected",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int minQuantity = (Integer) minQuantitySpinner.getValue();
            int reorderQuantity = (Integer) reorderQuantitySpinner.getValue();

            if (minQuantity <= 0 || reorderQuantity <= 0) {
                JOptionPane.showMessageDialog(this,
                    "Minimum quantity and reorder quantity must be greater than 0",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (reorderQuantity < minQuantity) {
                JOptionPane.showMessageDialog(this,
                    "Reorder quantity should be greater than or equal to minimum quantity",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            }

            Connection conn = DatabaseManager.getInstance().getConnection();

            // Check if record exists
            String checkQuery = "SELECT COUNT(*) FROM minimum_stock WHERE product_id = ?";
            boolean exists = false;
            try (PreparedStatement pstmt = conn.prepareStatement(checkQuery)) {
                pstmt.setInt(1, minStock.getProductId());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    exists = rs.getInt(1) > 0;
                }
            }

            SupplierComboItem selectedSupplier = (SupplierComboItem) supplierCombo.getSelectedItem();
            Integer supplierId = selectedSupplier != null ? selectedSupplier.getId() : null;

            if (exists) {
                // Update existing record
                String updateQuery = """
                    UPDATE minimum_stock SET
                        minimum_quantity = ?, reorder_quantity = ?,
                        lead_time_days = ?, preferred_supplier_id = ?, notes = ?
                    WHERE product_id = ?
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                    pstmt.setInt(1, minQuantity);
                    pstmt.setInt(2, reorderQuantity);
                    pstmt.setInt(3, (Integer) leadTimeSpinner.getValue());
                    if (supplierId != null) {
                        pstmt.setInt(4, supplierId);
                    } else {
                        pstmt.setNull(4, Types.INTEGER);
                    }
                    pstmt.setString(5, notesArea.getText().trim());
                    pstmt.setInt(6, minStock.getProductId());
                    pstmt.executeUpdate();
                }
            } else {
                // Insert new record
                String insertQuery = """
                    INSERT INTO minimum_stock (
                        product_id, minimum_quantity, reorder_quantity,
                        lead_time_days, preferred_supplier_id, notes
                    ) VALUES (?, ?, ?, ?, ?, ?)
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {
                    pstmt.setInt(1, minStock.getProductId());
                    pstmt.setInt(2, minQuantity);
                    pstmt.setInt(3, reorderQuantity);
                    pstmt.setInt(4, (Integer) leadTimeSpinner.getValue());
                    if (supplierId != null) {
                        pstmt.setInt(5, supplierId);
                    } else {
                        pstmt.setNull(5, Types.INTEGER);
                    }
                    pstmt.setString(6, notesArea.getText().trim());
                    pstmt.executeUpdate();
                }
            }

            stockSaved = true;
            dispose();

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error saving minimum stock: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isStockSaved() {
        return stockSaved;
    }

    // Helper class for supplier combo box
    private static class SupplierComboItem {
        private Integer id;
        private String name;

        public SupplierComboItem(Integer id, String name) {
            this.id = id;
            this.name = name;
        }

        public Integer getId() { return id; }

        @Override
        public String toString() {
            return name;
        }
    }
}
