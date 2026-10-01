import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Vector;

/**
 * Shows the products currently supplied by one supplier.
 */
public class SupplierProductsWindow extends JDialog {
    private final int supplierId;
    private final String supplierName;
    private JTable table;
    private DefaultTableModel model;
    private JButton editButton;
    private JButton deleteButton;

    public SupplierProductsWindow(JDialog parent,int supplierId,String supplierName){
        super(parent,I18n.tr("Products Supplied")+": "+supplierName,true);
        this.supplierId=supplierId;this.supplierName=supplierName;
        setup();
    }

    public SupplierProductsWindow(JFrame parent,int supplierId,String supplierName){
        super(parent,I18n.tr("Products Supplied")+": "+supplierName,true);
        this.supplierId=supplierId;this.supplierName=supplierName;
        setup();
    }

    private void setup(){
        setLayout(new BorderLayout(8,8));
        setSize(780,520);

        model=new DefaultTableModel(new String[]{
            "Product","Supplier Product Code","Purchase Price","Preferred","Notes","ID"
        },0){
            @Override public boolean isCellEditable(int r,int c){return false;}
        };
        table=new JTable(model);
        TableStyleUtil.applyCompact(table);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e->updateButtons());
        table.removeColumn(table.getColumnModel().getColumn(5));

        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add=new JButton("Add Product");
        editButton=new JButton("Edit");
        deleteButton=new JButton("Remove");
        JButton refresh=new JButton("Refresh");
        add.addActionListener(e->openEditor(null));
        editButton.addActionListener(e->editSelected());
        deleteButton.addActionListener(e->deleteSelected());
        refresh.addActionListener(e->load());
        buttons.add(add);buttons.add(editButton);buttons.add(deleteButton);buttons.add(refresh);

        JLabel help=new JLabel("?");
        help.setToolTipText(I18n.tr("Supplier prices are current reference prices. Each supplier order stores the actual price paid separately."));
        JPanel north=new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel(I18n.tr("Products Supplied")));
        north.add(help);

        add(north,BorderLayout.NORTH);
        add(new JScrollPane(table),BorderLayout.CENTER);
        add(buttons,BorderLayout.SOUTH);
        I18n.localize(this);
        Dialogs.centerWindow(this);
        load();
        updateButtons();
    }

    private void updateButtons(){
        boolean selected=table.getSelectedRow()>=0;
        editButton.setEnabled(selected);deleteButton.setEnabled(selected);
    }

    private void load(){
        model.setRowCount(0);
        try{
            Connection c=DatabaseManager.getInstance().getConnection();
            try(PreparedStatement p=c.prepareStatement("""
                    SELECT ps.id,ps.product_id,p.code,p.name,ps.supplier_product_code,
                           ps.purchase_price,ps.is_preferred,ps.notes
                    FROM product_suppliers ps
                    JOIN products p ON p.id=ps.product_id
                    WHERE ps.supplier_id=?
                    ORDER BY ps.is_preferred DESC,p.name,p.code
                    """)){
                p.setInt(1,supplierId);
                try(ResultSet r=p.executeQuery()){
                    while(r.next()){
                        Vector<Object> row=new Vector<>();
                        row.add(r.getString("code")+" - "+r.getString("name"));
                        row.add(r.getString("supplier_product_code"));
                        row.add(r.getDouble("purchase_price"));
                        row.add(r.getInt("is_preferred")==1?I18n.tr("Yes"):I18n.tr("No"));
                        row.add(r.getString("notes"));
                        row.add(r.getInt("id"));
                        model.addRow(row);
                    }
                }
            }
        }catch(SQLException e){
            Dialogs.showMessageDialog(this,I18n.tr("Error loading supplied products")+": "+e.getMessage(),
                I18n.tr("Error"),JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openEditor(SupplierProduct relation){
        SupplierProductDialog d=new SupplierProductDialog(this,supplierId,supplierName,relation);
        d.setVisible(true);
        if(d.isSaved())load();
    }

    private void editSelected(){
        int view=table.getSelectedRow();if(view<0)return;
        int row=table.convertRowIndexToModel(view);
        int id=((Number)model.getValueAt(row,5)).intValue();
        try{
            Connection c=DatabaseManager.getInstance().getConnection();
            try(PreparedStatement p=c.prepareStatement("""
                    SELECT id,supplier_id,product_id,supplier_product_code,purchase_price,is_preferred,notes
                    FROM product_suppliers WHERE id=? AND supplier_id=?
                    """)){
                p.setInt(1,id);p.setInt(2,supplierId);
                try(ResultSet r=p.executeQuery()){
                    if(r.next())openEditor(new SupplierProduct(
                        r.getInt("id"),r.getInt("supplier_id"),r.getInt("product_id"),
                        r.getString("supplier_product_code"),r.getDouble("purchase_price"),
                        r.getInt("is_preferred")==1,r.getString("notes")));
                }
            }
        }catch(SQLException e){
            Dialogs.showMessageDialog(this,I18n.tr("Error loading supplier product")+": "+e.getMessage(),
                I18n.tr("Error"),JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected(){
        int view=table.getSelectedRow();if(view<0)return;
        int row=table.convertRowIndexToModel(view);
        int id=((Number)model.getValueAt(row,5)).intValue();
        String product=String.valueOf(model.getValueAt(row,0));
        if(Dialogs.showConfirmDialog(this,
                I18n.tr("Remove this product from the supplier?")+"\n"+product,
                I18n.tr("Confirm Deletion"),JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION)return;
        try{
            Connection c=DatabaseManager.getInstance().getConnection();
            DatabaseManager.beginTransaction(c);
            try{
                boolean preferred=false;int productId=0;
                try(PreparedStatement q=c.prepareStatement(
                        "SELECT product_id,is_preferred FROM product_suppliers WHERE id=?")){
                    q.setInt(1,id);try(ResultSet r=q.executeQuery()){
                        if(!r.next())throw new SQLException("Supplier product no longer exists");
                        productId=r.getInt(1);preferred=r.getInt(2)==1;
                    }
                }
                try(PreparedStatement d=c.prepareStatement("DELETE FROM product_suppliers WHERE id=?")){
                    d.setInt(1,id);d.executeUpdate();
                }
                String newPreferredName=null;
                double newPreferredPrice=0.0;
                if (preferred) {
                    SupplierPricing.PreferredSupplier selected=SupplierPricing.preferCheapestRemaining(c,productId);
                    if (selected!=null) {
                        newPreferredName=selected.name();newPreferredPrice=selected.price();
                    }
                }
                c.commit();
                if(newPreferredName!=null){
                    Dialogs.showMessageDialog(this,
                        I18n.format("Preferred supplier changed automatically to {0}, the remaining supplier with the lowest purchase price ({1}).",
                            newPreferredName,SettingsPanel.formatUnitCurrency(newPreferredPrice)),
                        I18n.tr("Preferred Supplier Changed"),JOptionPane.INFORMATION_MESSAGE);
                } else if (preferred) {
                    Dialogs.showMessageDialog(this,I18n.tr("No alternative supplier remains. This product now has no preferred supplier."),
                        I18n.tr("Preferred Supplier Changed"),JOptionPane.INFORMATION_MESSAGE);
                }
            }catch(Exception e){c.rollback();throw e;}
            finally{c.setAutoCommit(true);}
            load();
        }catch(Exception e){
            Dialogs.showMessageDialog(this,I18n.tr("Error removing supplier product")+": "+e.getMessage(),
                I18n.tr("Error"),JOptionPane.ERROR_MESSAGE);
        }
    }
}

