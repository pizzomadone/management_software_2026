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
        String[] columns = {"ID", "Customer", "Date", "Status", "Total " + SettingsPanel.getCurrencySymbol(), "Payment"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        ordersTable = new JTable(tableModel);
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
            new TableInteractionUtil.TableAction("Generate Invoice", this::generateInvoiceFromOrder),
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
        boolean isRowSelected = ordersTable.getSelectedRow() != -1;
        editButton.setEnabled(isRowSelected);
        deleteButton.setEnabled(isRowSelected);
        generateInvoiceButton.setEnabled(isRowSelected);
    }

    private void loadOrders() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT o.*, c.first_name || ' ' || c.last_name as customer_name
                FROM orders o
                LEFT JOIN customers c ON o.customer_id = c.id
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
                    row.add(Numbers.moneyText(rs.getDouble("total")));

                    // Add payment status
                    String paymentStatus = rs.getString("payment_status");
                    double paidAmount = rs.getDouble("paid_amount");
                    double total = rs.getDouble("total");
                    row.add(formatPaymentStatus(paymentStatus, paidAmount, total));

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
                SELECT o.*, c.first_name || ' ' || c.last_name as customer_name
                FROM orders o
                LEFT JOIN customers c ON o.customer_id = c.id
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
                        row.add(Numbers.moneyText(rs.getDouble("total")));

                        // Add payment status
                        String paymentStatus = rs.getString("payment_status");
                        double paidAmount = rs.getDouble("paid_amount");
                        double total = rs.getDouble("total");
                        row.add(formatPaymentStatus(paymentStatus, paidAmount, total));

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
            if (parentWindow instanceof MainWindow) {
                ((MainWindow) parentWindow).refreshProductsPanel();
            }
        }
    }

    private void editSelectedOrder() {
        int selectedRow = ordersTable.getSelectedRow();
        if (selectedRow != -1) {
            // Convert view index to model index (important when table is sorted)
            int modelRow = ordersTable.convertRowIndexToModel(selectedRow);
            int orderId = (int)tableModel.getValueAt(modelRow, 0);
            try {
                try (PreparedStatement p=DatabaseManager.getInstance().getConnection().prepareStatement("SELECT number FROM invoices WHERE order_id=? LIMIT 1")) {
                    p.setInt(1,orderId); try (ResultSet r=p.executeQuery()) {
                        if (r.next()) { Dialogs.showMessageDialog(this,I18n.tr("This order already has an invoice and cannot be modified.")+"\n"+I18n.tr("Invoice")+": "+r.getString(1),I18n.tr("Invoice Already Exists"),JOptionPane.WARNING_MESSAGE); return; }
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
                        rs.getDouble("total")
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
                        rs.getDouble("unit_price")
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

            int result = Dialogs.showConfirmDialog(this,
                "Are you sure you want to delete the order from customer '" + customer + "'?\n" +
                "Status: " + status + "\n" +
                (status.equals("Completed") ? "Stock will be restored." :
                 (status.equals("New") || status.equals("In Progress")) ? "Reservations will be cancelled." :
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
                            (status.equals("Completed") ? "\nStock has been restored." :
                             (status.equals("New") || status.equals("In Progress")) ? "\nReservations have been cancelled." : ""),
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

    private void generateInvoiceFromOrder() {
        int selectedRow = ordersTable.getSelectedRow();
        if (selectedRow == -1) return;

        // Convert view index to model index (important when table is sorted)
        int modelRow = ordersTable.convertRowIndexToModel(selectedRow);
        int orderId = (int)tableModel.getValueAt(modelRow, 0);
        String customer = (String)tableModel.getValueAt(modelRow, 1);
        String status = (String)tableModel.getValueAt(modelRow, 3);

        if (!canGenerateInvoice(status)) {
            Dialogs.showMessageDialog(this,
                I18n.tr("Only completed customer orders can be invoiced."),
                I18n.tr("Generate Invoice"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Connection connection = DatabaseManager.getInstance().getConnection();
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT number FROM invoices WHERE order_id = ?")) {
                check.setInt(1, orderId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        Dialogs.showMessageDialog(this,
                            "This order has already been invoiced as " + rs.getString("number") + ".",
                            "Invoice Already Exists", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }
            }
        } catch (SQLException e) {
            Dialogs.showMessageDialog(this, "Unable to verify existing invoices: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
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
                // Generate invoice number
                int year = new java.util.Calendar.Builder()
                    .setInstant(order.getOrderDate())
                    .build()
                    .get(java.util.Calendar.YEAR);
                String invoiceNumber = DatabaseManager.getInstance().getNextInvoiceNumber(conn, year);

                // Use each product's VAT rate; fall back to the configured default only
                // when no product rate is available.
                Map<Integer, Double> vatRates = new HashMap<>();
                String vatQuery = "SELECT vat_rate FROM products WHERE id = ?";
                try (PreparedStatement vatStmt = conn.prepareStatement(vatQuery)) {
                    for (OrderItem item : order.getItems()) {
                        vatStmt.setInt(1, item.getProductId());
                        try (ResultSet vatRs = vatStmt.executeQuery()) {
                            double rate = SettingsPanel.getDefaultVatRate();
                            if (vatRs.next()) {
                                double productRate = vatRs.getDouble("vat_rate");
                                if (!vatRs.wasNull()) rate = productRate;
                            }
                            vatRates.put(item.getProductId(), rate);
                        }
                    }
                }
                double taxableAmount = 0.0;
                double vatTotal = 0.0;

                for (OrderItem item : order.getItems()) {
                    double itemTaxable = Numbers.lineTotal(item.getQuantity(), item.getUnitPrice());
                    double itemVat = Numbers.tax(itemTaxable, Numbers.vat(vatRates.get(item.getProductId())));
                    taxableAmount = Numbers.add(taxableAmount,itemTaxable);
                    vatTotal = Numbers.add(vatTotal,itemVat);
                }

                double total = Numbers.add(taxableAmount,vatTotal);

                // Insert invoice
                String insertInvoice = """
                    INSERT INTO invoices (number, date, customer_id, taxable_amount, vat, total, status, order_id)
                    VALUES (?, ?, ?, ?, ?, ?, 'Draft', ?)
                """;

                int invoiceId = 0;
                try (PreparedStatement pstmt = conn.prepareStatement(insertInvoice, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setString(1, invoiceNumber);
                    pstmt.setTimestamp(2, DateUtils.toSqlTimestamp(order.getOrderDate()));
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
                    INSERT INTO invoice_details (invoice_id, product_id, quantity, unit_price, vat_rate, total)
                    VALUES (?, ?, ?, ?, ?, ?)
                """;

                try (PreparedStatement pstmt = conn.prepareStatement(insertDetails)) {
                    for (OrderItem item : order.getItems()) {
                        double itemTaxable = Numbers.lineTotal(item.getQuantity(), item.getUnitPrice());
                        double vatRate = vatRates.get(item.getProductId());

                        pstmt.setInt(1, invoiceId);
                        pstmt.setInt(2, item.getProductId());
                        pstmt.setInt(3, item.getQuantity());
                        pstmt.setDouble(4, item.getUnitPrice());
                        pstmt.setDouble(5, vatRate);
                        pstmt.setDouble(6, itemTaxable);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                InvoiceSnapshots.capture(conn,invoiceId,false);
                // Commit transaction (verify we're still in transaction mode)
                if (!conn.getAutoCommit()) {
                    conn.commit();
                }

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

    /**
     * Formats payment status for display in the table
     */
    private String formatPaymentStatus(String paymentStatus, double paidAmount, double total) {
        if (paymentStatus == null || "NOT_PAID".equals(paymentStatus)) {
            return "Not Paid";
        } else if ("PAID".equals(paymentStatus)) {
            return "Paid";
        } else if ("PARTIALLY_PAID".equals(paymentStatus)) {
            return "Partial (" + SettingsPanel.formatCurrency(paidAmount) + ")";
        }
        return "Not Paid";
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
