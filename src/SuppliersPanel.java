import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Vector;

public class SuppliersPanel extends JPanel {
    private JTable suppliersTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JButton addButton;
    private JButton editButton;
    private JButton deleteButton;
    private JButton ordersButton;
    private JButton priceListButton;
    private JButton refreshButton;

    public SuppliersPanel() {
        setupPanel();
        initComponents();
        loadSuppliers();
    }

    private void setupPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void initComponents() {
        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Suppliers"));

        searchField = new JTextField(25);
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchSuppliers());

        searchPanel.add(new JLabel("Search: "));
        searchPanel.add(searchField);
        searchPanel.add(searchButton);

        // Suppliers table
        String[] columns = {"ID", "Company Name", "VAT No.", "Email", "Phone", "Address"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        suppliersTable = new JTable(tableModel);
        suppliersTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suppliersTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        // Add mouse listener for double click
        suppliersTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(evt)) {
                    int selectedRow = suppliersTable.getSelectedRow();
                    if (selectedRow != -1) {
                        editSelectedSupplier();
                    }
                }
            }
        });

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        addButton = new JButton("New Supplier");
        editButton = new JButton("Edit");
        deleteButton = new JButton("Delete");
        ordersButton = new JButton("Orders");
        priceListButton = new JButton("Price List");
        refreshButton = new JButton("Refresh");

        addButton.addActionListener(e -> showSupplierDialog(null));
        editButton.addActionListener(e -> editSelectedSupplier());
        deleteButton.addActionListener(e -> deleteSelectedSupplier());
        ordersButton.addActionListener(e -> showSupplierOrders());
        priceListButton.addActionListener(e -> showSupplierPriceList());
        refreshButton.addActionListener(e -> loadSuppliers());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(ordersButton);
        buttonPanel.add(priceListButton);
        buttonPanel.add(refreshButton);

        // Main layout
        add(searchPanel, BorderLayout.NORTH);
        add(new JScrollPane(suppliersTable), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean isRowSelected = suppliersTable.getSelectedRow() != -1;
        editButton.setEnabled(isRowSelected);
        deleteButton.setEnabled(isRowSelected);
        ordersButton.setEnabled(isRowSelected);
        priceListButton.setEnabled(isRowSelected);
    }

    private void loadSuppliers() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = "SELECT * FROM suppliers ORDER BY company_name";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getInt("id"));
                    row.add(rs.getString("company_name"));
                    row.add(rs.getString("vat_number"));
                    row.add(rs.getString("email"));
                    row.add(rs.getString("phone"));
                    row.add(rs.getString("address"));
                    tableModel.addRow(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading suppliers: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void searchSuppliers() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadSuppliers();
            return;
        }

        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT * FROM suppliers
                WHERE company_name LIKE ?
                   OR vat_number LIKE ?
                   OR email LIKE ?
                ORDER BY company_name
            """;

            String searchPattern = "%" + searchTerm + "%";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, searchPattern);
                pstmt.setString(2, searchPattern);
                pstmt.setString(3, searchPattern);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Vector<Object> row = new Vector<>();
                        row.add(rs.getInt("id"));
                        row.add(rs.getString("company_name"));
                        row.add(rs.getString("vat_number"));
                        row.add(rs.getString("email"));
                        row.add(rs.getString("phone"));
                        row.add(rs.getString("address"));
                        tableModel.addRow(row);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error searching for suppliers: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showSupplierDialog(Supplier supplier) {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);

        SupplierDialog dialog;
        if (parentWindow instanceof JFrame) {
            dialog = new SupplierDialog((JFrame) parentWindow, supplier);
        } else {
            dialog = new SupplierDialog((JDialog) parentWindow, supplier);
        }

        dialog.setVisible(true);
        if (dialog.isSupplierSaved()) {
            loadSuppliers();
        }
    }

    private void editSelectedSupplier() {
        int selectedRow = suppliersTable.getSelectedRow();
        if (selectedRow != -1) {
            Supplier supplier = new Supplier(
                (int)tableModel.getValueAt(selectedRow, 0),
                (String)tableModel.getValueAt(selectedRow, 1),
                (String)tableModel.getValueAt(selectedRow, 2),
                "", // tax code
                (String)tableModel.getValueAt(selectedRow, 5), // address
                (String)tableModel.getValueAt(selectedRow, 4), // phone
                (String)tableModel.getValueAt(selectedRow, 3), // email
                "", // certified email
                "", // website
                ""  // notes
            );
            showSupplierDialog(supplier);
        }
    }

    private void deleteSelectedSupplier() {
        int selectedRow = suppliersTable.getSelectedRow();
        if (selectedRow != -1) {
            int id = (int)tableModel.getValueAt(selectedRow, 0);
            String name = (String)tableModel.getValueAt(selectedRow, 1);

            try {
                Connection conn = DatabaseManager.getInstance().getConnection();

                // Check for existing dependencies
                boolean hasOrders = hasSupplierOrders(conn, id);
                boolean hasPriceList = hasSupplierPriceList(conn, id);
                boolean hasMinStockReferences = hasMinStockReferences(conn, id);

                if (hasOrders || hasPriceList || hasMinStockReferences) {
                    StringBuilder message = new StringBuilder();
                    message.append("Cannot delete supplier '").append(name).append("' because it has:\n");

                    if (hasOrders) message.append("- Existing orders\n");
                    if (hasPriceList) message.append("- Price list entries\n");
                    if (hasMinStockReferences) message.append("- Minimum stock references\n");

                    message.append("\nOptions:\n");
                    message.append("1. Delete/reassign related records first\n");
                    message.append("2. Use 'Force Delete' to remove all related data");

                    String[] options = {"Cancel", "Force Delete (All Data)"};
                    int choice = JOptionPane.showOptionDialog(this,
                        message.toString(),
                        "Cannot Delete Supplier",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null, options, options[0]);

                    if (choice == 1) { // Force Delete
                        performCascadeDelete(conn, id, name);
                    }
                    return;
                }

                // Safe to delete - no foreign key references
                int result = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete supplier '" + name + "'?",
                    "Confirm Deletion",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

                if (result == JOptionPane.YES_OPTION) {
                    String query = "DELETE FROM suppliers WHERE id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                        pstmt.setInt(1, id);
                        pstmt.executeUpdate();
                        loadSuppliers();

                        JOptionPane.showMessageDialog(this,
                            "Supplier deleted successfully",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error deleting supplier: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void performCascadeDelete(Connection conn, int id, String name) {
        int confirmResult = JOptionPane.showConfirmDialog(this,
            "WARNING: This will permanently delete supplier '" + name + "' and ALL related data:\n" +
            "- All orders from this supplier\n" +
            "- All price list entries\n" +
            "- All references in minimum stock settings\n\n" +
            "This action CANNOT be undone!\n\n" +
            "Are you absolutely sure?",
            "FORCE DELETE - Final Confirmation",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.ERROR_MESSAGE);

        if (confirmResult != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            conn.setAutoCommit(false);

            try {
                // Delete in order to respect foreign key constraints

                // 1. Delete supplier order details
                String deleteOrderDetails = """
                    DELETE FROM supplier_order_details
                    WHERE order_id IN (
                        SELECT id FROM supplier_orders WHERE supplier_id = ?
                    )
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(deleteOrderDetails)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " order details");
                }

                // 2. Delete supplier orders
                String deleteOrders = "DELETE FROM supplier_orders WHERE supplier_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteOrders)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " orders");
                }

                // 3. Delete price lists
                String deletePriceLists = "DELETE FROM supplier_price_lists WHERE supplier_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deletePriceLists)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " price list entries");
                }

                // 4. Remove supplier references from minimum stock
                String updateMinStock = """
                    UPDATE minimum_stock
                    SET preferred_supplier_id = NULL
                    WHERE preferred_supplier_id = ?
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(updateMinStock)) {
                    pstmt.setInt(1, id);
                    int updated = pstmt.executeUpdate();
                    System.out.println("Updated " + updated + " minimum stock references");
                }

                // 5. Finally delete the supplier
                String deleteSupplier = "DELETE FROM suppliers WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteSupplier)) {
                    pstmt.setInt(1, id);
                    pstmt.executeUpdate();
                    System.out.println("Deleted supplier");
                }

                conn.commit();
                loadSuppliers();

                JOptionPane.showMessageDialog(this,
                    "Supplier '" + name + "' and all related records deleted successfully",
                    "Force Delete Completed",
                    JOptionPane.INFORMATION_MESSAGE);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error during force delete: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean hasSupplierOrders(Connection conn, int supplierId) throws SQLException {
        String query = "SELECT COUNT(*) FROM supplier_orders WHERE supplier_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, supplierId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean hasSupplierPriceList(Connection conn, int supplierId) throws SQLException {
        String query = "SELECT COUNT(*) FROM supplier_price_lists WHERE supplier_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, supplierId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean hasMinStockReferences(Connection conn, int supplierId) throws SQLException {
        String query = "SELECT COUNT(*) FROM minimum_stock WHERE preferred_supplier_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, supplierId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private void showSupplierOrders() {
        int selectedRow = suppliersTable.getSelectedRow();
        if (selectedRow != -1) {
            int supplierId = (int)tableModel.getValueAt(selectedRow, 0);
            String supplierName = (String)tableModel.getValueAt(selectedRow, 1);

            Window parentWindow = SwingUtilities.getWindowAncestor(this);

            SupplierOrdersWindow ordersWindow;
            if (parentWindow instanceof JFrame) {
                ordersWindow = new SupplierOrdersWindow((JFrame) parentWindow, supplierId, supplierName);
            } else {
                ordersWindow = new SupplierOrdersWindow((JDialog) parentWindow, supplierId, supplierName);
            }

            ordersWindow.setVisible(true);
        }
    }

    private void showSupplierPriceList() {
        int selectedRow = suppliersTable.getSelectedRow();
        if (selectedRow != -1) {
            int supplierId = (int)tableModel.getValueAt(selectedRow, 0);
            String supplierName = (String)tableModel.getValueAt(selectedRow, 1);

            Window parentWindow = SwingUtilities.getWindowAncestor(this);

            SupplierPriceListWindow priceListWindow;
            if (parentWindow instanceof JFrame) {
                priceListWindow = new SupplierPriceListWindow((JFrame) parentWindow, supplierId, supplierName);
            } else {
                priceListWindow = new SupplierPriceListWindow((JDialog) parentWindow, supplierId, supplierName);
            }

            priceListWindow.setVisible(true);
        }
    }
}
