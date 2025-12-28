import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.Vector;

public class CustomersPanel extends JPanel {
    private JTable customersTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JButton addButton;
    private JButton editButton;
    private JButton deleteButton;
    private JButton refreshButton;

    public CustomersPanel() {
        setupPanel();
        initComponents();
        loadCustomers();
    }

    private void setupPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void initComponents() {
        // Search Panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Customers"));

        searchField = new JTextField(25);
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchCustomers());

        searchPanel.add(new JLabel("Search: "));
        searchPanel.add(searchField);
        searchPanel.add(searchButton);

        // Customers table
        String[] columns = {"ID", "Name", "Surname", "Email", "Phone", "Address"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        customersTable = new JTable(tableModel);
        customersTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        customersTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        // Add mouse listener for double click
        customersTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(evt)) {
                    int selectedRow = customersTable.getSelectedRow();
                    if (selectedRow != -1) {
                        editSelectedCustomer();
                    }
                }
            }
        });

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        addButton = new JButton("Add Customer");
        editButton = new JButton("Modify");
        deleteButton = new JButton("Delete");
        refreshButton = new JButton("Refresh");

        addButton.addActionListener(e -> showCustomerDialog(null));
        editButton.addActionListener(e -> editSelectedCustomer());
        deleteButton.addActionListener(e -> deleteSelectedCustomer());
        refreshButton.addActionListener(e -> loadCustomers());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(refreshButton);

        // Main layout
        add(searchPanel, BorderLayout.NORTH);
        add(new JScrollPane(customersTable), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean isRowSelected = customersTable.getSelectedRow() != -1;
        editButton.setEnabled(isRowSelected);
        deleteButton.setEnabled(isRowSelected);
    }

    private void loadCustomers() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = "SELECT * FROM customers ORDER BY last_name, first_name";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getInt("id"));
                    row.add(rs.getString("first_name"));
                    row.add(rs.getString("last_name"));
                    row.add(rs.getString("email"));
                    row.add(rs.getString("phone"));
                    row.add(rs.getString("address"));
                    tableModel.addRow(row);
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                "Error while loading customers: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void searchCustomers() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadCustomers();
            return;
        }

        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT * FROM customers
                WHERE first_name LIKE ? OR last_name LIKE ? OR email LIKE ? OR phone LIKE ?
                ORDER BY last_name, first_name
            """;
            String searchPattern = "%" + searchTerm + "%";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, searchPattern);
                pstmt.setString(2, searchPattern);
                pstmt.setString(3, searchPattern);
                pstmt.setString(4, searchPattern);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Vector<Object> row = new Vector<>();
                        row.add(rs.getInt("id"));
                        row.add(rs.getString("first_name"));
                        row.add(rs.getString("last_name"));
                        row.add(rs.getString("email"));
                        row.add(rs.getString("phone"));
                        row.add(rs.getString("address"));
                        tableModel.addRow(row);
                    }
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                "Error while searching for customers: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showCustomerDialog(Customer customer) {
        // Get the parent window for the dialog
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JFrame parentFrame = (parentWindow instanceof JFrame) ? (JFrame) parentWindow : null;

        CustomerDialog dialog = new CustomerDialog(parentFrame, customer);
        dialog.setVisible(true);
        if (dialog.isCustomerSaved()) {
            loadCustomers();
        }
    }

    private void editSelectedCustomer() {
        int selectedRow = customersTable.getSelectedRow();
        if (selectedRow != -1) {
            Customer customer = new Customer(
                (int)tableModel.getValueAt(selectedRow, 0),
                (String)tableModel.getValueAt(selectedRow, 1),
                (String)tableModel.getValueAt(selectedRow, 2),
                (String)tableModel.getValueAt(selectedRow, 3),
                (String)tableModel.getValueAt(selectedRow, 4),
                (String)tableModel.getValueAt(selectedRow, 5)
            );
            showCustomerDialog(customer);
        }
    }

    private void deleteSelectedCustomer() {
        int selectedRow = customersTable.getSelectedRow();
        if (selectedRow != -1) {
            int id = (int)tableModel.getValueAt(selectedRow, 0);
            String firstName = (String)tableModel.getValueAt(selectedRow, 1);
            String lastName = (String)tableModel.getValueAt(selectedRow, 2);
            String fullName = firstName + " " + lastName;

            try {
                Connection conn = DatabaseManager.getInstance().getConnection();

                // Check for existing dependencies
                boolean hasOrders = hasCustomerOrders(conn, id);
                boolean hasInvoices = hasCustomerInvoices(conn, id);

                if (hasOrders || hasInvoices) {
                    StringBuilder message = new StringBuilder();
                    message.append("Cannot delete customer '").append(fullName).append("' because they have:\n");

                    if (hasOrders) message.append("- Existing orders\n");
                    if (hasInvoices) message.append("- Invoices\n");

                    message.append("\nOptions:\n");
                    message.append("1. Delete/reassign related records first\n");
                    message.append("2. Use 'Force Delete' to remove all related data");

                    String[] options = {"Cancel", "Force Delete (All Data)"};
                    int choice = JOptionPane.showOptionDialog(this,
                        message.toString(),
                        "Cannot Delete Customer",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null, options, options[0]);

                    if (choice == 1) { // Force Delete
                        performCascadeDelete(conn, id, fullName);
                    }
                    return;
                }

                // Safe to delete - no foreign key references
                int result = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete the customer '" + fullName + "'?",
                    "Confirm Deletion",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

                if (result == JOptionPane.YES_OPTION) {
                    String query = "DELETE FROM customers WHERE id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                        pstmt.setInt(1, id);
                        pstmt.executeUpdate();
                        loadCustomers();

                        JOptionPane.showMessageDialog(this,
                            "Customer deleted successfully",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error while deleting the customer: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void performCascadeDelete(Connection conn, int id, String fullName) {
        int confirmResult = JOptionPane.showConfirmDialog(this,
            "WARNING: This will permanently delete customer '" + fullName + "' and ALL related data:\n" +
            "- All orders from this customer\n" +
            "- All invoices\n" +
            "- All order and invoice details\n\n" +
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

                // 1. Delete order details for this customer's orders
                String deleteOrderDetails = """
                    DELETE FROM order_details
                    WHERE order_id IN (
                        SELECT id FROM orders WHERE customer_id = ?
                    )
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(deleteOrderDetails)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " order details");
                }

                // 2. Delete customer orders
                String deleteOrders = "DELETE FROM orders WHERE customer_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteOrders)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " orders");
                }

                // 3. Delete invoice details for this customer's invoices
                String deleteInvoiceDetails = """
                    DELETE FROM invoice_details
                    WHERE invoice_id IN (
                        SELECT id FROM invoices WHERE customer_id = ?
                    )
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(deleteInvoiceDetails)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " invoice details");
                }

                // 4. Delete customer invoices
                String deleteInvoices = "DELETE FROM invoices WHERE customer_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteInvoices)) {
                    pstmt.setInt(1, id);
                    int deleted = pstmt.executeUpdate();
                    System.out.println("Deleted " + deleted + " invoices");
                }

                // 5. Finally delete the customer
                String deleteCustomer = "DELETE FROM customers WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteCustomer)) {
                    pstmt.setInt(1, id);
                    pstmt.executeUpdate();
                    System.out.println("Deleted customer");
                }

                conn.commit();
                loadCustomers();

                JOptionPane.showMessageDialog(this,
                    "Customer '" + fullName + "' and all related records deleted successfully",
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

    private boolean hasCustomerOrders(Connection conn, int customerId) throws SQLException {
        String query = "SELECT COUNT(*) FROM orders WHERE customer_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, customerId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean hasCustomerInvoices(Connection conn, int customerId) throws SQLException {
        String query = "SELECT COUNT(*) FROM invoices WHERE customer_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, customerId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }
}
