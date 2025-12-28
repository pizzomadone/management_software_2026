import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.Vector;

public class SupplierSelectionDialog extends JDialog {
    private JTextField searchField;
    private JTable suppliersTable;
    private DefaultTableModel tableModel;
    private Supplier selectedSupplier;
    private boolean supplierSelected = false;

    public SupplierSelectionDialog(JDialog parent) {
        super(parent, "Select Supplier", true);

        setupWindow();
        initComponents();
        loadAllSuppliers();
    }

    private void setupWindow() {
        setSize(700, 500);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(10, 10));
    }

    private void initComponents() {
        // Main panel with padding
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Improved search panel
        JPanel searchPanel = new JPanel(new BorderLayout(10, 10));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Supplier"));

        JPanel searchInputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchField = new JTextField(30);
        searchField.setToolTipText("Search by company name, VAT number, email or phone");

        JButton searchButton = new JButton("Search");
        JButton clearButton = new JButton("Clear");
        JButton newSupplierButton = new JButton("New Supplier");

        // Real-time search while typing
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
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
            loadAllSuppliers();
        });
        newSupplierButton.addActionListener(e -> createNewSupplier());

        searchInputPanel.add(new JLabel("Search:"));
        searchInputPanel.add(searchField);
        searchInputPanel.add(searchButton);
        searchInputPanel.add(clearButton);
        searchInputPanel.add(newSupplierButton);

        searchPanel.add(searchInputPanel, BorderLayout.CENTER);

        // Suppliers table with detailed columns
        String[] columns = {"ID", "Company Name", "VAT Number", "Email", "Phone", "City", "Full Address"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        suppliersTable = new JTable(tableModel);

        // Hide ID column but keep it for data
        suppliersTable.getColumnModel().getColumn(0).setMinWidth(0);
        suppliersTable.getColumnModel().getColumn(0).setMaxWidth(0);
        suppliersTable.getColumnModel().getColumn(0).setPreferredWidth(0);

        // Set column widths
        suppliersTable.getColumnModel().getColumn(1).setPreferredWidth(180); // Company Name
        suppliersTable.getColumnModel().getColumn(2).setPreferredWidth(120); // VAT Number
        suppliersTable.getColumnModel().getColumn(3).setPreferredWidth(180); // Email
        suppliersTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Phone
        suppliersTable.getColumnModel().getColumn(5).setPreferredWidth(100); // City
        suppliersTable.getColumnModel().getColumn(6).setPreferredWidth(200); // Full Address

        // Double-click for selection
        suppliersTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectSupplier();
                }
            }
        });

        // Selection with Enter
        suppliersTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    selectSupplier();
                }
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(suppliersTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Suppliers (Double-click to select)"));

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton selectButton = new JButton("Select");
        JButton cancelButton = new JButton("Cancel");

        selectButton.addActionListener(e -> selectSupplier());
        cancelButton.addActionListener(e -> dispose());

        // Button styling
        selectButton.setPreferredSize(new Dimension(100, 30));
        cancelButton.setPreferredSize(new Dimension(100, 30));
        selectButton.setFont(selectButton.getFont().deriveFont(Font.BOLD));

        buttonPanel.add(selectButton);
        buttonPanel.add(cancelButton);

        // Assembly
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        mainPanel.add(tableScrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Initial focus on search field
        SwingUtilities.invokeLater(() -> searchField.requestFocus());
    }

    private void loadAllSuppliers() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT id, company_name, vat_number, email, phone, address
                FROM suppliers
                ORDER BY company_name
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    addSupplierRow(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading suppliers: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void performSearch() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadAllSuppliers();
            return;
        }

        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT id, company_name, vat_number, email, phone, address
                FROM suppliers
                WHERE LOWER(company_name) LIKE LOWER(?)
                   OR LOWER(vat_number) LIKE LOWER(?)
                   OR LOWER(email) LIKE LOWER(?)
                   OR phone LIKE ?
                   OR LOWER(address) LIKE LOWER(?)
                ORDER BY
                    CASE
                        WHEN LOWER(company_name) LIKE LOWER(?) THEN 1
                        ELSE 2
                    END,
                    company_name
            """;

            String searchPattern = "%" + searchTerm + "%";
            String exactPattern = searchTerm + "%";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, searchPattern);
                pstmt.setString(2, searchPattern);
                pstmt.setString(3, searchPattern);
                pstmt.setString(4, searchPattern);
                pstmt.setString(5, searchPattern);
                pstmt.setString(6, exactPattern); // For ordering

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        addSupplierRow(rs);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error searching suppliers: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addSupplierRow(ResultSet rs) throws SQLException {
        Vector<Object> row = new Vector<>();
        row.add(rs.getInt("id")); // Hidden ID
        row.add(rs.getString("company_name"));
        row.add(rs.getString("vat_number"));
        row.add(rs.getString("email"));
        row.add(rs.getString("phone"));

        // Extract city from address (take the last part after comma)
        String fullAddress = rs.getString("address");
        String city = "";
        if (fullAddress != null && !fullAddress.isEmpty()) {
            String[] parts = fullAddress.split(",");
            if (parts.length > 1) {
                city = parts[parts.length - 1].trim();
            }
        }

        row.add(city);
        row.add(fullAddress);
        tableModel.addRow(row);
    }

    private void selectSupplier() {
        int selectedRow = suppliersTable.getSelectedRow();
        if (selectedRow != -1) {
            // Get complete supplier data from database
            int supplierId = (int)tableModel.getValueAt(selectedRow, 0);
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
                            supplierSelected = true;
                            dispose();
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error loading supplier details: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "Please select a supplier from the list",
                "No Selection", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void createNewSupplier() {
        SupplierDialog dialog = new SupplierDialog(this, null);
        dialog.setVisible(true);
        if (dialog.isSupplierSaved()) {
            // Reload list and select the new supplier
            loadAllSuppliers();
        }
    }

    public Supplier getSelectedSupplier() {
        return selectedSupplier;
    }

    public boolean isSupplierSelected() {
        return supplierSelected;
    }
}
