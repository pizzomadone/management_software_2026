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
    private JButton productsButton;
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
        searchPanel.add(TableInteractionUtil.withClearButton(searchField, this::searchSuppliers));
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
        TableStyleUtil.applyCompact(suppliersTable);
        suppliersTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suppliersTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        // Enable column sorting
        TableSorterUtil.enableSorting(suppliersTable);

        // Add Delete key shortcut
        TableInteractionUtil.addDeleteKeyAction(suppliersTable, this::deleteSelectedSupplier);

        // Add context menu
        TableInteractionUtil.addContextMenu(suppliersTable,
            new TableInteractionUtil.TableAction("New Supplier", () -> showSupplierDialog(null), false),
            new TableInteractionUtil.TableAction("Edit", this::editSelectedSupplier),
            new TableInteractionUtil.TableAction("Delete", this::deleteSelectedSupplier),
            null, // Separator
            new TableInteractionUtil.TableAction("Orders", this::showSupplierOrders),
            new TableInteractionUtil.TableAction("Products Supplied", this::showSupplierProducts),
            null, // Separator
            new TableInteractionUtil.TableAction("Refresh", this::loadSuppliers, false)
        );

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
        productsButton = new JButton("Products Supplied");
        refreshButton = new JButton("Refresh");

        addButton.addActionListener(e -> showSupplierDialog(null));
        editButton.addActionListener(e -> editSelectedSupplier());
        deleteButton.addActionListener(e -> deleteSelectedSupplier());
        ordersButton.addActionListener(e -> showSupplierOrders());
        productsButton.addActionListener(e -> showSupplierProducts());
        refreshButton.addActionListener(e -> loadSuppliers());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(ordersButton);
        buttonPanel.add(productsButton);
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
        productsButton.setEnabled(isRowSelected);
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
            Dialogs.showMessageDialog(this,
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
            Dialogs.showMessageDialog(this,
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
            // Convert view index to model index (important when table is sorted)
            int modelRow = suppliersTable.convertRowIndexToModel(selectedRow);
            Supplier supplier = new Supplier(
                (int)tableModel.getValueAt(modelRow, 0),
                (String)tableModel.getValueAt(modelRow, 1),
                (String)tableModel.getValueAt(modelRow, 2),
                "", // tax code
                (String)tableModel.getValueAt(modelRow, 5), // address
                (String)tableModel.getValueAt(modelRow, 4), // phone
                (String)tableModel.getValueAt(modelRow, 3), // email
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
            // Convert view index to model index (important when table is sorted)
            int modelRow = suppliersTable.convertRowIndexToModel(selectedRow);
            int id = (int)tableModel.getValueAt(modelRow, 0);
            String name = (String)tableModel.getValueAt(modelRow, 1);

            try {
                Connection conn = DatabaseManager.getInstance().getConnection();

                // Check for existing dependencies
                boolean hasOrders = hasSupplierOrders(conn, id);
                int suppliedProductCount = countSupplierProducts(conn, id);
                int preferredProductCount = countPreferredSupplierProducts(conn, id);
                boolean hasSupplierProducts = suppliedProductCount > 0;

                if (hasOrders || hasSupplierProducts) {
                    StringBuilder message = new StringBuilder();
                    message.append("Supplier '").append(name).append("' has related business records:\n");

                    if (hasOrders) message.append("- Existing orders\n");
                    if (hasSupplierProducts) {
                        message.append("- ").append(suppliedProductCount)
                            .append(" supplied product(s) will be disassociated\n");
                        if (preferredProductCount > 0) {
                            message.append("- For ").append(preferredProductCount)
                                .append(" product(s), this is the preferred supplier. ")
                                .append("WareStat will automatically choose the remaining supplier with the lowest purchase price; ")
                                .append("if no alternative exists, the product will remain without a preferred supplier.\n");
                        }
                    }

                    message.append("\nChoose what should happen:\n");
                    message.append("- Keep: nothing is deleted; supplier, orders, stock and references remain unchanged.\n");
                    message.append("- Delete all: supplier orders and product associations are permanently deleted; stock received from completed orders is reversed and related movements are deleted.");

                    String[] options = {"Keep Supplier and Records", "Delete Supplier and All Related Data"};
                    int choice = Dialogs.showOptionDialog(this,
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
                int result = Dialogs.showConfirmDialog(this,
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

                        Dialogs.showMessageDialog(this,
                            "Supplier deleted successfully",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                Dialogs.showMessageDialog(this,
                    "Error deleting supplier: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void performCascadeDelete(Connection conn, int id, String name) {
        int confirmResult = Dialogs.showConfirmDialog(this,
            "WARNING: This will permanently delete supplier '" + name + "' and ALL related data:\n" +
            "- All orders from this supplier\n" +
            "- All supplied-product associations\n" +
            "- Preferred-supplier references linked to those products\n" +
            "- All related warehouse movements\n\n" +
            "Stock received from completed supplier orders will be removed before deletion.\n\n" +
            "This action CANNOT be undone!\n\n" +
            "Are you absolutely sure?",
            "FORCE DELETE - Final Confirmation",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.ERROR_MESSAGE);

        if (confirmResult != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            DatabaseManager.beginTransaction(conn);

            try {
                java.util.LinkedHashMap<Integer,String> preferredProducts =
                    new java.util.LinkedHashMap<>();
                try (PreparedStatement q = conn.prepareStatement("""
                        SELECT p.id,p.code,p.name
                        FROM product_suppliers ps
                        JOIN products p ON p.id=ps.product_id
                        WHERE ps.supplier_id=? AND ps.is_preferred=1
                        ORDER BY p.name,p.code
                        """)) {
                    q.setInt(1,id);
                    try (ResultSet r=q.executeQuery()) {
                        while(r.next()) {
                            String productName=r.getString("code")+" - "+r.getString("name");
                            preferredProducts.put(r.getInt("id"),productName);
                        }
                    }
                }
                java.util.List<String> preferredChanges = new java.util.ArrayList<>();
                int productsWithoutAlternative = 0;

                StockManager.reverseCompletedSupplierOrders(conn, id);
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

                // 3. Remove product/supplier associations. Triggers keep the
                // legacy preferred-supplier caches synchronized.
                String deleteRelations = "DELETE FROM product_suppliers WHERE supplier_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteRelations)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " supplied-product associations");
                }

                // Reassign the preferred supplier for every affected product to
                // the remaining relation with the lowest current purchase price.
                for (java.util.Map.Entry<Integer,String> affected : preferredProducts.entrySet()) {
                    try (PreparedStatement q = conn.prepareStatement("""
                            SELECT ps.id,s.company_name,ps.purchase_price
                            FROM product_suppliers ps
                            JOIN suppliers s ON s.id=ps.supplier_id
                            WHERE ps.product_id=?
                            ORDER BY ps.purchase_price ASC,ps.id ASC
                            LIMIT 1
                            """)) {
                        q.setInt(1,affected.getKey());
                        try (ResultSet r=q.executeQuery()) {
                            if (r.next()) {
                                try (PreparedStatement u=conn.prepareStatement(
                                        "UPDATE product_suppliers SET is_preferred=1 WHERE id=?")) {
                                    u.setInt(1,r.getInt("id"));
                                    u.executeUpdate();
                                }
                                preferredChanges.add(affected.getValue()+" → "+
                                    r.getString("company_name")+" ("+
                                    SettingsPanel.formatUnitCurrency(r.getDouble("purchase_price"))+")");
                            } else {
                                productsWithoutAlternative++;
                            }
                        }
                    }
                }

                // 4. Remove any remaining legacy minimum-stock cache references
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

                // 5. Remove supplier references from products
                String updateProducts = """
                    UPDATE products
                    SET supplier_id = NULL
                    WHERE supplier_id = ?
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(updateProducts)) {
                    pstmt.setInt(1, id);
                    int updated = pstmt.executeUpdate();
                    System.out.println("Updated " + updated + " product supplier references");
                }

                // 6. Finally delete the supplier
                String deleteSupplier = "DELETE FROM suppliers WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteSupplier)) {
                    pstmt.setInt(1, id);
                    pstmt.executeUpdate();
                    System.out.println("Deleted supplier");
                }

                conn.commit();
                loadSuppliers();

                StringBuilder completedMessage = new StringBuilder(
                    I18n.format("Supplier '{0}' and all related records deleted successfully.", name));
                if (!preferredChanges.isEmpty()) {
                    completedMessage.append("\n\n")
                        .append(I18n.tr("Preferred supplier changed automatically to the lowest-cost remaining supplier for:"))
                        .append("\n");
                    for (String change : preferredChanges)
                        completedMessage.append("- ").append(change).append("\n");
                }
                if (productsWithoutAlternative > 0) {
                    completedMessage.append("\n").append(I18n.format(
                        "{0} product(s) now have no preferred supplier because no alternative supplier remained.",
                        productsWithoutAlternative));
                }
                Dialogs.showMessageDialog(this,
                    completedMessage.toString(),
                    I18n.tr("Supplier Deleted"),
                    JOptionPane.INFORMATION_MESSAGE);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
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

    private int countSupplierProducts(Connection conn, int supplierId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM product_suppliers WHERE supplier_id=?")) {
            pstmt.setInt(1, supplierId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int countPreferredSupplierProducts(Connection conn, int supplierId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM product_suppliers WHERE supplier_id=? AND is_preferred=1")) {
            pstmt.setInt(1, supplierId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void showSupplierOrders() {
        int selectedRow = suppliersTable.getSelectedRow();
        if (selectedRow != -1) {
            // Convert view index to model index (important when table is sorted)
            int modelRow = suppliersTable.convertRowIndexToModel(selectedRow);
            int supplierId = (int)tableModel.getValueAt(modelRow, 0);
            String supplierName = (String)tableModel.getValueAt(modelRow, 1);

            Window parentWindow = SwingUtilities.getWindowAncestor(this);

            SupplierOrdersWindow ordersWindow;
            if (parentWindow instanceof JFrame) {
                ordersWindow = new SupplierOrdersWindow((JFrame) parentWindow, supplierId, supplierName);
            } else {
                ordersWindow = new SupplierOrdersWindow((JDialog) parentWindow, supplierId, supplierName);
            }

            ordersWindow.setVisible(true);

            // Refresh ProductsPanel after closing supplier orders window
            // (stock may have been updated by completing orders)
            if (parentWindow instanceof MainWindow) {
                ((MainWindow) parentWindow).refreshProductsPanel();
            }
        }
    }

    private void showSupplierProducts() {
        int selectedRow = suppliersTable.getSelectedRow();
        if (selectedRow != -1) {
            // Convert view index to model index (important when table is sorted)
            int modelRow = suppliersTable.convertRowIndexToModel(selectedRow);
            int supplierId = (int)tableModel.getValueAt(modelRow, 0);
            String supplierName = (String)tableModel.getValueAt(modelRow, 1);

            Window parentWindow = SwingUtilities.getWindowAncestor(this);

            SupplierProductsWindow productsWindow;
            if (parentWindow instanceof JFrame) {
                productsWindow = new SupplierProductsWindow((JFrame) parentWindow, supplierId, supplierName);
            } else {
                productsWindow = new SupplierProductsWindow((JDialog) parentWindow, supplierId, supplierName);
            }

            productsWindow.setVisible(true);
        }
    }
    public void refreshData() { loadSuppliers(); }
}
