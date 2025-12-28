// File: SupplierPriceListWindow.java
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Vector;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SupplierPriceListWindow extends JDialog {
    private int supplierId;
    private String supplierName;
    private JTable priceListTable;
    private DefaultTableModel tableModel;
    private JButton addButton;
    private JButton editButton;
    private JButton deleteButton;
    private JButton refreshButton;
    private SimpleDateFormat dateFormat;

    public SupplierPriceListWindow(JDialog parent, int supplierId, String supplierName) {
        super(parent, "Price List: " + supplierName, true);
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");

        setupWindow();
        initComponents();
        loadPriceList();
    }

    // Constructor for JFrame parent
    public SupplierPriceListWindow(JFrame parent, int supplierId, String supplierName) {
        super(parent, "Price List: " + supplierName, true);
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy");

        setupWindow();
        initComponents();
        loadPriceList();
    }

    private void setupWindow() {
        setSize(900, 600);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(10, 10));
    }

    private void initComponents() {
        // Price list table
        String[] columns = {"Product", "Supplier Code", "Price €", "Min. Qty.", "Valid From", "Valid Until", "Notes"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        priceListTable = new JTable(tableModel);
        priceListTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        priceListTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        // Buttons panel
        JPanel buttonPanel = new JPanel();
        addButton = new JButton("New Price");
        editButton = new JButton("Edit");
        deleteButton = new JButton("Delete");
        refreshButton = new JButton("Refresh");

        addButton.addActionListener(e -> showPriceDialog(null));
        editButton.addActionListener(e -> editSelectedPrice());
        deleteButton.addActionListener(e -> deleteSelectedPrice());
        refreshButton.addActionListener(e -> loadPriceList());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(refreshButton);

        // Main layout
        add(new JScrollPane(priceListTable), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean isRowSelected = priceListTable.getSelectedRow() != -1;
        editButton.setEnabled(isRowSelected);
        deleteButton.setEnabled(isRowSelected);
    }

    private void loadPriceList() {
        tableModel.setRowCount(0);
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = """
                SELECT l.*, p.name as product_name
                FROM supplier_price_lists l
                JOIN products p ON l.product_id = p.id
                WHERE l.supplier_id = ?
                ORDER BY p.name
            """;

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, supplierId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getString("product_name"));
                    row.add(rs.getString("supplier_product_code"));
                    row.add(String.format("%.2f", rs.getDouble("price")));
                    row.add(rs.getInt("minimum_quantity"));
                    row.add(dateFormat.format(rs.getDate("validity_start_date")));
                    Date endDate = rs.getDate("validity_end_date");
                    row.add(endDate != null ? dateFormat.format(endDate) : "");
                    row.add(rs.getString("notes"));
                    tableModel.addRow(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error loading price list: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showPriceDialog(SupplierPriceList priceList) {
        SupplierPriceListDialog dialog = new SupplierPriceListDialog(this, supplierId, supplierName, priceList);
        dialog.setVisible(true);
        if (dialog.isPriceSaved()) {
            loadPriceList();
        }
    }

    private void editSelectedPrice() {
        int selectedRow = priceListTable.getSelectedRow();
        if (selectedRow != -1) {
            String productName = (String)tableModel.getValueAt(selectedRow, 0);
            String supplierCode = (String)tableModel.getValueAt(selectedRow, 1);
            try {
                SupplierPriceList priceList = loadPriceListItem(productName, supplierCode);
                if (priceList != null) {
                    showPriceDialog(priceList);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error loading price: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private SupplierPriceList loadPriceListItem(String productName, String supplierCode) throws SQLException {
        Connection conn = DatabaseManager.getInstance().getConnection();
        String query = """
            SELECT l.*, p.name as product_name
            FROM supplier_price_lists l
            JOIN products p ON l.product_id = p.id
            WHERE l.supplier_id = ? AND p.name = ? AND l.supplier_product_code = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, supplierId);
            pstmt.setString(2, productName);
            pstmt.setString(3, supplierCode);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new SupplierPriceList(
                        rs.getInt("id"),
                        rs.getInt("supplier_id"),
                        rs.getInt("product_id"),
                        rs.getString("supplier_product_code"),
                        rs.getDouble("price"),
                        rs.getInt("minimum_quantity"),
                        rs.getDate("validity_start_date"),
                        rs.getDate("validity_end_date"),
                        rs.getString("notes")
                    );
                }
            }
        }
        return null;
    }

    private void deleteSelectedPrice() {
        int selectedRow = priceListTable.getSelectedRow();
        if (selectedRow != -1) {
            String productName = (String)tableModel.getValueAt(selectedRow, 0);
            String supplierCode = (String)tableModel.getValueAt(selectedRow, 1);

            int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete the price for product " + productName + "?",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                try {
                    Connection conn = DatabaseManager.getInstance().getConnection();
                    String query = """
                        DELETE FROM supplier_price_lists
                        WHERE supplier_id = ?
                        AND product_id = (SELECT id FROM products WHERE name = ?)
                        AND supplier_product_code = ?
                    """;

                    try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                        pstmt.setInt(1, supplierId);
                        pstmt.setString(2, productName);
                        pstmt.setString(3, supplierCode);
                        pstmt.executeUpdate();
                        loadPriceList();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(this,
                        "Error deleting price: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
}
