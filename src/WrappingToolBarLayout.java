import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import javax.swing.JToolBar;

/**
 * Wraps complete toolbar buttons into additional rows when the window is too
 * narrow. Labels and actions remain visible: nothing is clipped or hidden.
 */
public final class WrappingToolBarLayout implements LayoutManager {
    private static final int ROW_GAP = 4;

    @Override public void addLayoutComponent(String name, Component comp) { }
    @Override public void removeLayoutComponent(Component comp) { }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            int width = parent.getParent() != null
                ? parent.getParent().getWidth() : parent.getWidth();
            // During initial pack the window has not been sized yet.
            if (width <= 0) width = 1200;
            return arrange(parent, width, false);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Dimension preferred = preferredLayoutSize(parent);
            return new Dimension(0, preferred.height);
        }
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            arrange(parent, parent.getWidth(), true);
        }
    }

    /**
     * Use exactly the same row-break logic for measurement and placement.
     * Separators never occupy the beginning of a wrapped row.
     */
    private static Dimension arrange(Container parent, int outerWidth, boolean place) {
        Insets insets = parent.getInsets();
        int left = insets.left;
        int top = insets.top;
        int available = Math.max(1, outerWidth - insets.left - insets.right);
        int x = 0;
        int y = top;
        int rowHeight = 0;
        int widestRow = 0;
        boolean any = false;
        Component[] children = parent.getComponents();

        for (int i = 0; i < children.length; i++) {
            Component component = children[i];
            if (!component.isVisible()) continue;
            Dimension size = component.getPreferredSize();
            boolean separator = component instanceof JToolBar.Separator;

            // Keep a group separator with the first button of its group.
            if (separator) {
                if (x == 0) {
                    if (place) component.setBounds(0, 0, 0, 0);
                    continue;
                }
                int upcoming = 0;
                for (int next = i + 1; next < children.length; next++) {
                    if (!children[next].isVisible()) continue;
                    if (!(children[next] instanceof JToolBar.Separator)) {
                        upcoming = children[next].getPreferredSize().width;
                        break;
                    }
                }
                if (x + size.width + upcoming > available) {
                    widestRow = Math.max(widestRow, x);
                    y += rowHeight + ROW_GAP;
                    x = 0;
                    rowHeight = 0;
                    if (place) component.setBounds(0, 0, 0, 0);
                    continue;
                }
            }

            if (x > 0 && x + size.width > available) {
                widestRow = Math.max(widestRow, x);
                y += rowHeight + ROW_GAP;
                x = 0;
                rowHeight = 0;
            }

            if (place) component.setBounds(left + x, y, size.width, size.height);
            x += size.width;
            rowHeight = Math.max(rowHeight, size.height);
            any = true;
        }

        widestRow = Math.max(widestRow, x);
        return new Dimension(widestRow + insets.left + insets.right,
            y + (any ? rowHeight : 0) + insets.bottom);
    }
}
