import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JToolBar;

/**
 * Single-row responsive layout for WareStat's navigation toolbar.
 * Fit translated button captions at the largest readable size. If even
 * compact captions cannot fit, keep every button as an icon with its tooltip.
 * No toolbar button ever moves to another row.
 */
public final class ResponsiveToolBarLayout implements LayoutManager {
    public static final String ICON_PROPERTY = "warestat.toolbar.icon";
    public static final String CAPTION_PROPERTY = "warestat.toolbar.caption";
    private static final int HEIGHT = 60;

    private record Mode(float textSize, float iconSize, int minWidth,
                        int labelPadding, int separatorWidth, int buttonHeight) { }

    private static final Mode[] MODES = {
        new Mode(13f, 24f, 72, 12, 12, 60),
        new Mode(12f, 22f, 62, 10, 8, 57),
        new Mode(11f, 20f, 54, 8, 6, 54),
        new Mode(10f, 18f, 46, 6, 4, 50),
        new Mode(9f, 16f, 42, 4, 3, 48),
        new Mode(0f, 20f, 39, 0, 3, 46)
    };

    @Override public void addLayoutComponent(String name, Component component) { }
    @Override public void removeLayoutComponent(Component component) { }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            return new Dimension(totalWidth(parent, MODES[0]) +
                insets.left + insets.right, HEIGHT + insets.top + insets.bottom);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            return new Dimension(0, HEIGHT + insets.top + insets.bottom);
        }
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int available = Math.max(0, parent.getWidth() - insets.left - insets.right);
            Mode chosen = MODES[MODES.length - 1];
            for (Mode candidate : MODES) {
                if (totalWidth(parent, candidate) <= available) {
                    chosen = candidate;
                    break;
                }
            }

            int buttonCount = 0;
            int separators = 0;
            for (Component component : parent.getComponents()) {
                if (!component.isVisible() && !(component instanceof JButton)) continue;
                if (component instanceof JButton) buttonCount++;
                if (component instanceof JToolBar.Separator) separators++;
            }

            // Icon-only mode can shrink a little further on small windows.
            int widthForButtons = available - separators * chosen.separatorWidth();
            int compactButtonWidth = buttonCount == 0 ? 0 :
                Math.max(1, widthForButtons / buttonCount);
            int x = insets.left;
            int baseY = insets.top;
            int availableHeight = Math.max(0,
                parent.getHeight() - insets.top - insets.bottom);

            for (Component component : parent.getComponents()) {
                if (!component.isVisible() && !(component instanceof JButton)) continue;

                if (component instanceof JToolBar.Separator) {
                    int w = Math.min(chosen.separatorWidth(), Math.max(0,
                        insets.left + available - x));
                    component.setBounds(x, baseY, w, Math.min(availableHeight, HEIGHT));
                    x += w;
                    continue;
                }

                int width = itemWidth(component, chosen);
                int height = chosen.buttonHeight();
                if (component instanceof JButton button) {
                    JLabel caption = (JLabel) button.getClientProperty(CAPTION_PROPERTY);
                    JLabel icon = (JLabel) button.getClientProperty(ICON_PROPERTY);
                    if (caption != null && icon != null) {
                        boolean showCaption = chosen.textSize() > 0;
                        if (caption.isVisible() != showCaption)
                            caption.setVisible(showCaption);
                        if (showCaption) {
                            Font resized = caption.getFont().deriveFont(chosen.textSize());
                            if (!caption.getFont().equals(resized)) caption.setFont(resized);
                        }
                        Font resizedIcon = icon.getFont().deriveFont(chosen.iconSize());
                        if (!icon.getFont().equals(resizedIcon))
                            icon.setFont(resizedIcon);
                    }
                    if (chosen.textSize() == 0)
                        width = Math.min(width, compactButtonWidth);
                }
                // Guard against out-of-bounds controls if the window is resized
                // while a new layout pass is still pending.
                width = Math.min(width, Math.max(0, insets.left + available - x));
                int y = baseY + Math.max(0, (availableHeight - height) / 2);
                component.setBounds(x, y, width, Math.min(height, availableHeight));
                x += width;
            }
        }
    }

    private static int totalWidth(Container parent, Mode mode) {
        int width = 0;
        for (Component component : parent.getComponents()) {
            if (!component.isVisible() && !(component instanceof JButton)) continue;
            width += component instanceof JToolBar.Separator
                ? mode.separatorWidth() : itemWidth(component, mode);
        }
        return width;
    }

    private static int itemWidth(Component component, Mode mode) {
        if (component instanceof JButton button) {
            JLabel caption = (JLabel) button.getClientProperty(CAPTION_PROPERTY);
            if (caption != null) {
                if (mode.textSize() == 0) return mode.minWidth();
                Font font = caption.getFont().deriveFont(mode.textSize());
                int textWidth = caption.getFontMetrics(font)
                    .stringWidth(caption.getText() == null ? "" : caption.getText());
                return Math.max(mode.minWidth(), textWidth + mode.labelPadding());
            }
            return mode.textSize() == 0 ? mode.minWidth()
                : Math.max(mode.minWidth(), component.getPreferredSize().width);
        }
        return component.getPreferredSize().width;
    }
}
