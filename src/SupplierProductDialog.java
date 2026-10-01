import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class SupplierProductDialog extends JDialog {
    private final int supplierId;
    private final String supplierName;
    private final SupplierProduct relation;
    private boolean saved;

    private JComboBox<ProductDisplay> productCombo;
    private JTextField supplierCodeField;
    private JTextField priceField;
    private JCheckBox preferredCheck;
    private JTextArea notesArea;

    public SupplierProductDialog(JDialog parent,int supplierId,String supplierName,SupplierProduct relation){
        super(parent,I18n.tr(relation==null?"Add Supplied Product":"Edit Supplied Product"),true);
        this.supplierId=supplierId;this.supplierName=supplierName;this.relation=relation;
        build();
        loadProducts();
        if(relation!=null)loadRelation();
    }

    private void build(){
        setLayout(new BorderLayout(8,8));
        JPanel form=new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10,12,6,12));
        GridBagConstraints g=new GridBagConstraints();
        g.insets=new Insets(4,4,4,4);g.fill=GridBagConstraints.HORIZONTAL;

        JTextField supplier=new JTextField(supplierName);supplier.setEditable(false);
        productCombo=new JComboBox<>();
        supplierCodeField=new JTextField(22);
        priceField=new JTextField(12);
        preferredCheck=new JCheckBox(I18n.tr("Preferred supplier for this product"));
        notesArea=new JTextArea(3,22);notesArea.setLineWrap(true);notesArea.setWrapStyleWord(true);

        addRow(form,g,0,"Supplier:",supplier);
        addRow(form,g,1,"Product *:",productCombo);
        addRow(form,g,2,"Supplier Product Code (optional):",supplierCodeField);
        addRow(form,g,3,"Purchase Price *:",priceField);
        addRow(form,g,4,"Preferred:",preferredCheck);
        addRow(form,g,5,"Notes:",new JScrollPane(notesArea));

        JLabel help=new JLabel("?");
        help.setFont(help.getFont().deriveFont(Font.BOLD));
        help.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        help.setToolTipText(I18n.tr("Click for help"));
        help.addMouseListener(new java.awt.event.MouseAdapter(){
            @Override public void mouseClicked(java.awt.event.MouseEvent e){ showFieldHelp(); }
        });
        JPanel helpPanel=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        helpPanel.add(help);

        JButton save=new JButton("Save"),cancel=new JButton("Cancel");
        save.addActionListener(e->save());cancel.addActionListener(e->dispose());
        JPanel actions=new JPanel();actions.add(save);actions.add(cancel);
        JPanel footer=new JPanel(new BorderLayout());
        footer.add(Dialogs.requiredLegendPanel(),BorderLayout.WEST);
        footer.add(actions,BorderLayout.EAST);

        add(helpPanel,BorderLayout.NORTH);add(form,BorderLayout.CENTER);add(footer,BorderLayout.SOUTH);
        setSize(520,390);Dialogs.centerWindow(this);I18n.localize(this);
    }

    private void addRow(JPanel p,GridBagConstraints g,int row,String label,Component field){
        g.gridx=0;g.gridy=row;g.weightx=0;g.weighty=0;g.fill=GridBagConstraints.HORIZONTAL;
        p.add(new JLabel(label),g);
        g.gridx=1;g.weightx=1;
        if(field instanceof JScrollPane){g.fill=GridBagConstraints.BOTH;g.weighty=1;}
        p.add(field,g);
    }

    private void showFieldHelp(){
        String message=I18n.tr(
                "Supplier: the supplier associated with this product.")
            +"\n\n"+I18n.tr(
                "Product: the WareStat product supplied by this supplier.")
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

    private void loadProducts(){
        try{
            Connection c=DatabaseManager.getInstance().getConnection();
            String sql=relation==null?"""
                SELECT id,code,name FROM products
                WHERE active=1 AND NOT EXISTS(
                    SELECT 1 FROM product_suppliers ps
                    WHERE ps.product_id=products.id AND ps.supplier_id=?
                )
                ORDER BY name,code
                """:"""
                SELECT id,code,name FROM products
                WHERE id=? ORDER BY name,code
                """;
            try(PreparedStatement p=c.prepareStatement(sql)){
                p.setInt(1,relation==null?supplierId:relation.getProductId());
                try(ResultSet r=p.executeQuery()){
                    while(r.next())productCombo.addItem(new ProductDisplay(
                        r.getInt("id"),r.getString("code"),r.getString("name")));
                }
            }
        }catch(SQLException e){
            Dialogs.showMessageDialog(this,I18n.tr("Error loading products")+": "+e.getMessage(),
                I18n.tr("Error"),JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadRelation(){
        supplierCodeField.setText(relation.getSupplierProductCode()==null?"":relation.getSupplierProductCode());
        priceField.setText(Numbers.unitText(relation.getPurchasePrice()));
        preferredCheck.setSelected(relation.isPreferred());
        notesArea.setText(relation.getNotes()==null?"":relation.getNotes());
        productCombo.setEnabled(false);
    }

    private void save(){
        try{
            ProductDisplay product=(ProductDisplay)productCombo.getSelectedItem();
            if(product==null)throw new IllegalArgumentException(I18n.tr("Select a product"));
            double price=Numbers.nonNegative(priceField.getText());
            Connection c=DatabaseManager.getInstance().getConnection();
            DatabaseManager.beginTransaction(c);
            try{
                boolean wasPreferred=relation!=null && relation.isPreferred();
                boolean preferred=preferredCheck.isSelected();
                if(!preferred){
                    try(PreparedStatement p=c.prepareStatement("""
                            SELECT 1 FROM product_suppliers
                            WHERE product_id=? AND is_preferred=1 AND id<>? LIMIT 1
                            """)){
                        p.setInt(1,product.id);p.setInt(2,relation==null?-1:relation.getId());
                        try(ResultSet r=p.executeQuery()){if(!r.next())preferred=true;}
                    }
                }

                if(preferred && !wasPreferred){
                    String message=I18n.format("New preferred supplier: {0}.",supplierName)
                        +"\n"+I18n.format(
                            "The new preferred supplier's price ({0}) will become the default reference price proposed in new supplier orders.",
                            SettingsPanel.formatUnitCurrency(price))
                        +"\n\n"+I18n.tr("Confirm?");
                    if(Dialogs.showConfirmDialog(this,message,I18n.tr("Set Preferred Supplier"),
                            JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION){
                        c.rollback();
                        return;
                    }
                }

                if(relation==null){
                    try(PreparedStatement p=c.prepareStatement("""
                            INSERT INTO product_suppliers
                            (product_id,supplier_id,supplier_product_code,purchase_price,is_preferred,notes)
                            VALUES(?,?,?,?,?,?)
                            """)){
                        p.setInt(1,product.id);p.setInt(2,supplierId);
                        p.setString(3,supplierCodeField.getText().trim());
                        p.setDouble(4,price);p.setInt(5,preferred?1:0);
                        p.setString(6,notesArea.getText().trim());p.executeUpdate();
                    }
                }else{
                    try(PreparedStatement p=c.prepareStatement("""
                            UPDATE product_suppliers SET supplier_product_code=?,purchase_price=?,
                                is_preferred=?,notes=? WHERE id=? AND supplier_id=?
                            """)){
                        p.setString(1,supplierCodeField.getText().trim());p.setDouble(2,price);
                        p.setInt(3,preferred?1:0);p.setString(4,notesArea.getText().trim());
                        p.setInt(5,relation.getId());p.setInt(6,supplierId);
                        if(p.executeUpdate()!=1)throw new SQLException("Supplier product no longer exists");
                    }
                }
                c.commit();saved=true;
            }catch(Exception e){c.rollback();throw e;}
            finally{c.setAutoCommit(true);}
            dispose();
        }catch(Exception e){
            Dialogs.showMessageDialog(this,I18n.tr("Error saving supplier product")+": "+e.getMessage(),
                I18n.tr("Error"),JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isSaved(){return saved;}

    private static final class ProductDisplay{
        final int id;final String code;final String name;
        ProductDisplay(int id,String code,String name){this.id=id;this.code=code;this.name=name;}
        @Override public String toString(){return code+" - "+name;}
    }
}
