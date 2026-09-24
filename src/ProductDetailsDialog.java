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

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JTextArea title = wrappedText(product.getName(), true);
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 3f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setMaximumSize(new Dimension(560, 90));
        content.add(title);

        JLabel code = new JLabel(product.getCode());
        code.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(code);
        content.add(Box.createVerticalStrut(10));

        JPanel photos = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
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
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 5, 3, 12);
        g.anchor = GridBagConstraints.WEST;
        g.gridy = 0;
        addLine(details, g, "Category", product.getCategory());
        addWrappedLine(details, g, "Description", product.getDescription());
        addLine(details, g, "Quantity", String.valueOf(product.getQuantity()));
        addLine(details, g, "Price", SettingsPanel.formatCurrency(product.getPrice()));
        addLine(details, g, "Acquisition Cost", SettingsPanel.formatCurrency(product.getAcquisitionCost()));
        addLine(details, g, "Minimum Quantity", String.valueOf(product.getMinimumQuantity()));
        addLine(details, g, "Supplier", product.getSupplierName());
        addLine(details, g, "Warehouse Position", product.getWarehousePosition());
        addLine(details, g, "VAT Rate (%)", Numbers.moneyText(product.getVatRate()));
        addLine(details, g, "Active", product.isActive() ? I18n.tr("Yes") : I18n.tr("No"));
        content.add(details);

        JScrollPane scroll = new JScrollPane(content,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton edit = new JButton("Edit");
        JButton close = new JButton("Close");
        edit.addActionListener(e -> { editRequested = true; dispose(); });
        close.addActionListener(e -> dispose());
        buttons.add(edit);
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);

        setSize(620, 620);
        I18n.localize(this);
        centerOnOwner();
    }

    private void centerOnOwner() {
        Window owner = getOwner();
        setLocationRelativeTo(owner != null && owner.isShowing() ? owner : null);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) centerOnOwner();
        super.setVisible(visible);
    }

    private static void addLine(JPanel panel, GridBagConstraints g, String label, String value) {
        g.gridx = 0; g.weightx = 0;
        JLabel l = new JLabel(label + ":");
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        panel.add(l, g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel(value == null || value.isBlank() ? I18n.tr("Not set") : value), g);
        g.fill = GridBagConstraints.NONE;
        g.gridy++;
    }

    private static void addWrappedLine(JPanel panel, GridBagConstraints g,
            String label, String value) {
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
        text.setColumns(34);
        text.setRows(3);
        text.setMaximumSize(new Dimension(440, 90));
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
        area.setColumns(title ? 38 : 34);
        return area;
    }

    public boolean isEditRequested() { return editRequested; }
}
