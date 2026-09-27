import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

/** Read-only product view with a compact photo gallery and an explicit Edit action. */
public final class ProductDetailsDialog extends JDialog {
    private boolean editRequested;

    public ProductDetailsDialog(Window parent, Product product) {
        super(parent, I18n.tr("Product Details"), ModalityType.APPLICATION_MODAL);
        build(product);
    }

    private void build(Product product) {
        setLayout(new BorderLayout(10, 10));
        ((JComponent)getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel content = new ViewportWidthPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JTextArea title = wrappedText(product.getName(), true);
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 3f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        content.add(title);

        JTextArea code = wrappedText(product.getCode(), false);
        code.setAlignmentX(Component.LEFT_ALIGNMENT);
        code.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        content.add(code);
        content.add(Box.createVerticalStrut(10));

        JPanel photos = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        photos.setAlignmentX(Component.LEFT_ALIGNMENT);
        photos.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        photos.setBorder(BorderFactory.createTitledBorder("Photos"));
        try {
            List<ProductImageStore.ProductImage> images = ProductImageStore.getInstance().list(product.getId());
            if (images.isEmpty()) {
                photos.add(new JLabel("No photos"));
            } else {
                for (int i = 0; i < images.size(); i++) {
                    ProductImageStore.ProductImage image = images.get(i);
                    int startIndex = i;
                    ImageIcon icon = ProductImageStore.getInstance().icon(image.filename(), 120);
                    JButton button = new JButton(icon);
                    button.setPreferredSize(new Dimension(132, 132));
                    button.setToolTipText(image.primary() ? I18n.tr("Primary photo") : I18n.tr("Open photo"));
                    button.addActionListener(e ->
                        new ProductPhotoGalleryDialog(this, product.getName(), images, startIndex)
                            .setVisible(true));
                    photos.add(button);
                }
            }
        } catch (SQLException e) {
            photos.add(new JLabel(I18n.tr("Unable to load product photos")));
        }
        content.add(photos);
        content.add(Box.createVerticalStrut(8));

        JPanel details = new JPanel(new GridBagLayout());
        details.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 5, 3, 8);
        g.anchor = GridBagConstraints.WEST;
        g.gridy = 0;
        addLine(details, g, "Category", product.getCategory());
        addWrappedLine(details, g, "Description", product.getDescription());
        addLine(details, g, "Quantity", String.valueOf(product.getQuantity()));
        addLine(details, g, "Price", SettingsPanel.formatCurrency(product.getPrice()));
        addLine(details, g, "Last Purchase Cost", SettingsPanel.formatCurrency(product.getAcquisitionCost()));
        addLine(details, g, "Minimum Quantity", String.valueOf(product.getMinimumQuantity()));
        addLine(details, g, "Preferred Supplier", product.getSupplierName());
        addLine(details, g, "Warehouse Position", product.getWarehousePosition());
        addLine(details, g, "VAT Rate (%)", Numbers.moneyText(product.getVatRate()));
        addLine(details, g, "Active", product.isActive() ? I18n.tr("Yes") : I18n.tr("No"));
        content.add(details);

        JScrollPane scroll = new JScrollPane(content,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(content.getBackground());
        add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton suppliers = new JButton("Suppliers");
        JButton lots = new JButton("Lots");
        JButton edit = new JButton("Edit");
        JButton close = new JButton("Close");
        suppliers.addActionListener(e ->
            new ProductSuppliersDialog(this,product.getId(),product.getName()).setVisible(true));
        lots.addActionListener(e ->
            new InventoryLotsDialog(this,product.getId(),product.getName()).setVisible(true));
        edit.addActionListener(e -> { editRequested = true; dispose(); });
        close.addActionListener(e -> dispose());
        buttons.add(suppliers);
        buttons.add(lots);
        buttons.add(edit);
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);

        setSize(620, 620);
        setMinimumSize(new Dimension(520, 480));
        I18n.localize(this);
        centerOnOwner();
    }

    private void centerOnOwner() {
        Dialogs.centerWindow(this);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) centerOnOwner();
        super.setVisible(visible);
    }

    private static void addLine(JPanel panel, GridBagConstraints g, String label, String value) {
        addValueLine(panel, g, label, value, 1, 42);
    }

    private static void addWrappedLine(JPanel panel, GridBagConstraints g,
            String label, String value) {
        addValueLine(panel, g, label, value, 3, 82);
    }

    private static void addValueLine(JPanel panel, GridBagConstraints g,
            String label, String value, int rows, int maxHeight) {
        g.gridx = 0;
        g.weightx = 0;
        g.weighty = 0;
        g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.NORTHWEST;

        JLabel l = new JLabel(label + ":");
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        panel.add(l, g);

        g.gridx = 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;

        JTextArea text = wrappedText(
            value == null || value.isBlank() ? I18n.tr("Not set") : value, false);
        text.setRows(rows);
        text.setColumns(1);
        text.setMinimumSize(new Dimension(0, text.getPreferredSize().height));
        text.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxHeight));
        panel.add(text, g);

        g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.WEST;
        g.gridy++;
    }

    private static JTextArea wrappedText(String value, boolean title) {
        JTextArea area = new JTextArea(value == null ? "" : value);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFocusable(false);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setRows(title ? 2 : 1);
        area.setColumns(1);
        return area;
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    public boolean isEditRequested() { return editRequested; }
}
