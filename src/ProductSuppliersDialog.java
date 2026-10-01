import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

/** Manage every supplier that can provide one product. */
public class ProductSuppliersDialog extends JDialog {
    private final int productId;
    private final String productName;
    private final DefaultTableModel model;
    private final JTable table;

    public ProductSuppliersDialog(Window owner,int productId,String productName){
        super(owner,I18n.tr("Product Suppliers")+": "+productName,ModalityType.APPLICATION_MODAL);
        this.productId=productId;this.productName=productName;
        setLayout(new BorderLayout(8,8));
        setSize(760,480);

        model=new DefaultTableModel(new String[]{
            "Supplier","Supplier Product Code","Purchase Price","Preferred","Notes","ID","Supplier ID"
        },0){@Override public boolean isCellEditable(int r,int c){return false;}};
        table=new JTable(model);table.setAutoCreateRowSorter(true);
        TableStyleUtil.applyCompact(table);
        hide(5);hide(6);
        table.addMouseListener(new java.awt.event.MouseAdapter(){
            @Override public void mouseClicked(java.awt.event.MouseEvent e){
                if(e.getClickCount()==2 && SwingUtilities.isLeftMouseButton(e) && table.getSelectedRow()>=0)
                    editSelected();
            }
        });

        JLabel help=new JLabel("?");
        help.setFont(help.getFont().deriveFont(Font.BOLD));
        help.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        help.setToolTipText(I18n.tr("Click for help"));
        help.addMouseListener(new java.awt.event.MouseAdapter(){
            @Override public void mouseClicked(java.awt.event.MouseEvent e){ showHelp(); }
        });
        JPanel top=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        top.add(help);

        JButton add=new JButton("Add Supplier");
        JButton edit=new JButton("Edit");
        JButton preferred=new JButton("Set Preferred");
        JButton remove=new JButton("Remove");
        JButton close=new JButton("Close");
        add.addActionListener(e->addSupplier());
        edit.addActionListener(e->editSelected());
        preferred.addActionListener(e->setPreferred());
        remove.addActionListener(e->removeSelected());
        close.addActionListener(e->dispose());
        JPanel buttons=new JPanel();
        buttons.add(add);buttons.add(edit);buttons.add(preferred);buttons.add(remove);buttons.add(close);

        add(top,BorderLayout.NORTH);add(new JScrollPane(table),BorderLayout.CENTER);add(buttons,BorderLayout.SOUTH);
        Dialogs.centerWindow(this);I18n.localize(this);load();
    }

    private void hide(int modelIndex){
        int view=table.convertColumnIndexToView(modelIndex);
        if(view>=0){
            table.getColumnModel().getColumn(view).setMinWidth(0);
            table.getColumnModel().getColumn(view).setMaxWidth(0);
            table.getColumnModel().getColumn(view).setPreferredWidth(0);
        }
    }

    private void load(){
        model.setRowCount(0);
        try{
            Connection c=DatabaseManager.getInstance().getConnection();
            try(PreparedStatement p=c.prepareStatement("""
                    SELECT ps.id,ps.supplier_id,s.company_name,ps.supplier_product_code,
                           ps.purchase_price,ps.is_preferred,ps.notes
                    FROM product_suppliers ps JOIN suppliers s ON s.id=ps.supplier_id
                    WHERE ps.product_id=?
                    ORDER BY ps.is_preferred DESC,s.company_name
                    """)){
                p.setInt(1,productId);
                try(ResultSet r=p.executeQuery()){
                    while(r.next())model.addRow(new Object[]{
                        r.getString("company_name"),r.getString("supplier_product_code"),
                        r.getDouble("purchase_price"),
                        r.getInt("is_preferred")==1?I18n.tr("Yes"):I18n.tr("No"),
                        r.getString("notes"),r.getInt("id"),r.getInt("supplier_id")
                    });
                }
            }
        }catch(SQLException e){showError(e);}
    }

