import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

/** Main, cross-supplier purchasing panel. */
public class SupplierOrdersPanel extends JPanel {
    private final DefaultTableModel model;
    private final JTable table;
    private final JTextField search = new JTextField(25);
    private final JButton edit = new JButton("Modify");
    private final JButton delete = new JButton("Delete");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");

    public SupplierOrdersPanel() {
        setLayout(new BorderLayout(10, 10)); setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Supplier Orders"));
        JButton searchButton = new JButton("Search"); searchButton.addActionListener(e -> loadOrders());
        search.addActionListener(e -> loadOrders());
        searchPanel.add(new JLabel("Search:")); searchPanel.add(TableInteractionUtil.withClearButton(search, this::loadOrders)); searchPanel.add(searchButton);
        model = new DefaultTableModel(new String[]{"ID", "Number", "Supplier", "Date", "Delivery Date", "Status", "Total " + SettingsPanel.getCurrencySymbol(), "Notes"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        TableSorterUtil.enableSorting(table, new int[]{3, 4}, dateFormat);
        styleSupplierOrdersTable();
        TableInteractionUtil.addDeleteKeyAction(table, this::deleteSelected);
        TableInteractionUtil.addContextMenu(table,
            new TableInteractionUtil.TableAction("New Supplier Order", this::newOrder, false),
            new TableInteractionUtil.TableAction("Modify", this::editSelected),
            new TableInteractionUtil.TableAction("Delete", this::deleteSelected), null,
            new TableInteractionUtil.TableAction("Refresh", this::loadOrders, false));
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow < 0) return;
                table.setRowSelectionInterval(viewRow, viewRow);
                editSelected();
            }
        });
        JPanel buttons = new JPanel(new FlowLayout());
        JButton add = new JButton("New Supplier Order"); JButton refresh = new JButton("Refresh");
        add.addActionListener(e -> newOrder()); edit.addActionListener(e -> editSelected()); delete.addActionListener(e -> deleteSelected()); refresh.addActionListener(e -> loadOrders());
        buttons.add(add); buttons.add(edit); buttons.add(delete); buttons.add(refresh);
        add(searchPanel, BorderLayout.NORTH); add(new JScrollPane(table), BorderLayout.CENTER); add(buttons, BorderLayout.SOUTH);
        loadOrders(); updateButtons();
    }

    private void styleSupplierOrdersTable() {
        TableStyleUtil.applyCompact(table);
        setPreferredColumnWidth(0, 42);
        setPreferredColumnWidth(1, 150);
        setPreferredColumnWidth(2, 190);
        setPreferredColumnWidth(3, 105);
        setPreferredColumnWidth(4, 120);
        setPreferredColumnWidth(5, 115);
        setPreferredColumnWidth(6, 105);
        setPreferredColumnWidth(7, 220);
    }

    private void setPreferredColumnWidth(int column, int width) {
        table.getColumnModel().getColumn(column).setPreferredWidth(width);
    }

    public void loadOrders() {
        model.setRowCount(0); String term = search.getText().trim();
        String sql = """
            SELECT so.*, s.company_name FROM supplier_orders so JOIN suppliers s ON s.id=so.supplier_id
            WHERE ?='' OR so.number LIKE ? OR s.company_name LIKE ? OR so.status LIKE ?
            ORDER BY so.order_date DESC, so.id DESC
            """;
        try (PreparedStatement ps = DatabaseManager.getInstance().getConnection().prepareStatement(sql)) {
            String pattern = "%" + term + "%"; ps.setString(1, term); ps.setString(2, pattern); ps.setString(3, pattern); ps.setString(4, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>(); row.add(rs.getInt("id")); row.add(rs.getString("number")); row.add(rs.getString("company_name"));
                    Date date = DateUtils.parseDate(rs, "order_date"); Date delivery = DateUtils.parseDate(rs, "expected_delivery_date");
                    row.add(date == null ? "" : DateUtils.formatDate(date, dateFormat)); row.add(delivery == null ? "" : DateUtils.formatDate(delivery, dateFormat));
                    row.add(rs.getString("status")); row.add(Numbers.moneyText(rs.getDouble("total"))); row.add(rs.getString("notes")); model.addRow(row);
                }
            }
        } catch (SQLException ex) { Dialogs.showMessageDialog(this, "Error loading supplier orders: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
    }

    private void newOrder() {
        MultiSupplierOrderDialog dialog = new MultiSupplierOrderDialog(SwingUtilities.getWindowAncestor(this)); dialog.setVisible(true);
        if (dialog.isSaved()) { loadOrders(); refreshProducts(); }
    }

    private void editSelected() {
        int row = selectedModelRow(); if (row < 0) return;
        int id = (int) model.getValueAt(row, 0);
        try {
            SupplierOrder order = loadOrder(id); if (order == null) return;
            Window owner = SwingUtilities.getWindowAncestor(this);
            SupplierOrderDialog dialog = new SupplierOrderDialog(
                owner, order.getSupplierId(), order.getSupplierName(), order);
            dialog.setVisible(true);
            if (dialog.isOrderSaved()) { loadOrders(); refreshProducts(); }
        } catch (SQLException ex) { Dialogs.showMessageDialog(this, "Error loading supplier order: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
    }

    private SupplierOrder loadOrder(int id) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        SupplierOrder order;
        try (PreparedStatement ps = conn.prepareStatement("SELECT so.*,s.company_name FROM supplier_orders so JOIN suppliers s ON s.id=so.supplier_id WHERE so.id=?")) {
            ps.setInt(1, id); try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) return null; order = new SupplierOrder(id, rs.getInt("supplier_id"), rs.getString("company_name"), rs.getString("number"), DateUtils.parseDate(rs,"order_date"), DateUtils.parseDate(rs,"expected_delivery_date"), rs.getString("status"), rs.getDouble("total"), rs.getString("notes")); }
        }
        try (PreparedStatement ps = conn.prepareStatement("SELECT d.*,p.name,p.code FROM supplier_order_details d JOIN products p ON p.id=d.product_id WHERE d.order_id=?")) {
            ps.setInt(1,id); try (ResultSet rs=ps.executeQuery()) { while(rs.next()) order.getItems().add(new SupplierOrderItem(rs.getInt("id"),id,rs.getInt("product_id"),rs.getString("name"),rs.getString("code"),rs.getInt("quantity"),rs.getDouble("unit_price"),rs.getDouble("total"),rs.getString("notes"))); }
        }
        return order;
    }

    private void deleteSelected() {
        int row = selectedModelRow(); if (row < 0) return; int id=(int)model.getValueAt(row,0); String number=String.valueOf(model.getValueAt(row,1));
        if (Dialogs.showConfirmDialog(this,"Are you sure you want to delete order " + number + "?","Confirm Deletion",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION) return;
        Connection conn=null; try { conn=DatabaseManager.getInstance().getConnection(); DatabaseManager.beginTransaction(conn); SupplierOrder order=loadOrder(id);
            if (order != null && "Completed".equals(order.getStatus())) {
                InventoryLotManager.reverseSupplierOrder(conn,id,number);
            }
            try(PreparedStatement ps=conn.prepareStatement("DELETE FROM supplier_order_details WHERE order_id=?")){ps.setInt(1,id);ps.executeUpdate();}
            try(PreparedStatement ps=conn.prepareStatement("DELETE FROM supplier_orders WHERE id=?")){ps.setInt(1,id);ps.executeUpdate();}
            conn.commit(); loadOrders(); refreshProducts();
        } catch(SQLException ex){if(conn!=null)try{conn.rollback();}catch(SQLException ignored){} Dialogs.showMessageDialog(this,"Error deleting supplier order: "+ex.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);} finally{if(conn!=null)try{conn.setAutoCommit(true);}catch(SQLException ignored){}}
    }
    private int selectedModelRow(){int row=table.getSelectedRow();return row<0?-1:table.convertRowIndexToModel(row);}
    private void updateButtons(){boolean selected=table.getSelectedRow()>=0;edit.setEnabled(selected);delete.setEnabled(selected);}
    private void refreshProducts(){Window owner=SwingUtilities.getWindowAncestor(this);if(owner instanceof MainWindow main)main.refreshProductsPanel();}
}
