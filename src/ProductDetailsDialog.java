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

        JLabel title = new JLabel(product.getName());
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 3f));
        content.add(title);
        content.add(new JLabel(product.getCode()));
        content.add(Box.createVerticalStrut(10));

        JPanel photos = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        photos.setBorder(BorderFactory.createTitledBorder("Photos"));
        try {
            List<ProductImageStore.ProductImage> images = ProductImageStore.getInstance().list(product.getId());
            if (images.isEmpty()) {
                photos.add(new JLabel("No photos"));
            } else {
                for (ProductImageStore.ProductImage image : images) {
                    ImageIcon icon = ProductImageStore.getInstance().icon(image.filename(), 120);
                    JButton button = new JButton(icon);
                    button.setPreferredSize(new Dimension(132, 132));
                    button.setToolTipText(image.primary() ? I18n.tr("Primary photo") : I18n.tr("Open photo"));
                    button.addActionListener(e -> showLarge(image.filename(), product.getName()));
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
        addLine(details, g, "Description", product.getDescription());
        addLine(details, g, "Quantity", String.valueOf(product.getQuantity()));
        addLine(details, g, "Price", SettingsPanel.formatCurrency(product.getPrice()));
        addLine(details, g, "Acquisition Cost", SettingsPanel.formatCurrency(product.getAcquisitionCost()));
        addLine(details, g, "Minimum Quantity", String.valueOf(product.getMinimumQuantity()));
        addLine(details, g, "Supplier", product.getSupplierName());
        addLine(details, g, "Warehouse Position", product.getWarehousePosition());
        addLine(details, g, "VAT Rate (%)", Numbers.moneyText(product.getVatRate()));
        addLine(details, g, "Active", product.isActive() ? I18n.tr("Yes") : I18n.tr("No"));
        content.add(details);

        add(new JScrollPane(content), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton edit = new JButton("Edit");
        JButton close = new JButton("Close");
        edit.addActionListener(e -> { editRequested = true; dispose(); });
        close.addActionListener(e -> dispose());
        buttons.add(edit);
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);

        setSize(620, 620);
        setLocationRelativeTo(getOwner());
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

    private void showLarge(String filename, String title) {
        ImageIcon icon = ProductImageStore.getInstance().icon(filename, 700);
        if (icon == null) {
            Dialogs.showMessageDialog(this, I18n.tr("Unable to load product photo"),
                I18n.tr("Error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        JLabel image = new JLabel(icon);
        JScrollPane pane = new JScrollPane(image);
        pane.setPreferredSize(new Dimension(
            Math.min(760, Math.max(300, icon.getIconWidth() + 30)),
            Math.min(760, Math.max(300, icon.getIconHeight() + 30))));
        Dialogs.showMessageDialog(this, pane, title, JOptionPane.PLAIN_MESSAGE);
    }

    public boolean isEditRequested() { return editRequested; }
}