    private void addSupplier(){
        SupplierSelectionDialog picker=new SupplierSelectionDialog(this);
        picker.setVisible(true);
        if(!picker.isSupplierSelected())return;
        Supplier s=picker.getSelectedSupplier();
        try{
            Connection c=DatabaseManager.getInstance().getConnection();
            try(PreparedStatement p=c.prepareStatement(
                    "SELECT 1 FROM product_suppliers WHERE product_id=? AND supplier_id=?")){
                p.setInt(1,productId);p.setInt(2,s.getId());
                try(ResultSet r=p.executeQuery()){
                    if(r.next()){
                        Dialogs.showMessageDialog(this,I18n.tr("This supplier is already associated with the product"),
                            I18n.tr("Warning"),JOptionPane.WARNING_MESSAGE);return;
                    }
                }
            }
        }catch(SQLException e){showError(e);return;}
        editRelation(null,s);
    }

    private void editSelected(){
        int view=table.getSelectedRow();if(view<0)return;
        int row=table.convertRowIndexToModel(view);
        int relationId=((Number)model.getValueAt(row,5)).intValue();
        int supplierId=((Number)model.getValueAt(row,6)).intValue();
        Supplier supplier=loadSupplier(supplierId);
        if(supplier!=null)editRelation(relationId,supplier);
    }

