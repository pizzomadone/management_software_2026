import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.TableModelEvent;
import java.awt.*;
import java.sql.*;
import java.util.*;
import java.util.List;

/** Detail-level supplier return and immutable return history for one received order. */
public final class SupplierReturnDialog extends JDialog {
    private final SupplierOrder order;
    private final DefaultTableModel linesModel;
    private final DefaultTableModel historyModel;
    private final JTable linesTable;
    private final JTable historyTable;
    private final JTextArea notes=new JTextArea(3,45);
    private final JLabel valueLabel=new JLabel();
    private final JButton saveButton=new JButton(I18n.tr("Confirm Return"));
    private final JButton reverseButton=new JButton(I18n.tr("Reverse Return"));
    private final JTabbedPane tabs=new JTabbedPane();
    private List<SupplierReturnManager.Line> lines=List.of();
    private List<SupplierReturnManager.History> returns=List.of();
    private boolean changed;

    public SupplierReturnDialog(Window owner,SupplierOrder order,boolean showHistory) {
        super(owner,I18n.tr("Supplier Return"),ModalityType.APPLICATION_MODAL);
        this.order=order;
        setLayout(new BorderLayout(8,8));
        JPanel main=new JPanel(new BorderLayout(6,6));
        main.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        JLabel title=new JLabel(I18n.tr("Supplier")+": "+order.getSupplierName()
            +"     "+I18n.tr("Order Number:")+" "+order.getNumber());
        main.add(title,BorderLayout.NORTH);

        linesModel=new DefaultTableModel(new String[]{
                I18n.tr("Code"),I18n.tr("Product"),I18n.tr("Purchased"),
                I18n.tr("Returnable"),I18n.tr("To Return"),I18n.tr("Unit Price")},0) {
            @Override public boolean isCellEditable(int row,int column) {
                return column==4 && (int)getValueAt(row,3)>0;
            }
        };
        linesTable=new JTable(linesModel);
        TableStyleUtil.applyCompact(linesTable);
        linesModel.addTableModelListener(e->{
            if (e.getType()==TableModelEvent.UPDATE) updateValue();
        });

        JPanel newPanel=new JPanel(new BorderLayout(5,5));
        newPanel.add(new JScrollPane(linesTable),BorderLayout.CENTER);
        JPanel lower=new JPanel(new BorderLayout(4,4));
        JPanel notePanel=new JPanel(new BorderLayout(3,3));
        notePanel.add(new JLabel(I18n.tr("Notes:")),BorderLayout.NORTH);
        notes.setLineWrap(true);notes.setWrapStyleWord(true);
        notePanel.add(new JScrollPane(notes),BorderLayout.CENTER);
        lower.add(notePanel,BorderLayout.CENTER);
        notePanel.add(new JLabel(I18n.tr("Return value is based on purchase prices; no refund is recorded.")),
            BorderLayout.SOUTH);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton fill=new JButton(I18n.tr("Return Available"));
        fill.addActionListener(e->selectAvailable());
        actions.add(valueLabel);
        actions.add(fill);
        saveButton.addActionListener(e->saveReturn());
        actions.add(saveButton);
        lower.add(actions,BorderLayout.SOUTH);
        newPanel.add(lower,BorderLayout.SOUTH);
        tabs.addTab(I18n.tr("New Return"),newPanel);

        historyModel=new DefaultTableModel(new String[]{
                "ID",I18n.tr("Date"),I18n.tr("Quantity"),I18n.tr("Return Value"),
                I18n.tr("Status"),I18n.tr("Notes")},0) {
            @Override public boolean isCellEditable(int row,int col){return false;}
        };
        historyTable=new JTable(historyModel);
        TableStyleUtil.applyCompact(historyTable);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.getSelectionModel().addListSelectionListener(e->updateReverseButton());
        JPanel historyPanel=new JPanel(new BorderLayout(5,5));
        historyPanel.add(new JScrollPane(historyTable),BorderLayout.CENTER);
        JPanel historyFooter=new JPanel(new BorderLayout());
        if ("Returned".equals(order.getStatus())) {
            historyFooter.add(new JLabel(I18n.tr("Historical full return recorded before detailed return documents.")),
                BorderLayout.CENTER);
        }
        JPanel historyActions=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        reverseButton.addActionListener(e->reverseSelected());
        historyActions.add(reverseButton);
        historyFooter.add(historyActions,BorderLayout.EAST);
        historyPanel.add(historyFooter,BorderLayout.SOUTH);
        tabs.addTab(I18n.tr("Return History"),historyPanel);
        main.add(tabs,BorderLayout.CENTER);
        add(main,BorderLayout.CENTER);
        JButton close=new JButton(I18n.tr("Close"));
        close.addActionListener(e->dispose());
        JPanel bottom=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(close);add(bottom,BorderLayout.SOUTH);
        setSize(820,530);setMinimumSize(new Dimension(690,400));
        setLocationRelativeTo(owner);
        reload();
        if (showHistory || "Returned".equals(order.getStatus())) tabs.setSelectedIndex(1);
        if ("Returned".equals(order.getStatus())) tabs.setEnabledAt(0,false);
    }

