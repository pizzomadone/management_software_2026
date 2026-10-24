import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.JToolTip;
import javax.swing.SwingConstants;

/**
 * Identifies an icon-only navigation button by its current, localized panel
 * caption. The standard Swing tooltip delay provides hover-to-discover.
 * The icon label handles tooltips even when its parent button is disabled.
 */
public final class ToolbarIconLabel extends JLabel {
    private final JLabel caption;

    public ToolbarIconLabel(String icon, JLabel caption) {
        super(icon, SwingConstants.CENTER);
        this.caption = caption;
        setToolTipText(caption.getText());
        caption.addPropertyChangeListener("text", event ->
            setToolTipText(caption.getText()));
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        return caption.getText();
    }

    @Override
    public Point getToolTipLocation(MouseEvent event) {
        String text = getToolTipText(event);
        if (text == null || text.isBlank()) return null;
        JToolTip tip = createToolTip();
        tip.setTipText(text);
        Dimension size = tip.getPreferredSize();
        return new Point(Math.max(0, (getWidth() - size.width) / 2),
            -size.height - 6);
    }
}
