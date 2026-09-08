import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/** Creates one or more supplier orders by grouping selected products by supplier. */
public class MultiSupplierOrderDialog extends JDialog {
    private final DefaultTableModel model;
    private final JTable table;
    private final DatePickerField orderDate;
    private final DatePickerField deliveryDate;
    private final JComboBox<String> status;
    private final JTextArea notes;
    private final JLabel totalLabel;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
    private boolean saved;
    private boolean updating;

    public MultiSupplierOrderDialog(Window owner) {
        super(owner, "New Supplier Order", ModalityType.APPLICATION_MODAL);
        setSize(900, 620);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel header = new JPanel(new GridBagLayout());
        header.setBorder(BorderFactory.createTitledBorder("Order Information"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; header.add(new JLabel("Order Date:"), gbc);
        orderDate = new DatePickerField(DateUtils.formatDate(new java.util.Date(), dateFormat));
        gbc.gridx = 1; header.add(orderDate, gbc);
        gbc.gridx = 2; header.add(new JLabel("Delivery Date:"), gbc);
        deliveryDate = new DatePickerField(10, true);
        gbc.gridx = 3; header.add(deliveryDate, gbc);
        gbc.gridx = 0; gbc.gridy = 1; header.add(new JLabel("Status:"), gbc);
        status = new JComboBox<>(new String[]{"Draft", "Confirmed", "In Transit", "Completed", "Cancelled"});
        gbc.gridx = 1; header.add(status, gbc);
        gbc.gridx = 2; header.add(new JLabel("Notes:"), gbc);
        notes = new JTextArea(2, 28);
        notes.setLineWrap(true); notes.setWrapStyleWord(true);
        gbc.gridx = 3; header.add(new JScrollPane(notes), gbc);

        model = new DefaultTableModel(new String[]{"Product ID", "Code", "Product", "Supplier ID", "Supplier", "Quantity", "Unit Price", "Total", "Notes"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return column == 5 || column == 6 || column == 8; }
        };
        table = new JTable(model);
        hideColumn(0); hideColumn(3);
        model.addTableModelListener(e -> {
            if (!updating && (e.getColumn() == 5 || e.getColumn() == 6)) SwingUtilities.invokeLater(this::updateTotals);
        });

        JPanel itemButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add Product");
        JButton remove = new JButton("Remove Product");
        add.addActionListener(e -> selectProduct());
        remove.addActionListener(e -> removeProduct());
        itemButtons.add(add); itemButtons.add(remove);
        totalLabel = new JLabel("Total: " + SettingsPanel.formatCurrency(0));
        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 14f));
        JPanel total = new JPanel(new FlowLayout(FlowLayout.RIGHT)); total.add(totalLabel);
        JPanel center = new JPanel(new BorderLayout());
        center.add(itemButtons, BorderLayout.NORTH);
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(total, BorderLayout.SOUTH);

        JButton review = new JButton("Review and Confirm");
        JButton cancel = new JButton("Cancel");
        review.addActionListener(e -> reviewAndSave());
        cancel.addActionListener(e -> dispose());
        JPanel actions = new JPanel(); actions.add(review); actions.add(cancel);
        root.add(header, BorderLayout.NORTH); root.add(center, BorderLayout.CENTER); root.add(actions, BorderLayout.SOUTH);
        setContentPane(root);
        setLocationRelativeTo(owner);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) setLocationRelativeTo(getOwner());
        super.setVisible(visible);
    }

    private void hideColumn(int modelIndex) {
        int view = table.convertColumnIndexToView(modelIndex);
        table.getColumnModel().getColumn(view).setMinWidth(0);
        table.getColumnModel().getColumn(view).setMaxWidth(0);
        table.getColumnModel().getColumn(view).setPreferredWidth(0);
    }

    private void selectProduct() {
        JDialog picker = new JDialog(this, "Select Product", true);
        DefaultTableModel products = new DefaultTableModel(new String[]{"ID", "Code", "Product", "Supplier ID", "Supplier", "Purchase Price", "Stock"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable productTable = new JTable(products);
        JTextField search = new JTextField(25);
        Runnable load = () -> loadProducts(products, search.getText().trim());
        JButton searchButton = new JButton("Search"); searchButton.addActionListener(e -> load.run());
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Search:")); searchPanel.add(search); searchPanel.add(searchButton);
        JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 999999, 1));
        JButton choose = new JButton("Add");
        choose.addActionListener(e -> {
            int row = productTable.getSelectedRow();
            if (row < 0) { showWarning(picker, "Please select a product"); return; }
            row = productTable.convertRowIndexToModel(row);
            int id = (int) products.getValueAt(row, 0);
            for (int i = 0; i < model.getRowCount(); i++) {
                if ((int) model.getValueAt(i, 0) == id) { showWarning(picker, "This product is already in the order"); return; }
            }
            double price = ((Number) products.getValueAt(row, 5)).doubleValue();
            int qty = (int) quantity.getValue();
            model.addRow(new Object[]{id, products.getValueAt(row, 1), products.getValueAt(row, 2),
                products.getValueAt(row, 3), products.getValueAt(row, 4), qty,
                price, String.format("%.2f", Numbers.lineTotal(qty,price)), ""});
            updateTotals(); picker.dispose();
        });
        productTable.setAutoCreateRowSorter(true);
        productTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) choose.doClick(); }
        });
        JPanel bottom = new JPanel(); bottom.add(new JLabel("Quantity:")); bottom.add(quantity); bottom.add(choose);
        picker.add(searchPanel, BorderLayout.NORTH); picker.add(new JScrollPane(productTable), BorderLayout.CENTER); picker.add(bottom, BorderLayout.SOUTH);
        picker.setSize(760, 480); picker.setLocationRelativeTo(this); load.run(); picker.setVisible(true);
    }

    private void loadProducts(DefaultTableModel products, String term) {
        products.setRowCount(0);
        String query = """
            SELECT p.id, p.code, p.name, p.supplier_id, s.company_name, p.quantity,
                   p.acquisition_cost purchase_price
            FROM products p LEFT JOIN suppliers s ON s.id=p.supplier_id
            WHERE p.active=1 AND (?='' OR p.code LIKE ? OR p.name LIKE ? OR s.company_name LIKE ?)
            ORDER BY p.name
            """;
        try (PreparedStatement ps = DatabaseManager.getInstance().getConnection().prepareStatement(query)) {
            String pattern = "%" + term + "%";
            ps.setString(1, term); ps.setString(2, pattern); ps.setString(3, pattern); ps.setString(4, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) products.addRow(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3),
                    rs.getObject(4), rs.getString(5) == null ? "Not assigned" : rs.getString(5), SupplierPricing.price(DatabaseManager.getInstance().getConnection(),rs.getInt(4),rs.getInt(1),1,new java.util.Date()), rs.getInt(6)});
            }
        } catch (SQLException ex) { showError(this, "Error loading products: " + ex.getMessage()); }
    }

    private void removeProduct() {
        int row = table.getSelectedRow();
        if (row >= 0) { model.removeRow(table.convertRowIndexToModel(row)); updateTotals(); }
    }

    private void updateTotals() {
        updating = true;
        double total = 0;
        for (int i = 0; i < model.getRowCount(); i++) {
            int quantity = parseInt(model.getValueAt(i, 5));
            double price = parseDouble(model.getValueAt(i, 6));
            double line = Numbers.lineTotal(quantity, price);
            model.setValueAt(String.format("%.2f", line), i, 7); total = Numbers.add(total,line);
        }
        totalLabel.setText("Total: " + SettingsPanel.formatCurrency(total));
        updating = false;
    }

    private void reviewAndSave() {
        try {
            Numbers.commitEdits(table);
            for (int row=0;row<model.getRowCount();row++) {
                Numbers.positiveQuantity(model.getValueAt(row,5)); Numbers.nonNegative(model.getValueAt(row,6));
            }
            updateTotals();
        } catch (IllegalArgumentException e) { showWarning(this,e.getMessage()); return; }
        if (model.getRowCount() == 0) { showWarning(this, "Add at least one product to the order"); return; }
        java.util.Date date;
        java.util.Date expected = null;
        try {
            date = DateUtils.parseDate(orderDate.getText(), dateFormat);
            if (!deliveryDate.getText().trim().isEmpty()) expected = DateUtils.parseDate(deliveryDate.getText(), dateFormat);
            if (date == null) throw new IllegalArgumentException();
        } catch (Exception ex) { showWarning(this, "Invalid date format. Use dd/MM/yyyy format"); return; }

        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            Object supplier = model.getValueAt(i, 3);
            if (!(supplier instanceof Number) || parseInt(model.getValueAt(i, 5)) <= 0 || parseDouble(model.getValueAt(i, 6)) <= 0) {
                showWarning(this, "Every product must have a supplier, a positive quantity and a positive purchase price.\nCheck: " + model.getValueAt(i, 2));
                return;
            }
            groups.computeIfAbsent(((Number) supplier).intValue(), key -> new ArrayList<>()).add(i);
        }
        StringBuilder summary = new StringBuilder("The selection will create " + groups.size() + " separate supplier order(s):\n\n");
        double grandTotal = 0;
        for (List<Integer> rows : groups.values()) {
            int first = rows.get(0); double subtotal = 0;
            summary.append(model.getValueAt(first, 4)).append(" — ").append(rows.size()).append(" product(s)\n");
            for (int row : rows) subtotal = Numbers.add(subtotal,parseDouble(model.getValueAt(row, 7)));
            summary.append("Subtotal: ").append(SettingsPanel.formatCurrency(subtotal)).append("\n\n"); grandTotal = Numbers.add(grandTotal,subtotal);
        }
        summary.append("Grand total: ").append(SettingsPanel.formatCurrency(grandTotal));
        JTextArea area = new JTextArea(summary.toString(), 12, 48); area.setEditable(false); area.setOpaque(false);
        int answer = JOptionPane.showConfirmDialog(this, new JScrollPane(area), "Confirm Supplier Order Split", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        saveGroups(groups, date, expected);
    }

    private void saveGroups(Map<Integer, List<Integer>> groups, java.util.Date date, java.util.Date expected) {
        Connection conn = null;
        try {
            conn = DatabaseManager.getInstance().getConnection(); conn.setAutoCommit(false);
            String prefix = "OF" + new java.text.SimpleDateFormat("yyyy").format(date);
            int sequence = nextSequence(conn, prefix);
            for (Map.Entry<Integer, List<Integer>> group : groups.entrySet()) {
                String number = DocumentNumbers.nextSupplierNumber(conn,date);
                double total = 0;
                for (int row : group.getValue()) total=Numbers.add(total,Numbers.lineTotal(parseInt(model.getValueAt(row,5)),parseDouble(model.getValueAt(row,6))));
                int orderId;
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO supplier_orders (supplier_id,number,order_date,expected_delivery_date,status,total,notes) VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, group.getKey()); ps.setString(2, number); ps.setTimestamp(3, DateUtils.toSqlTimestamp(date));
                    ps.setTimestamp(4, expected == null ? null : DateUtils.toSqlTimestamp(expected)); ps.setString(5, (String) status.getSelectedItem());
                    ps.setDouble(6, total); ps.setString(7, notes.getText().trim()); ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Unable to create supplier order"); orderId = keys.getInt(1); }
                }
                List<StockManager.StockItem> stock = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO supplier_order_details (order_id,product_id,quantity,unit_price,total,notes) VALUES (?,?,?,?,?,?)")) {
                    for (int row : group.getValue()) {
                        int productId = (int) model.getValueAt(row, 0); int qty = parseInt(model.getValueAt(row, 5));
                        ps.setInt(1, orderId); ps.setInt(2, productId); ps.setInt(3, qty); ps.setDouble(4, parseDouble(model.getValueAt(row, 6)));
                        ps.setDouble(5, Numbers.lineTotal(qty,parseDouble(model.getValueAt(row,6)))); ps.setString(6, String.valueOf(model.getValueAt(row, 8))); ps.addBatch();
                        stock.add(new StockManager.StockItem(productId, String.valueOf(model.getValueAt(row, 2)), qty));
                    }
                    ps.executeBatch();
                }
                if ("Completed".equals(status.getSelectedItem())) StockManager.incrementStock(conn, stock, date, number, "SUPPLIER_ORDER");
            }
            conn.commit(); saved = true; dispose();
        } catch (Exception ex) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            showError(this, "No orders were created. Error saving supplier orders: " + ex.getMessage());
        } finally { if (conn != null) try { conn.setAutoCommit(true); } catch (SQLException ignored) {} }
    }

    private int nextSequence(Connection conn, String prefix) throws SQLException {
        return DocumentNumbers.nextSupplierSequence(conn,prefix);
    }

    private int parseInt(Object value) { return Numbers.positiveQuantity(value); }
    private double parseDouble(Object value) { return Numbers.nonNegative(value); }
    private void showWarning(Component parent, String message) { JOptionPane.showMessageDialog(parent, message, "Warning", JOptionPane.WARNING_MESSAGE); }
    private void showError(Component parent, String message) { JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE); }
    public boolean isSaved() { return saved; }
}
