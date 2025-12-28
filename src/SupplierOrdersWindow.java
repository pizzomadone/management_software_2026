// File: SupplierOrdersWindow.java
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Vector;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SupplierOrdersWindow extends JDialog {
    private int supplierId;
    private String supplierName;
    private JTable ordersTable;
    private DefaultTableModel tableModel;
    private JButton addButton;
    private JButton editButton;
    private JButton deleteButton;
    private JButton refreshButton;
    private SimpleDateFormat dateFormat;

    public SupplierOrdersWindow(JDialog parent, int supplierId, String supplierName) {
        super(parent, "Supplier Orders: " + supplierName, true);
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");

        setupWindow();
        initComponents();
        loadOrders();
    }

    // Constructor for JFrame parent
    public SupplierOrdersWindow(JFrame parent, int supplierId, String supplierName) {
        super(parent, "Supplier Orders: " + supplierName, true);
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");

        setupWindow();
        initComponents();
        loadOrders();
    }

    private void setupWindow() {
        setSize(900, 600);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(10, 10));
    }

    private void initComponents() {
        // Orders table
        String[] columns = {"Number", "Date", "Delivery Date", "Status", "Total €", "Notes"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        ordersTable = new JTable(tableModel);
        ordersTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ordersTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        // Buttons panel
        JPanel buttonPanel = new JPanel();
        addButton = new JButton("New Order");
        editButton = new JButton("Edit");
        deleteButton = new JButton("Delete");
        refreshButton = new JButton("Refresh");

        addButton.addActionListener(e -> showOrderDialog(null));
        editButton.addActionListener(e -> editSelectedOrder());
        deleteButton.addActionListener(e -> deleteSelectedOrder());
        refreshButton.addActionListener(e -> loadOrders());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(refreshButton);

        // Main layout
        add(new JScrollPane(ordersTable), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean isRowSelected = ordersTable.getSelectedRow() != -1;
        editButton.setEnabled(isRowSelected);
        deleteButton.setEnabled(isRowSelected);
    }

    private void loadOrders() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT * FROM supplier_orders
                WHERE supplier_id = ?
                ORDER BY order_date DESC
            """;

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, supplierId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getString("number"));

                    // FIXED: Use DateUtils for proper date parsing
                    Date orderDate = DateUtils.parseDate(rs, "order_date");
                    if (orderDate != null) {
                        row.add(DateUtils.formatDate(orderDate, dateFormat));
                    } else {
                        row.add("");
                    }

                    // FIXED: Use DateUtils for delivery date parsing
                    Date deliveryDate = DateUtils.parseDate(rs, "expected_delivery_date");
                    if (deliveryDate != null) {
                        row.add(DateUtils.formatDate(deliveryDate, dateFormat));
                    } else {
                        row.add("");
                    }

                    row.add(rs.getString("status"));
                    row.add(String.format("%.2f", rs.getDouble("total")));
                    row.add(rs.getString("notes"));
                    tableModel.addRow(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading orders: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showOrderDialog(SupplierOrder order) {
        SupplierOrderDialog dialog = new SupplierOrderDialog(this, supplierId, supplierName, order);
        dialog.setVisible(true);
        if (dialog.isOrderSaved()) {
            loadOrders();
        }
    }

    private void editSelectedOrder() {
        int selectedRow = ordersTable.getSelectedRow();
        if (selectedRow != -1) {
            String number = (String)tableModel.getValueAt(selectedRow, 0);
            try {
                SupplierOrder order = loadOrderByNumber(number);
                if (order != null) {
                    showOrderDialog(order);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error loading order: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private SupplierOrder loadOrderByNumber(String number) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        String query = "SELECT * FROM supplier_orders WHERE number = ? AND supplier_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, number);
            pstmt.setInt(2, supplierId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // FIXED: Use DateUtils for proper date parsing
                    Date orderDate = DateUtils.parseDate(rs, "order_date");
                    Date deliveryDate = DateUtils.parseDate(rs, "expected_delivery_date");

                    // Fallback to current date if parsing fails
                    if (orderDate == null) {
                        orderDate = new Date();
                    }

                    SupplierOrder order = new SupplierOrder(
                        rs.getInt("id"),
                        supplierId,
                        supplierName,
                        rs.getString("number"),
                        orderDate,
                        deliveryDate, // Can be null
                        rs.getString("status"),
                        rs.getDouble("total"),
                        rs.getString("notes")
                    );

                    loadOrderItems(order);
                    return order;
                }
            }
        }
        return null;
    }

    private void loadOrderItems(SupplierOrder order) throws SQLException {
        String query = """
            SELECT i.*, p.name as product_name, p.code as product_code
            FROM supplier_order_details i
            JOIN products p ON i.product_id = p.id
            WHERE i.order_id = ?
        """;

        Connection conn = DatabaseManager.getInstance().getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, order.getId());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    SupplierOrderItem item = new SupplierOrderItem(
                        rs.getInt("id"),
                        rs.getInt("order_id"),
                        rs.getInt("product_id"),
                        rs.getString("product_name"),
                        rs.getString("product_code"),
                        rs.getInt("quantity"),
                        rs.getDouble("unit_price"),
                        rs.getDouble("total"),
                        rs.getString("notes")
                    );
                    order.getItems().add(item);
                }
            }
        }
    }

    private void deleteSelectedOrder() {
        int selectedRow = ordersTable.getSelectedRow();
        if (selectedRow != -1) {
            String number = (String)tableModel.getValueAt(selectedRow, 0);

            int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete order " + number + "?",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                try {
                    Connection conn = DatabaseManager.getInstance().getConnection();
                    conn.setAutoCommit(false);

                    try {
                        // First delete order details
                        String deleteDetailsQuery = """
                            DELETE FROM supplier_order_details
                            WHERE order_id = (
                                SELECT id FROM supplier_orders
                                WHERE number = ? AND supplier_id = ?
                            )
                        """;
                        try (PreparedStatement pstmt = conn.prepareStatement(deleteDetailsQuery)) {
                            pstmt.setString(1, number);
                            pstmt.setInt(2, supplierId);
                            pstmt.executeUpdate();
                        }

                        // Then delete the order
                        String deleteOrderQuery = "DELETE FROM supplier_orders WHERE number = ? AND supplier_id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(deleteOrderQuery)) {
                            pstmt.setString(1, number);
                            pstmt.setInt(2, supplierId);
                            pstmt.executeUpdate();
                        }

                        conn.commit();
                        loadOrders();

                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(this,
                        "Error deleting order: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
}