    public boolean isChanged(){return changed;}

    private void reload() {
        try {
            Connection c=DatabaseManager.getInstance().getConnection();
            lines=SupplierReturnManager.lines(c,order.getId());
            linesModel.setRowCount(0);
            for (var line:lines) {
                linesModel.addRow(new Object[]{line.code(),line.product(),line.purchased(),
                    line.returnable(),0,SettingsPanel.formatCurrency(line.purchasePrice())});
            }
            returns=SupplierReturnManager.history(c,order.getId());
            historyModel.setRowCount(0);
            for (var item:returns) {
                historyModel.addRow(new Object[]{item.id(),
                    item.date()==null?"":new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(item.date()),
                    item.quantity(),SettingsPanel.formatCurrency(item.value()),
                    I18n.tr(item.reversed()?"Reversed":"Recorded"),item.notes()});
            }
            updateValue();updateReverseButton();
            saveButton.setEnabled("Completed".equals(order.getStatus())
                && lines.stream().anyMatch(l->l.returnable()>0));
        } catch(SQLException e) {
            saveButton.setEnabled(false);
            Dialogs.showMessageDialog(this,I18n.tr(e.getMessage()),I18n.tr("Error"),JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Fill at most the currently unreserved physical stock across repeated product lines. */
    private void selectAvailable() {
        Map<Integer,Integer> budget=new HashMap<>();
        try {
            Connection c=DatabaseManager.getInstance().getConnection();
            for (int i=0;i<lines.size();i++) {
                var line=lines.get(i);
                int left=budget.computeIfAbsent(line.productId(),id->{
                    try{return Math.max(0,StockManager.getAvailableStock(c,id));}
                    catch(SQLException ex){return 0;}
                });
                int amount=Math.min(line.returnable(),left);
                linesModel.setValueAt(amount,i,4);
                budget.put(line.productId(),left-amount);
            }
            updateValue();
        } catch(Exception e) {
            Dialogs.showMessageDialog(this,I18n.tr(e.getMessage()),I18n.tr("Error"),JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopEditing(){
        if(linesTable.isEditing())linesTable.getCellEditor().stopCellEditing();
    }

    private int quantityAt(int row) {
        Object value=linesModel.getValueAt(row,4);
        try {
            int q=Integer.parseInt(value.toString().trim());
            if(q<0 || q>lines.get(row).returnable())throw new NumberFormatException();
            return q;
        }catch(Exception e){
            throw new IllegalArgumentException(I18n.tr("Invalid return quantity for product")
                +" "+lines.get(row).product());
        }
    }

    private void updateValue() {
        try{
            java.math.BigDecimal value=java.math.BigDecimal.ZERO;
            for(int i=0;i<lines.size();i++){
                int qty=quantityAt(i);
                value=value.add(java.math.BigDecimal.valueOf(lines.get(i).purchasePrice())
                    .multiply(java.math.BigDecimal.valueOf(qty)));
            }
            valueLabel.setText(I18n.tr("Return Value")+": "+SettingsPanel.formatCurrency(value.doubleValue()));
        }catch(IllegalArgumentException e){
            valueLabel.setText(I18n.tr("Invalid return quantity"));
        }
    }

    private void saveReturn() {
        stopEditing();
        try {
            List<SupplierReturnManager.Request> requests=new ArrayList<>();
            for(int i=0;i<lines.size();i++){
                int quantity=quantityAt(i);
                if(quantity>0)requests.add(new SupplierReturnManager.Request(lines.get(i).detailId(),quantity));
            }
            if(requests.isEmpty())throw new IllegalArgumentException(I18n.tr("Select a quantity to return."));
            if(Dialogs.showConfirmDialog(this,I18n.tr("Confirm supplier return and remove stock from original FIFO lots?"),
                    I18n.tr("Confirm Return"),JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION)
                return;
            SupplierReturnManager.create(order.getId(),requests,notes.getText());
            changed=true;
            notes.setText("");
            reload();
            tabs.setSelectedIndex(1);
        } catch(SQLException | IllegalArgumentException e) {
            Dialogs.showMessageDialog(this,I18n.tr(e.getMessage()),I18n.tr("Error"),JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateReverseButton() {
        int row=historyTable.getSelectedRow();
        int index=row<0?-1:historyTable.convertRowIndexToModel(row);
        reverseButton.setEnabled(index>=0 && index<returns.size() && !returns.get(index).reversed());
    }

    private void reverseSelected() {
        int row=historyTable.getSelectedRow();
        if(row<0)return;
        int index=historyTable.convertRowIndexToModel(row);
        if(index<0 || index>=returns.size() || returns.get(index).reversed())return;
        if(Dialogs.showConfirmDialog(this,I18n.tr("Reverse this return and restore the original FIFO stock?"),
                I18n.tr("Reverse Return"),JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION)return;
        try {
            SupplierReturnManager.reverse(returns.get(index).id());
            changed=true;
            reload();
        } catch(SQLException e) {
            Dialogs.showMessageDialog(this,I18n.tr(e.getMessage()),I18n.tr("Error"),JOptionPane.ERROR_MESSAGE);
        }
    }
}
