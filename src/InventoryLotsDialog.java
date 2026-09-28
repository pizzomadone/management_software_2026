import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Read-only FIFO lot browser, optionally restricted to one product. */
public class InventoryLotsDialog extends JDialog {
    private final Integer productId;
    private final DefaultTableModel model;
    private final JTable table;
    private final JCheckBox showExhausted;
    private final SimpleDateFormat dateFormat=new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public InventoryLotsDialog(Window owner,Integer productId,String productName){
        super(owner,productId==null?I18n.tr("Inventory Lots"):
            I18n.tr("Inventory Lots")+": "+productName,ModalityType.APPLICATION_MODAL);
        this.productId=productId;
        setLayout(new BorderLayout(8,8));
        setSize(900,520);

        model=new DefaultTableModel(new String[]{
            "Date","Product","Supplier","Supplier Order","Initial Qty","Remaining Qty",
            "Unit Cost","Source","ID"
        },0){@Override public boolean isCellEditable(int r,int c){return false;}};
        table=new JTable(model);table.setAutoCreateRowSorter(true);
        TableStyleUtil.applyCompact(table);
        int idView=table.convertColumnIndexToView(8);
        table.getColumnModel().getColumn(idView).setMinWidth(0);
        table.getColumnModel().getColumn(idView).setMaxWidth(0);

        showExhausted=new JCheckBox(I18n.tr("Show exhausted lots"),true);
        showExhausted.addActionListener(e->load());
        JButton refresh=new JButton("Refresh");refresh.addActionListener(e->load());
        JLabel help=new JLabel("?");
        help.setToolTipText(I18n.tr("FIFO uses the oldest available lots first. Historical sales keep the exact cost of the lots consumed at the time of sale."));
        JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(showExhausted);top.add(refresh);top.add(help);

        add(top,BorderLayout.NORTH);add(new JScrollPane(table),BorderLayout.CENTER);
        JButton close=new JButton("Close");close.addActionListener(e->dispose());
        JPanel bottom=new JPanel();bottom.add(close);add(bottom,BorderLayout.SOUTH);
        I18n.localize(this);Dialogs.centerWindow(this);load();
    }

    private void load(){
        model.setRowCount(0);
        String sql="""
            SELECT l.id,l.received_date,p.code,p.name,s.company_name,o.number,
                   l.initial_quantity,l.remaining_quantity,l.unit_cost,l.source_type
            FROM inventory_lots l
            JOIN products p ON p.id=l.product_id
            LEFT JOIN suppliers s ON s.id=l.supplier_id
            LEFT JOIN supplier_order_details d ON d.id=l.supplier_order_detail_id
            LEFT JOIN supplier_orders o ON o.id=d.order_id
            WHERE (? IS NULL OR l.product_id=?)
              AND (?=1 OR l.remaining_quantity>0)
            ORDER BY l.received_date,l.id
            """;
        try{
            Connection c=DatabaseManager.getInstance().getConnection();
            try(PreparedStatement p=c.prepareStatement(sql)){
                if(productId==null){p.setNull(1,Types.INTEGER);p.setNull(2,Types.INTEGER);}
                else{p.setInt(1,productId);p.setInt(2,productId);}
                p.setInt(3,showExhausted.isSelected()?1:0);
                try(ResultSet r=p.executeQuery()){
                    while(r.next()){
                        Date date=DateUtils.parseDate(r,"received_date");
                        model.addRow(new Object[]{
                            date==null?"":DateUtils.formatDate(date,dateFormat),
                            r.getString("code")+" - "+r.getString("name"),
                            r.getString("company_name")==null?"":r.getString("company_name"),
                            r.getString("number")==null?"":r.getString("number"),
                            r.getInt("initial_quantity"),r.getInt("remaining_quantity"),
                            r.getDouble("unit_cost"),
                            r.getString("source_type"),r.getInt("id")
                        });
                    }
                }
            }
        }catch(SQLException e){
            Dialogs.showMessageDialog(this,I18n.tr("Error loading inventory lots")+": "+e.getMessage(),
                I18n.tr("Error"),JOptionPane.ERROR_MESSAGE);
        }
    }
}
