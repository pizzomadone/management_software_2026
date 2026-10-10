import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.sql.*;
import java.util.Vector;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;

public class OrdersPanel extends JPanel {
    private static final int INVOICE_COLUMN = 6;

    private record InvoiceLink(String number) {
        @Override public String toString() { return "🧾"; }
    }

    private JTable ordersTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JButton addButton;
    private JButton editButton;
    private JButton deleteButton;
    private JButton generateInvoiceButton;
    private JButton generateProformaButton;
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
        String[] columns = {"ID", "Customer", "Date", "Status", "Total incl. VAT", "Payment", "Invoice"};
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

        DefaultTableCellRenderer invoiceRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean selected, boolean focus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, selected, focus, row, column);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                if (value instanceof InvoiceLink link) {
                    label.setText(link.toString());
                    label.setToolTipText(I18n.tr("Go to Invoice") + ": " + link.number());
                } else {
                    label.setText("");
                    label.setToolTipText(null);
                }
                return label;
            }
        };
        ordersTable.getColumnModel().getColumn(INVOICE_COLUMN).setCellRenderer(invoiceRenderer);
        ordersTable.getColumnModel().getColumn(INVOICE_COLUMN).setPreferredWidth(70);
        ordersTable.getColumnModel().getColumn(INVOICE_COLUMN).setMaxWidth(95);

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
            new TableInteractionUtil.TableAction("Generate Proforma PDF", this::generateProformaFromOrder),
            null, // Separator
            new TableInteractionUtil.TableAction("Refresh", this::loadOrders, false)
        );

        // Add mouse listener for double click
        ordersTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (!SwingUtilities.isLeftMouseButton(evt)) return;

                int viewRow = ordersTable.rowAtPoint(evt.getPoint());
                int viewColumn = ordersTable.columnAtPoint(evt.getPoint());
                if (viewRow < 0 || viewColumn < 0) return;

                int modelColumn = ordersTable.convertColumnIndexToModel(viewColumn);
                if (modelColumn == INVOICE_COLUMN) {
                    int modelRow = ordersTable.convertRowIndexToModel(viewRow);
                    Object value = tableModel.getValueAt(modelRow, INVOICE_COLUMN);
                    if (value instanceof InvoiceLink link) {
                        ordersTable.setRowSelectionInterval(viewRow, viewRow);
                        Window window = SwingUtilities.getWindowAncestor(OrdersPanel.this);
                        if (window instanceof MainWindow mainWindow)
                            mainWindow.openInvoice(link.number());
                    }
                    return;
                }

                if (evt.getClickCount() == 2) {
                    ordersTable.setRowSelectionInterval(viewRow, viewRow);
                    editSelectedOrder();
                }
            }
        });

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        addButton = new JButton("New Customer Order");
        editButton = new JButton("Modify");
        deleteButton = new JButton("Delete");
        generateInvoiceButton = new JButton("Generate Invoice");
        generateProformaButton = new JButton("Generate Proforma PDF");
        refreshButton = new JButton("Refresh");

        generateInvoiceButton.setFont(generateInvoiceButton.getFont().deriveFont(Font.BOLD));
        generateInvoiceButton.setPreferredSize(new Dimension(150, 30));
        generateInvoiceButton.setOpaque(true);
        generateProformaButton.setPreferredSize(new Dimension(180, 30));

        addButton.addActionListener(e -> showOrderDialog(null));
        editButton.addActionListener(e -> editSelectedOrder());
        deleteButton.addActionListener(e -> deleteSelectedOrder());
        generateInvoiceButton.addActionListener(e -> generateInvoiceFromOrder());
        generateProformaButton.addActionListener(e -> generateProformaFromOrder());
        refreshButton.addActionListener(e -> loadOrders());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(generateProformaButton);
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
        if (isRowSelected) {
            int modelRow = ordersTable.convertRowIndexToModel(viewRow);
            int orderId = (int)tableModel.getValueAt(modelRow, 0);
            linkedInvoice = linkedInvoiceNumber(orderId);
        }
        generateInvoiceButton.setText(I18n.tr(linkedInvoice != null ? "Go to Invoice" : "Generate Invoice"));
        generateInvoiceButton.setEnabled(isRowSelected
            && (linkedInvoice != null || "Completed".equals(status)));
        generateProformaButton.setEnabled(isRowSelected
            && linkedInvoice == null
            && canGenerateProforma(status));
    }

    private void loadOrders() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT o.*, c.first_name || ' ' || c.last_name as customer_name,
                       i.currency_code AS payment_currency_code,
                       i.number AS invoice_number,
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

                    String invoiceNumber = rs.getString("invoice_number");
                    row.add(invoiceNumber == null || invoiceNumber.isBlank()
                        ? null : new InvoiceLink(invoiceNumber));

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
                       i.number AS invoice_number,
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

                        String invoiceNumber = rs.getString("invoice_number");
                        row.add(invoiceNumber == null || invoiceNumber.isBlank()
                            ? null : new InvoiceLink(invoiceNumber));

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

            // Select the saved row for both newly created and edited orders.
            // Use the real generated ID (not table position: the table is sortable).
            Integer savedId = dialog.getSavedOrderId();
            if (savedId != null) {
                selectOrderByIdInCurrentTable(savedId);
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
                        "SELECT number, status FROM invoices WHERE order_id = ?")) {
                    pstmt.setInt(1, id);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            String invoiceNumber = rs.getString("number");
                            String invoiceStatus = rs.getString("status");
                            if (linkedInvoiceBlocksOrderAction(status, invoiceStatus)) {
                                String instruction = "Draft".equals(invoiceStatus)
                                    ? I18n.tr("Delete the draft invoice first.")
                                    : "Canceled".equals(invoiceStatus)
                                        ? I18n.tr("Only a completed sale can be reversed while linked to a canceled invoice.")
                                        : I18n.tr("Cancel the issued invoice first.");
                                String message = "Completed".equals(status)
                                    ? I18n.format("Order cannot be reversed because invoice {0} is linked to it. {1}",
                                        invoiceNumber, instruction)
                                    : I18n.format("Order cannot be deleted because invoice {0} is linked to it. {1}",
                                        invoiceNumber, instruction);
                                Dialogs.showMessageDialog(this,
                                    message,
                                    I18n.tr("Order Cannot Be Changed"), JOptionPane.WARNING_MESSAGE);
                                return;
                            }
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

    static boolean linkedInvoiceBlocksOrderAction(String orderStatus, String invoiceStatus) {
        return !("Completed".equals(orderStatus) && "Canceled".equals(invoiceStatus));
    }

    static boolean canGenerateInvoice(String status) {
        return "Completed".equals(status);
    }

    static boolean canGenerateProforma(String status) {
        return "Reservation".equals(status)
            || "New".equals(status)
            || "In Progress".equals(status);
    }

    static void updateOrderPayment(Connection conn, int orderId,
            String paymentStatus, double requestedPaidAmount) throws SQLException {
        if (!java.util.Set.of("NOT_PAID", "PARTIALLY_PAID", "PAID").contains(paymentStatus))
            throw new SQLException("Invalid payment status");

        double total;
        boolean allowed;
        try (PreparedStatement p = conn.prepareStatement("""
                SELECT o.status,
                       EXISTS(SELECT 1 FROM invoices i WHERE i.order_id=o.id) AS has_invoice,
                       COALESCE((SELECT i.total FROM invoices i WHERE i.order_id=o.id LIMIT 1),
                                o.total_inc_vat) AS payment_total
                FROM orders o
                WHERE o.id=?
                """)) {
            p.setInt(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Order no longer exists");
                allowed = "Completed".equals(r.getString("status")) || r.getInt("has_invoice") == 1;
                if (!allowed)
                    throw new SQLException("Payment-only editing requires a completed order or a linked invoice");
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
                "UPDATE orders SET payment_status=?,paid_amount=? WHERE id=?")) {
            p.setString(1, paymentStatus);
            p.setDouble(2, paidAmount);
            p.setInt(3, orderId);
            if (p.executeUpdate() != 1)
                throw new SQLException("Order changed or no longer exists");
        }
    }

    static void updateCompletedOrderPayment(Connection conn, int orderId,
            String paymentStatus, double requestedPaidAmount) throws SQLException {
        try (PreparedStatement p = conn.prepareStatement(
                "SELECT 1 FROM orders WHERE id=? AND status='Completed'")) {
            p.setInt(1, orderId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Completed order no longer exists");
            }
        }
        updateOrderPayment(conn, orderId, paymentStatus, requestedPaidAmount);
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

    record GeneratedInvoice(int id, String number) {}

    // Called inside the caller's transaction so invoice, snapshots and payment state commit together.
    static GeneratedInvoice createInvoiceFromOrder(Connection conn, Order order) throws SQLException {
        if (conn.getAutoCommit()) throw new SQLException("Invoice creation requires a transaction");
        if (!canGenerateInvoice(order.getStatus()) || order.getItems().isEmpty())
            throw new SQLException("Only completed nonempty orders can be invoiced");
        int orderId = order.getId();
        // A draft has its own proposed document date, independent of the old order.
        // The actual date can still be changed when the draft is issued.
        java.util.Date draftInvoiceDate = new java.util.Date();
        int year = new java.util.Calendar.Builder()
            .setInstant(draftInvoiceDate)
            .build()
            .get(java.util.Calendar.YEAR);
        String invoiceNumber = DatabaseManager.getInstance().getNextInvoiceNumber(conn, year);

        // Customer orders snapshot both VAT rate and gross/net sale prices,
        // so invoicing never depends on later product changes.
        double taxableAmount = 0.0;
        double vatTotal = 0.0;
        double total = 0.0;

        for (OrderItem item : order.getItems()) {
            double itemTaxable = item.getTotalExVat();
            double itemGross = item.getTotalIncVat();
            double itemVat = Numbers.money(java.math.BigDecimal.valueOf(itemGross)
                .subtract(java.math.BigDecimal.valueOf(itemTaxable)));
            taxableAmount = Numbers.add(taxableAmount,itemTaxable);
            vatTotal = Numbers.add(vatTotal,itemVat);
            total = Numbers.add(total,itemGross);
        }



        // Insert invoice
        String insertInvoice = """
            INSERT INTO invoices (number, date, customer_id, taxable_amount, vat, total, status, order_id)
            VALUES (?, ?, ?, ?, ?, ?, 'Draft', ?)
        """;

        int invoiceId = 0;
        try (PreparedStatement pstmt = conn.prepareStatement(insertInvoice, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, invoiceNumber);
            pstmt.setTimestamp(2, DateUtils.toSqlTimestamp(draftInvoiceDate));
            pstmt.setInt(3, order.getCustomerId());
            pstmt.setDouble(4, taxableAmount);
            pstmt.setDouble(5, vatTotal);
            pstmt.setDouble(6, total);
            pstmt.setInt(7, orderId);
            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                invoiceId = rs.getInt(1);
            }
        }

        // Insert invoice details
        String insertDetails = """
            INSERT INTO invoice_details
                (invoice_id, product_id, quantity, sale_unit_price_ex_vat,
                 sale_unit_price_inc_vat, vat_rate, total)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(insertDetails)) {
            for (OrderItem item : order.getItems()) {
                double itemTaxable = item.getTotalExVat();

                pstmt.setInt(1, invoiceId);
                pstmt.setInt(2, item.getProductId());
                pstmt.setInt(3, item.getQuantity());
                pstmt.setDouble(4, item.getUnitPriceExVat());
                pstmt.setDouble(5, item.getUnitPriceIncVat());
                pstmt.setDouble(6, item.getVatRate());
                pstmt.setDouble(7, itemTaxable);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }

        reconcileCompletedOrderPaymentAfterInvoice(conn, orderId);
        InvoiceSnapshots.capture(conn,invoiceId,false);
        return new GeneratedInvoice(invoiceId, invoiceNumber);
    }

    private void generateProformaFromOrder() {
        int selectedRow = ordersTable.getSelectedRow();
        if (selectedRow == -1) return;

        int modelRow = ordersTable.convertRowIndexToModel(selectedRow);
        int orderId = (int)tableModel.getValueAt(modelRow, 0);
        String status = String.valueOf(tableModel.getValueAt(modelRow, 3));

        if (linkedInvoiceNumber(orderId) != null || !canGenerateProforma(status)) {
            Dialogs.showMessageDialog(this,
                I18n.tr("Proforma can only be generated for Reservation, New or In Progress customer orders."),
                I18n.tr("Generate Proforma PDF"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        ProformaPDFGenerator.generateAndSave(orderId, this);
    }

    private void generateInvoiceFromOrder() {
        int selectedRow = ordersTable.getSelectedRow();
        if (selectedRow == -1) return;

        // Convert view index to model index (important when table is sorted)
        int modelRow = ordersTable.convertRowIndexToModel(selectedRow);
        int orderId = (int)tableModel.getValueAt(modelRow, 0);
        String customer = (String)tableModel.getValueAt(modelRow, 1);
        String status = (String)tableModel.getValueAt(modelRow, 3);

        String existingInvoice = linkedInvoiceNumber(orderId);
        if (existingInvoice != null) {
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window instanceof MainWindow mainWindow) {
                mainWindow.openInvoice(existingInvoice);
            }
            return;
        }

        if (!canGenerateInvoice(status)) {
            Dialogs.showMessageDialog(this,
                I18n.tr("Only completed customer orders can be invoiced."),
                I18n.tr("Generate Invoice"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Confirm with user
        int result = Dialogs.showConfirmDialog(this,
            "Generate invoice for order from customer: " + customer + "?\n" +
            "Order status: " + status,
            "Confirm Invoice Generation",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);

        if (result != JOptionPane.YES_OPTION) return;

        try {
            Order order = loadOrderDetails(orderId);
            if (order == null) {
                Dialogs.showMessageDialog(this,
                    "Error loading order details",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (order.getItems().isEmpty()) {
                Dialogs.showMessageDialog(this,
                    "Cannot generate invoice: order has no items",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Connection conn = DatabaseManager.getInstance().getConnection();
            DatabaseManager.beginTransaction(conn);

            try {
                GeneratedInvoice generated = createInvoiceFromOrder(conn, order);
                int invoiceId = generated.id();
                String invoiceNumber = generated.number();
                // Commit transaction (verify we're still in transaction mode)
                if (!conn.getAutoCommit()) {
                    conn.commit();
                }

                // Show the invoice indicator immediately without waiting for a manual refresh.
                loadOrders();
                selectOrderByIdInCurrentTable(orderId);

                Object[] invoiceOptions = { I18n.tr("Go to Invoice"), I18n.tr("Close") };
                int invoiceAction = Dialogs.showOptionDialog(this,
                    I18n.format("Invoice {0} generated successfully!", invoiceNumber) + "\n" +
                    I18n.tr("You can find it in the Invoices panel."),
                    I18n.tr("Invoice Generated"), JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE, null, invoiceOptions, invoiceOptions[0]);

                Window invoiceParentWindow = SwingUtilities.getWindowAncestor(this);
                if (invoiceParentWindow instanceof MainWindow mainWindow) {
                    mainWindow.refreshInvoicesPanel();
                    if (invoiceAction == 0) mainWindow.openInvoice(invoiceNumber);
                }

            } catch (Exception e) {
                try {
                    if (!conn.getAutoCommit()) {
                        conn.rollback();
                    }
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException finallyEx) {
                    finallyEx.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            Dialogs.showMessageDialog(this,
                "Error generating invoice: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
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

    private boolean selectOrderByIdInCurrentTable(int orderId) {
        for (int modelRow = 0; modelRow < tableModel.getRowCount(); modelRow++) {
            Object value = tableModel.getValueAt(modelRow, 0);
            if (value instanceof Integer && ((Integer) value) == orderId) {
                int viewRow = ordersTable.convertRowIndexToView(modelRow);
                if (viewRow < 0) return false;
                ordersTable.setRowSelectionInterval(viewRow, viewRow);
                ordersTable.scrollRectToVisible(ordersTable.getCellRect(viewRow, 0, true));
                updateButtonStates();
                return true;
            }
        }
        return false;
    }

    /**
     * Selects and highlights a specific order in the table by order ID
     * @param orderId The ID of the order to select
     */
    public void selectOrderById(int orderId) {
        loadOrders();

        if (selectOrderByIdInCurrentTable(orderId)) return;

        Dialogs.showMessageDialog(this,
            "Order #" + orderId + " not found in the list.",
            "Order Not Found",
            JOptionPane.INFORMATION_MESSAGE);
    }
    public void refreshData() { loadOrders(); }
}