    private void editRelation(Integer relationId,Supplier supplier){
        JTextField code=new JTextField(18),price=new JTextField(12);
        JTextArea notes=new JTextArea(3,20);notes.setLineWrap(true);notes.setWrapStyleWord(true);
        JCheckBox preferred=new JCheckBox(I18n.tr("Preferred supplier for this product"));

        if(relationId!=null){
            try{
                Connection c=DatabaseManager.getInstance().getConnection();
                try(PreparedStatement p=c.prepareStatement(
                        "SELECT supplier_product_code,purchase_price,is_preferred,notes FROM product_suppliers WHERE id=?")){
                    p.setInt(1,relationId);try(ResultSet r=p.executeQuery()){
                        if(!r.next())return;
                        code.setText(r.getString(1));price.setText(Numbers.unitText(r.getDouble(2)));
                        preferred.setSelected(r.getInt(3)==1);notes.setText(r.getString(4));
                    }
                }
            }catch(SQLException e){showError(e);return;}
        }

        JPanel form=new JPanel(new GridLayout(0,2,6,6));
        form.add(new JLabel(I18n.tr("Supplier")));form.add(new JLabel(supplier.getCompanyName()));
        form.add(new JLabel(I18n.tr("Supplier Product Code (optional)")));form.add(code);
        form.add(new JLabel("Purchase Price *"));form.add(price);
        form.add(new JLabel(I18n.tr("Preferred")));form.add(preferred);
        form.add(new JLabel(I18n.tr("Notes")));form.add(new JScrollPane(notes));

        JLabel fieldHelp=new JLabel("?");
        fieldHelp.setFont(fieldHelp.getFont().deriveFont(Font.BOLD));
        fieldHelp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fieldHelp.setToolTipText(I18n.tr("Click for help"));
        fieldHelp.addMouseListener(new java.awt.event.MouseAdapter(){
            @Override public void mouseClicked(java.awt.event.MouseEvent e){ showRelationHelp(); }
        });
        JPanel helpBar=new JPanel(new FlowLayout(FlowLayout.RIGHT,0,0));
        helpBar.add(fieldHelp);

        JPanel content=new JPanel(new BorderLayout(0,8));
        content.add(helpBar,BorderLayout.NORTH);
        content.add(form,BorderLayout.CENTER);
        content.add(Dialogs.requiredLegendPanel(),BorderLayout.SOUTH);

        if(Dialogs.showConfirmDialog(this,content,
                I18n.tr(relationId==null?"Add Supplier":"Edit Supplier"),
                JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
        try{
            double purchasePrice=Numbers.nonNegative(price.getText());
            Connection c=DatabaseManager.getInstance().getConnection();DatabaseManager.beginTransaction(c);
            try{
                boolean wasPreferred = relationId != null && isPreferredRelation(c, relationId);
                boolean makePreferred=preferred.isSelected();
                if(!makePreferred){
                    try(PreparedStatement q=c.prepareStatement("""
                            SELECT 1 FROM product_suppliers
                            WHERE product_id=? AND is_preferred=1 AND id<>? LIMIT 1
                            """)){
                        q.setInt(1,productId);q.setInt(2,relationId==null?-1:relationId);
                        try(ResultSet r=q.executeQuery()){if(!r.next())makePreferred=true;}
                    }
                }
                if(makePreferred && !wasPreferred &&
                        !confirmPreferredChange(supplier.getCompanyName(), purchasePrice)){
                    c.rollback();
                    return;
                }
                if(relationId==null){
                    try(PreparedStatement p=c.prepareStatement("""
                            INSERT INTO product_suppliers
                            (product_id,supplier_id,supplier_product_code,purchase_price,is_preferred,notes)
                            VALUES(?,?,?,?,?,?)
                            """)){
                        p.setInt(1,productId);p.setInt(2,supplier.getId());p.setString(3,code.getText().trim());
                        p.setDouble(4,purchasePrice);p.setInt(5,makePreferred?1:0);
                        p.setString(6,notes.getText().trim());p.executeUpdate();
                    }
                }else{
                    try(PreparedStatement p=c.prepareStatement("""
                            UPDATE product_suppliers SET supplier_product_code=?,purchase_price=?,
                                is_preferred=?,notes=? WHERE id=?
                            """)){
                        p.setString(1,code.getText().trim());p.setDouble(2,purchasePrice);
                        p.setInt(3,makePreferred?1:0);p.setString(4,notes.getText().trim());
                        p.setInt(5,relationId);p.executeUpdate();
                    }
                }
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
            finally{c.setAutoCommit(true);}
            load();
        }catch(Exception e){showError(e);}
    }

    private void setPreferred(){
        int view=table.getSelectedRow();if(view<0)return;
        int row=table.convertRowIndexToModel(view);
        int id=((Number)model.getValueAt(row,5)).intValue();
        String supplierName=String.valueOf(model.getValueAt(row,0));
        double purchasePrice=((Number)model.getValueAt(row,2)).doubleValue();
        try{
            Connection c=DatabaseManager.getInstance().getConnection();
            if(isPreferredRelation(c,id))return;
            if(!confirmPreferredChange(supplierName,purchasePrice))return;
            try(PreparedStatement p=c.prepareStatement("UPDATE product_suppliers SET is_preferred=1 WHERE id=?")){
                p.setInt(1,id);p.executeUpdate();
            }
            load();
        }catch(SQLException e){showError(e);}
    }

    private boolean isPreferredRelation(Connection c,int relationId) throws SQLException{
        try(PreparedStatement p=c.prepareStatement(
                "SELECT is_preferred FROM product_suppliers WHERE id=?")){
            p.setInt(1,relationId);
            try(ResultSet r=p.executeQuery()){return r.next() && r.getInt(1)==1;}
        }
    }

    private void showRelationHelp(){
        String message=I18n.tr(
                "Supplier: the supplier associated with this product.")
            +"\n\n"+I18n.tr(
                "Supplier product code (optional): the code this supplier uses for the product in its catalog, quotes, invoices or portal. Leave it blank if the supplier does not provide one.")
            +"\n\n"+I18n.tr(
                "Purchase price: the supplier's current reference purchase price. It is proposed in new supplier orders and can be changed in the order.")
            +"\n\n"+I18n.tr(
                "Preferred: makes this the preferred supplier for the product. Its purchase price becomes the product's default purchase price; historical FIFO costs and the sale price are not changed.")
            +"\n\n"+I18n.tr(
                "Notes: optional notes about this product-supplier relationship.");
        Dialogs.showMessageDialog(this,message,I18n.tr("Supplier product fields"),
            JOptionPane.INFORMATION_MESSAGE);
    }

    private boolean confirmPreferredChange(String supplierName,double purchasePrice){
        String message=I18n.format("New preferred supplier: {0}.",supplierName)
            +"\n"+I18n.format(
                "The new preferred supplier's price ({0}) will become the default reference price proposed in new supplier orders.",
                SettingsPanel.formatUnitCurrency(purchasePrice))
            +"\n\n"+I18n.tr("Confirm?");
        return Dialogs.showConfirmDialog(this,message,I18n.tr("Set Preferred Supplier"),
            JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION;
    }

    private void showHelp(){
        String message=I18n.tr(
                "A product can have multiple suppliers, but only one preferred supplier.")
            +"\n\n"+I18n.tr(
                "The purchase price shown here is the supplier's current reference price. It is proposed automatically in new supplier orders and can still be changed in the order, for example for discounts or negotiated prices.")
            +"\n\n"+I18n.tr(
                "When you set a different preferred supplier, that supplier's purchase price becomes the default purchase price proposed for this product. The product sale price and historical FIFO costs are not changed.")
            +"\n\n"+I18n.tr(
                "Double-click a row to edit the supplier relationship.");
        Dialogs.showMessageDialog(this,message,I18n.tr("Product Suppliers Help"),
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void removeSelected(){
        int view=table.getSelectedRow();if(view<0)return;
        int row=table.convertRowIndexToModel(view);
        int id=((Number)model.getValueAt(row,5)).intValue();
        if(Dialogs.showConfirmDialog(this,I18n.tr("Remove this supplier from the product?"),
                I18n.tr("Confirm Deletion"),JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try{
            Connection c=DatabaseManager.getInstance().getConnection();DatabaseManager.beginTransaction(c);
            try{
                boolean wasPreferred=false;
                try(PreparedStatement q=c.prepareStatement("SELECT is_preferred FROM product_suppliers WHERE id=?")){
                    q.setInt(1,id);try(ResultSet r=q.executeQuery()){if(r.next())wasPreferred=r.getInt(1)==1;}
                }
                try(PreparedStatement d=c.prepareStatement("DELETE FROM product_suppliers WHERE id=?")){
                    d.setInt(1,id);d.executeUpdate();
                }
                String newPreferredName=null;
                double newPreferredPrice=0.0;
                if(wasPreferred){
                    try(PreparedStatement q=c.prepareStatement("""
                            SELECT ps.id,s.company_name,ps.purchase_price
                            FROM product_suppliers ps
                            JOIN suppliers s ON s.id=ps.supplier_id
                            WHERE ps.product_id=?
                            ORDER BY ps.purchase_price ASC,ps.id ASC
                            LIMIT 1
                            """)){
                        q.setInt(1,productId);
                        try(ResultSet r=q.executeQuery()){
                            if(r.next()){
                                int newPreferredId=r.getInt("id");
                                newPreferredName=r.getString("company_name");
                                newPreferredPrice=r.getDouble("purchase_price");
                                try(PreparedStatement p=c.prepareStatement(
                                        "UPDATE product_suppliers SET is_preferred=1 WHERE id=?")){
                                    p.setInt(1,newPreferredId);p.executeUpdate();
                                }
                            }
                        }
                    }
                }
                c.commit();
                if(newPreferredName!=null){
                    Dialogs.showMessageDialog(this,
                        I18n.format("Preferred supplier changed automatically to {0}, the remaining supplier with the lowest purchase price ({1}).",
                            newPreferredName,SettingsPanel.formatUnitCurrency(newPreferredPrice)),
                        I18n.tr("Preferred Supplier Changed"),JOptionPane.INFORMATION_MESSAGE);
                }
            }catch(Exception e){c.rollback();throw e;}
            finally{c.setAutoCommit(true);}
            load();
        }catch(Exception e){showError(e);}
    }

    private Supplier loadSupplier(int id){
        try{
            Connection c=DatabaseManager.getInstance().getConnection();
            try(PreparedStatement p=c.prepareStatement("SELECT * FROM suppliers WHERE id=?")){
                p.setInt(1,id);try(ResultSet r=p.executeQuery()){
                    if(r.next())return new Supplier(r.getInt("id"),r.getString("company_name"),
                        r.getString("vat_number"),r.getString("tax_code"),r.getString("address"),
                        r.getString("phone"),r.getString("email"),r.getString("certified_email"),
                        r.getString("website"),r.getString("notes"));
                }
            }
        }catch(SQLException e){showError(e);}
        return null;
    }

    private void showError(Exception e){
        Dialogs.showMessageDialog(this,I18n.tr("Error")+": "+e.getMessage(),
            I18n.tr("Error"),JOptionPane.ERROR_MESSAGE);
    }
}
