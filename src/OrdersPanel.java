import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Vector;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;

public class OrdersPanel extends JPanel {
    private JTable ordersTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JButton addButton;
    private JButton editButton;
    private JButton deleteButton;
    private JButton generateInvoiceButton;
    private JButton refreshButton;
    private SimpleDateFormat dateFormat;
    private Window parentWindow;

    public OrdersPanel() {
        dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        setupPanel();
        initComponents();
        loadOrders();
    }

    private void setupPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void initComponents() {
        // Search Panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Orders"));

        searchField = new JTextField(25);
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchOrders());

        searchPanel.add(new JLabel("Search: "));
        searchPanel.add(TableInteractionUtil.withClearButton(searchField, this::searchOrders));
        searchPanel.add(searchButton);

        // Orders table
        String[] columns = {"ID", "Customer", "Date", "Status", "Total incl. VAT", "Payment"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        ordersTable = new JTable(tableModel);
        TableStyleUtil.applyCompact(ordersTable);
        I18n.localize(ordersTable);
        ordersTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ordersTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        // Enable column sorting with date support (column 2 is "Date")
        TableSorterUtil.enableSorting(ordersTable, new int[]{2}, dateFormat);

        // Add Delete key shortcut
        TableInteractionUtil.addDeleteKeyAction(ordersTable, this::deleteSelectedOrder);

        // Add context menu
        TableInteractionUtil.addContextMenu(ordersTable,
            new TableInteractionUtil.TableAction("New Customer Order", () -> showOrderDialog(null), false),
            new TableInteractionUtil.TableAction("Modify", this::editSelectedOrder),
            new TableInteractionUtil.TableAction("Delete", this::deleteSelectedOrder),
            null, // Separator
            TableInteractionUtil.TableAction.dynamic(
                () -> linkedInvoiceNumberForSelectedOrder() != null
                    ? "Go to Invoice" : "Generate Invoice",
                this::generateInvoiceFromOrder),
            null, // Separator
            new TableInteractionUtil.TableAction("Refresh", this::loadOrders, false)
        );

        // Add mouse listener for double click
        ordersTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(evt)) {
                    int selectedRow = ordersTable.getSelectedRow();
                    if (selectedRow != -1) {
                        editSelectedOrder();
                    }
                }
            }
        });

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        addButton = new JButton("New Customer Order");
        editButton = new JButton("Modify");
        deleteButton = new JButton("Delete");
        generateInvoiceButton = new JButton("Generate Invoice");
        refreshButton = new JButton("Refresh");

        generateInvoiceButton.setFont(generateInvoiceButton.getFont().deriveFont(Font.BOLD));
        generateInvoiceButton.setPreferredSize(new Dimension(150, 30));
        generateInvoiceButton.setOpaque(true);

        addButton.addActionListener(e -> showOrderDialog(null));
        editButton.addActionListener(e -> editSelectedOrder());
        deleteButton.addActionListener(e -> deleteSelectedOrder());
        generateInvoiceButton.addActionListener(e -> generateInvoiceFromOrder());
        refreshButton.addActionListener(e -> loadOrders());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(generateInvoiceButton);
        buttonPanel.add(refreshButton);

        // Main layout
        add(searchPanel, BorderLayout.NORTH);
        add(new JScrollPane(ordersTable), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        updateButtonStates();
    }

    private void updateButtonStates() {
        int viewRow = ordersTable.getSelectedRow();
        boolean isRowSelected = viewRow != -1;
        String status = null;
        if (isRowSelected) {
            int modelRow = ordersTable.convertRowIndexToModel(viewRow);
            status = String.valueOf(tableModel.getValueAt(modelRow, 3));
        }

        boolean historical = "Completed".equals(status) || "Reversed".equals(status);
        editButton.setEnabled(isRowSelected && !"Reversed".equals(status));
        deleteButton.setEnabled(isRowSelected && !"Reversed".equals(status));
        deleteButton.setText(I18n.tr("Completed".equals(status) ? "Reverse Sale" : "Delete"));
        deleteButton.setToolTipText("Completed".equals(status)
            ? I18n.tr("Reverse the completed sale while preserving FIFO history.")
            : null);

        String linkedInvoice = null;
        if (isRowSelected && "Completed".equals(status)) {
            int modelRow = ordersTable.convertRowIndexToModel(viewRow);
            int orderId = (int)tableModel.getValueAt(modelRow, 0);
            linkedInvoice = linkedInvoiceNumber(orderId);
        }
        generateInvoiceButton.setText(I18n.tr(linkedInvoice != null ? "Go to Invoice" : "Generate Invoice"));
        generateInvoiceButton.setEnabled(isRowSelected && "Completed".equals(status));
    }

    private void loadOrders() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT o.*, c.first_name || ' ' || c.last_name as customer_name,
                       i.currency_code AS payment_currency_code,
                       COALESCE(i.total,o.total_inc_vat) AS total_inc_vat
                FROM orders o
                LEFT JOIN customers c ON o.customer_id = c.id
                LEFT JOIN invoices i ON i.order_id = o.id
                ORDER BY o.order_date DESC
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getInt("id"));
                    row.add(rs.getString("customer_name"));

                    Date date = DateUtils.parseDate(rs, "order_date");
                    if (date != null) {
                        row.add(DateUtils.formatDate(date, dateFormat));
                    } else {
                        row.add("");
                    }

                    row.add(rs.getString("status"));
                    row.add(Numbers.moneyText(rs.getDouble("total_inc_vat")));

                    // Add payment status
                    String paymentStatus = rs.getString("payment_status");
                    double paidAmount = rs.getDouble("paid_amount");
                                        row.add(formatPaymentStatus(paymentStatus, paidAmount,
                        rs.getString("payment_currency_code")));

                    tableModel.addRow(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error while loading orders: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void searchOrders() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            loadOrders();
            return;
        }

        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT o.*, c.first_name || ' ' || c.last_name as customer_name,
                       i.currency_code AS payment_currency_code,
                       COALESCE(i.total,o.total_inc_vat) AS total_inc_vat
                FROM orders o
                LEFT JOIN customers c ON o.customer_id = c.id
                LEFT JOIN invoices i ON i.order_id = o.id
                WHERE c.first_name LIKE ? OR c.last_name LIKE ? OR o.status LIKE ?
                ORDER BY o.order_date DESC
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
                        row.add(rs.getString("customer_name"));

                        Date date = DateUtils.parseDate(rs, "order_date");
                        if (date != null) {
                            row.add(DateUtils.formatDate(date, dateFormat));
                        } else {
                            row.add("");
                        }

                        row.add(rs.getString("status"));
                        row.add(Numbers.moneyText(rs.getDouble("total_inc_vat")));

                        // Add payment status
                        String paymentStatus = rs.getString("payment_status");
                        double paidAmount = rs.getDouble("paid_amount");
                                                row.add(formatPaymentStatus(paymentStatus, paidAmount,
                        rs.getString("payment_currency_code")));

                        tableModel.addRow(row);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error while searching orders: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showOrderDialog(Order order) {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);

        OrderDialog dialog;
        if (parentWindow instanceof JFrame) {
            dialog = new OrderDialog((JFrame) parentWindow, order);
        } else {
            dialog = new OrderDialog((JDialog) parentWindow, order);
        }

        dialog.setVisible(true);
        if (dialog.isOrderSaved()) {
            loadOrders();
            // Refresh ProductsPanel to update stock levels
            if (parentWindow instanceof MainWindow mainWindow) {
                mainWindow.refreshProductsPanel();
                mainWindow.refreshInvoicesPanel();
            }
        }
    }

    private String linkedInvoiceNumber(int orderId) {
        try (PreparedStatement p = DatabaseManager.getInstance().getConnection().prepareStatement(
                "SELECT number FROM invoices WHERE order_id=? LIMIT 1")) {
            p.setInt(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getString(1) : null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String linkedInvoiceNumberForSelectedOrder() {
        int viewRow = ordersTable.getSelectedRow();
        if (viewRow == -1) return null;
        int modelRow = ordersTable.convertRowIndexToModel(viewRow);
        int orderId = (int)tableModel.getValueAt(modelRow, 0);
        return linkedInvoiceNumber(orderId);
    }

    private boolean openLinkedInvoiceForSelectedOrder() {
        String invoiceNumber = linkedInvoiceNumberForSelectedOrder();
        if (invoiceNumber == null || invoiceNumber.isBlank()) return false;

        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof MainWindow mainWindow) {
            mainWindow.openInvoice(invoiceNumber);
            return true;
        }
        return false;
    }

    private void editSelectedOrder() {
        int selectedRow = ordersTable.getSelectedRow();
        if (selectedRow != -1) {
            // Convert view index to model index (important when table is sorted)
            int modelRow = ordersTable.convertRowIndexToModel(selectedRow);
            int orderId = (int)tableModel.getValueAt(modelRow, 0);
            String status = String.valueOf(tableModel.getValueAt(modelRow, 3));
            if ("Reversed".equals(status)) {
                Dialogs.showMessageDialog(this,
                    I18n.tr("This sale has already been reversed and is kept as historical data."),
                    I18n.tr("Historical Sale"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                if (!"Completed".equals(status)) {
                    try (PreparedStatement p=DatabaseManager.getInstance().getConnection().prepareStatement("SELECT number FROM invoices WHERE order_id=? LIMIT 1")) {
                        p.setInt(1,orderId); try (ResultSet r=p.executeQuery()) {
                            if (r.next()) { Dialogs.showMessageDialog(this,I18n.tr("This order already has an invoice and cannot be modified.")+"\n"+I18n.tr("Invoice")+": "+r.getString(1),I18n.tr("Invoice Already Exists"),JOptionPane.WARNING_MESSAGE); return; }
                        }
                    }
                }
                Order order = loadOrderDetails(orderId);
                if (order != null) {
                    showOrderDialog(order);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                Dialogs.showMessageDialog(this,
                    "Error while loading the order: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private Order loadOrderDetails(int orderId) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        String query = """
            SELECT o.*, c.first_name || ' ' || c.last_name as customer_name
            FROM orders o
            LEFT JOIN customers c ON o.customer_id = c.id
            WHERE o.id = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, orderId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Date date = DateUtils.parseDate(rs, "order_date");
                    if (date == null) {
                        date = new Date();
                    }

                    Order order = new Order(
                        rs.getInt("id"),
                        rs.getInt("customer_id"),
                        rs.getString("customer_name"),
                        date,
                        rs.getString("status"),
                        rs.getDouble("total_ex_vat")
                    );

                    // Load payment information
                    String paymentStatus = rs.getString("payment_status");
                    double paidAmount = rs.getDouble("paid_amount");
                    order.setPaymentStatus(paymentStatus != null ? paymentStatus : "NOT_PAID");
                    order.setPaidAmount(paidAmount);

                    loadOrderItems(order);
                    return order;
                }
            }
        }
        return null;
    }

    private void loadOrderItems(Order order) throws SQLException {
        String query = """
            SELECT i.*, p.name as product_name
            FROM order_details i
            LEFT JOIN products p ON i.product_id = p.id
            WHERE i.order_id = ?
        """;

        Connection conn = DatabaseManager.getInstance().getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, order.getId());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    OrderItem item = new OrderItem(
                        rs.getInt("id"),
                        rs.getInt("order_id"),
                        rs.getInt("product_id"),
                        rs.getString("product_name"),
                        rs.getInt("quantity"),
                        rs.getDouble("sale_unit_price_ex_vat"),
                        rs.getDouble("sale_unit_price_inc_vat"),
                        rs.getDouble("vat_rate")
                    );
                    order.getItems().add(item);
                }
            }
        }
    }

    private void deleteSelectedOrder() {
        int selectedRow = ordersTable.getSelectedRow();
        if (selectedRow != -1) {
            // Convert view index to model index (important when table is sorted)
            int modelRow = ordersTable.convertRowIndexToModel(selectedRow);
            int id = (int)tableModel.getValueAt(modelRow, 0);
            String customer = (String)tableModel.getValueAt(modelRow, 1);
            String status = (String)tableModel.getValueAt(modelRow, 3);

            try {
                Connection connection = DatabaseManager.getInstance().getConnection();
                try (PreparedStatement pstmt = connection.prepareStatement(
                        "SELECT number FROM invoices WHERE order_id = ?")) {
                    pstmt.setInt(1, id);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            Dialogs.showMessageDialog(this,
                                "Order cannot be deleted because invoice " + rs.getString("number") +
                                " was generated from it. Delete the invoice first.",
                                "Order Cannot Be Deleted", JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                    }
                }
            } catch (SQLException e) {
                Dialogs.showMessageDialog(this, "Unable to verify linked invoices: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if ("Reversed".equals(status)) {
                Dialogs.showMessageDialog(this,
                    I18n.tr("This sale has already been reversed and is kept as historical data."),
                    I18n.tr("Historical Sale"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            if ("Completed".equals(status)) {
                int reverse = Dialogs.showConfirmDialog(this,
                    I18n.format("Reverse the completed sale for customer '{0}'?", customer) + "\n\n" +
                    I18n.tr("The original sale will remain in history as reversed. Stock will be returned to the exact FIFO lots originally used."),
                    I18n.tr("Reverse Completed Sale"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (reverse != JOptionPane.YES_OPTION) return;

                try {
                    Connection conn = DatabaseManager.getInstance().getConnection();
                    DatabaseManager.beginTransaction(conn);
                    try {
                        StockManager.reverseCompletedOrder(conn, id);
                        conn.commit();
                    } catch (Exception e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }
                    loadOrders();
                    Window parentWindow = SwingUtilities.getWindowAncestor(this);
                    if (parentWindow instanceof MainWindow) {
                        ((MainWindow) parentWindow).refreshProductsPanel();
                    }
                    Dialogs.showMessageDialog(this,
                        I18n.tr("Sale reversed successfully. Stock and FIFO layers were restored while the original sale was kept in history."),
                        I18n.tr("Sale Reversed"), JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Dialogs.showMessageDialog(this,
                        I18n.tr("Unable to reverse the completed sale") + ": " + I18n.tr(e.getMessage()),
                        I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
                }
                return;
            }

            int result = Dialogs.showConfirmDialog(this,
                "Are you sure you want to delete the order from customer '" + customer + "'?\n" +
                "Status: " + status + "\n" +
                ((status.equals("New") || status.equals("In Progress")) ? "Reservations will be cancelled." :
                 "No stock changes."),
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                try {
                    Connection conn = DatabaseManager.getInstance().getConnection();
                    DatabaseManager.beginTransaction(conn);

                    try {
                        // Restore stock and handle reservations based on order status
                        StockManager.deleteOrder(conn, id, status);

                        // Delete the order details
                        String deleteDetailsQuery = "DELETE FROM order_details WHERE order_id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(deleteDetailsQuery)) {
                            pstmt.setInt(1, id);
                            pstmt.executeUpdate();
                        }

                        // Delete the order
                        String deleteOrderQuery = "DELETE FROM orders WHERE id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(deleteOrderQuery)) {
                            pstmt.setInt(1, id);
                            pstmt.executeUpdate();
                        }

                        conn.commit();
                        loadOrders();

                        // Refresh ProductsPanel to update stock levels
                        Window parentWindow = SwingUtilities.getWindowAncestor(this);
                        if (parentWindow instanceof MainWindow) {
                            ((MainWindow) parentWindow).refreshProductsPanel();
                        }

                        Dialogs.showMessageDialog(this,
                            "Order deleted successfully!" +
                            ((status.equals("New") || status.equals("In Progress")) ? "\nReservations have been cancelled." : ""),
                            "Success", JOptionPane.INFORMATION_MESSAGE);

                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(true);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    Dialogs.showMessageDialog(this,
                        "Error while deleting the order: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    static boolean canGenerateInvoice(String status) {
        return "Completed".equals(status);
    }

    static void updateCompletedOrderPayment(Connection conn, int orderId,
            String paymentStatus, double requestedPaidAmount) throws SQLException {
        if (!java.util.Set.of("NOT_PAID", "PARTIALLY_PAID", "PAID").contains(paymentStatus))
            throw new SQLException("Invalid payment status");

        double total;
        try (PreparedStatement p = conn.prepareStatement("""
                SELECT COALESCE(i.total,o.total_inc_vat) AS payment_total
                FROM orders o
                LEFT JOIN invoices i ON i.order_id=o.id
                WHERE o.id=? AND o.status='Completed'
                """)) {
            p.setInt(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Completed order no longer exists");
                total = Numbers.money(java.math.BigDecimal.valueOf(r.getDouble("payment_total")));
            }
        }

        double paidAmount;
        if ("NOT_PAID".equals(paymentStatus)) {
            paidAmount = 0.0;
        } else if ("PAID".equals(paymentStatus)) {
            paidAmount = total;
        } else {
            paidAmount = Numbers.money(java.math.BigDecimal.valueOf(requestedPaidAmount));
            if (paidAmount <= 0 || paidAmount >= total)
                throw new SQLException(I18n.tr(
                    "Partial paid amount must be greater than zero and less than the total."));
        }

        try (PreparedStatement p = conn.prepareStatement(
                "UPDATE orders SET payment_status=?,paid_amount=? WHERE id=? AND status='Completed'")) {
            p.setString(1, paymentStatus);
            p.setDouble(2, paidAmount);
            p.setInt(3, orderId);
            if (p.executeUpdate() != 1)
                throw new SQLException("Completed order changed or no longer exists");
        }
    }

    static void normalizeCompletedOrderPayment(
            Connection conn, int orderId) throws SQLException {
        String currentStatus;
        double paidAmount;
        double paymentTotal;
        try (PreparedStatement p = conn.prepareStatement("""
                SELECT o.status,o.payment_status,o.paid_amount,
                       COALESCE(i.total,o.total_inc_vat) AS payment_total
                FROM orders o
                LEFT JOIN invoices i ON i.order_id=o.id
                WHERE o.id=?
                """)) {
            p.setInt(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Order no longer exists");
                if (!"Completed".equals(r.getString("status"))) return;
                currentStatus = r.getString("payment_status");
                paidAmount = Numbers.nonNegative(r.getObject("paid_amount"));
                paymentTotal = Numbers.nonNegative(r.getObject("payment_total"));
            }
        }

        String normalized = PaymentAmounts.status(paidAmount, paymentTotal);
        if (!normalized.equals(currentStatus)) {
            try (PreparedStatement p = conn.prepareStatement(
                    "UPDATE orders SET payment_status=? WHERE id=? AND status='Completed'")) {
                p.setString(1, normalized);
                p.setInt(2, orderId);
                if (p.executeUpdate() != 1)
                    throw new SQLException("Completed order changed or no longer exists");
            }
        }
    }

    static void reconcileCompletedOrderPaymentAfterInvoice(
            Connection conn, int orderId) throws SQLException {
        normalizeCompletedOrderPayment(conn, orderId);
    }

    private String formatPaymentStatus(String paymentStatus, double paidAmount,
            String paymentCurrency) {
        if (paymentStatus == null || "NOT_PAID".equals(paymentStatus)) {
            return I18n.tr("Not Paid");
        } else if ("PAID".equals(paymentStatus)) {
            return I18n.tr("Paid");
        } else if ("PARTIALLY_PAID".equals(paymentStatus)) {
            String currency = paymentCurrency == null || paymentCurrency.isBlank()
                ? SettingsPanel.getCurrencyCode() : paymentCurrency;
            return I18n.tr("Partial") + " (" +
                SettingsPanel.formatCurrency(paidAmount, currency) + ")";
        }
        return I18n.tr("Not Paid");
    }

    /**
     * Selects and highlights a specific order in the table by order ID
     * @param orderId The ID of the order to select
     */
    public void selectOrderById(int orderId) {
        // First ensure the table is loaded
        loadOrders();

        // Search for the order ID in the table model
        for (int modelRow = 0; modelRow < tableModel.getRowCount(); modelRow++) {
            Object value = tableModel.getValueAt(modelRow, 0); // ID is in column 0
            if (value != null && value instanceof Integer && (Integer) value == orderId) {
                // Found the order, convert model index to view index
                int viewRow = ordersTable.convertRowIndexToView(modelRow);
                // Select the row in the view
                ordersTable.setRowSelectionInterval(viewRow, viewRow);
                // Scroll to make the row visible
                ordersTable.scrollRectToVisible(ordersTable.getCellRect(viewRow, 0, true));
                // Update button states
                updateButtonStates();
                return;
            }
        }

        // If we get here, the order was not found
        Dialogs.showMessageDialog(this,
            "Order #" + orderId + " not found in the list.",
            "Order Not Found",
            JOptionPane.INFORMATION_MESSAGE);
    }
    public void refreshData() { loadOrders(); }
}

