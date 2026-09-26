import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

/** Centered, scrollable viewer for all photos attached to one product. */
public final class ProductPhotoGalleryDialog extends JDialog {
    private final List<ProductImageStore.ProductImage> images;
    private final String productName;
    private final JLabel imageLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel counterLabel = new JLabel("", SwingConstants.CENTER);
    private final JButton previousButton = new JButton("Previous");
    private final JButton nextButton = new JButton("Next");
    private int index;

    public ProductPhotoGalleryDialog(Window owner, String productName,
            List<ProductImageStore.ProductImage> images, int startIndex) {
        super(owner, I18n.tr("Product Photos"), ModalityType.APPLICATION_MODAL);
        this.images = List.copyOf(images);
        this.productName = productName == null ? "" : productName;
        this.index = images.isEmpty() ? 0 : Math.max(0, Math.min(startIndex, images.size() - 1));
        build();
    }

    private void build() {
        setLayout(new BorderLayout(8, 8));
        ((JComponent)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel(productName, SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2f));
        add(title, BorderLayout.NORTH);

        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);

        JPanel imageCanvas = new JPanel(new GridBagLayout());
        imageCanvas.add(imageLabel);
        JScrollPane imageScroll = new JScrollPane(imageCanvas);
        imageScroll.setBorder(BorderFactory.createEmptyBorder());
        imageScroll.getViewport().setBackground(imageCanvas.getBackground());
        add(imageScroll, BorderLayout.CENTER);

        JPanel navigation = new JPanel(new BorderLayout(8, 0));
        previousButton.addActionListener(e -> showPrevious());
        nextButton.addActionListener(e -> showNext());
        navigation.add(previousButton, BorderLayout.WEST);
        navigation.add(counterLabel, BorderLayout.CENTER);
        navigation.add(nextButton, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(navigation, BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel closePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closePanel.add(close);
        bottom.add(closePanel, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> showPrevious(),
            KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> showNext(),
            KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        setSize(820, 720);
        setMinimumSize(new Dimension(520, 460));
        updateImage();
        Dialogs.centerWindow(this);
        I18n.localize(this);
    }

    private void showPrevious() {
        if (index <= 0) return;
        index--;
        updateImage();
    }

    private void showNext() {
        if (index >= images.size() - 1) return;
        index++;
        updateImage();
    }

    private void updateImage() {
        if (images.isEmpty()) {
            imageLabel.setIcon(null);
            imageLabel.setText(I18n.tr("No photos"));
            counterLabel.setText("0/0");
            previousButton.setEnabled(false);
            nextButton.setEnabled(false);
            return;
        }

        ProductImageStore.ProductImage image = images.get(index);
        ImageIcon icon = ProductImageStore.getInstance().icon(image.filename(), 700);
        if (icon == null) {
            imageLabel.setIcon(null);
            imageLabel.setText(I18n.tr("Unable to load product photo"));
        } else {
            imageLabel.setText("");
            imageLabel.setIcon(icon);
        }
        counterLabel.setText((index + 1) + "/" + images.size());
        previousButton.setEnabled(index > 0);
        nextButton.setEnabled(index < images.size() - 1);
    }
}
