import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;

/** Headless regression for single-row resizing and full toolbar access. */
public final class ResponsiveToolBarRegressionTest {
    public static void main(String[] args) {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setLayout(new ResponsiveToolBarLayout());
        List<JButton> buttons = new ArrayList<>();
        String[] titles = {
            "Home", "Discover", "Customers", "Products", "Customer Orders",
            "Supplier Orders", "Invoices", "Suppliers", "Warehouse",
            "Sales Report", "Statistics", "Warehouse Report", "Backup", "Settings"
        };
        for (int i = 0; i < titles.length; i++) {
            if (i == 2 || i == 9 || i == 12)
                toolbar.addSeparator(new Dimension(10, 55));
            JButton button = new JButton();
            button.setLayout(new BorderLayout());
            JLabel icon = new JLabel("*", SwingConstants.CENTER);
            icon.setFont(new Font("Dialog", Font.PLAIN, 24));
            JLabel title = new JLabel(titles[i], SwingConstants.CENTER);
            title.setFont(new Font("Dialog", Font.PLAIN, 13));
            button.add(icon, BorderLayout.CENTER);
            button.add(title, BorderLayout.SOUTH);
            button.putClientProperty(ResponsiveToolBarLayout.ICON_PROPERTY, icon);
            button.putClientProperty(ResponsiveToolBarLayout.CAPTION_PROPERTY, title);
            button.setToolTipText(titles[i]);
            button.setEnabled(i == 0 || i == 1 || i >= 12);
            toolbar.add(button);
            buttons.add(button);
        }

        checkLayout(toolbar, buttons, 2400, true);
        checkLayout(toolbar, buttons, 1536, false);
        checkLayout(toolbar, buttons, 1100, false);
        checkLayout(toolbar, buttons, 800, false);
        checkLayout(toolbar, buttons, 640, false);
        checkLayout(toolbar, buttons, 480, false);
        // Verify that resizing back restores complete captions.
        checkLayout(toolbar, buttons, 2400, true);
        // Longer translated button labels still fit on a single row.
        ((JLabel) buttons.get(4).getClientProperty(
            ResponsiveToolBarLayout.CAPTION_PROPERTY))
            .setText("Customer orders with a long translated caption");
        ((JLabel) buttons.get(5).getClientProperty(
            ResponsiveToolBarLayout.CAPTION_PROPERTY))
            .setText("Supplier orders with a long translated caption");
        checkLayout(toolbar, buttons, 1100, false);
        checkLayout(toolbar, buttons, 480, false);
        checkLayout(toolbar, buttons, 2400, true);
        System.out.println("PASS: Responsive toolbar always uses one row");
    }

    private static void checkLayout(JToolBar toolbar, List<JButton> buttons,
            int width, boolean expectFullCaptions) {
        toolbar.setSize(width, 60);
        toolbar.doLayout();
        int rowY = buttons.get(0).getY();
        for (JButton button : buttons) {
            check(button.getWidth() > 0 && button.getHeight() > 0,
                "Button must keep a usable target at width " + width);
            check(button.getX() >= 0 && button.getX() + button.getWidth() <= width,
                "Button is clipped at width " + width);
            check(button.getY() == rowY && button.getY() + button.getHeight() <= 60,
                "Toolbar wrapped or clipped vertically at width " + width);
            JLabel title = (JLabel) button.getClientProperty(
                ResponsiveToolBarLayout.CAPTION_PROPERTY);
            JLabel icon = (JLabel) button.getClientProperty(
                ResponsiveToolBarLayout.ICON_PROPERTY);
            check(icon.isVisible() && button.getToolTipText() != null,
                "Icon and tooltip must always be accessible");
            if (expectFullCaptions)
                check(title.isVisible() && title.getFont().getSize() == 13,
                    "Full captions must return when there is enough room");
        }
        check(!buttons.get(2).isEnabled() && buttons.get(13).isEnabled(),
            "Recovery mode must preserve disabled buttons and enabled Settings");
    }

    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
