import javax.swing.JButton;
import javax.swing.JToolBar;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/** Headless checks for the full-width toolbar at common window sizes. */
public final class WrappingToolBarRegressionTest {
    public static void main(String[] args) {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setLayout(new WrappingToolBarLayout());
        List<JButton> buttons = new ArrayList<>();
        int[] widths = {80, 90, 110, 95, 155, 175, 95, 105, 105,
                        125, 105, 165, 90, 95};
        for (int i = 0; i < widths.length; i++) {
            if (i == 2 || i == 9 || i == 12) toolbar.addSeparator(new Dimension(12, 55));
            JButton button = new JButton("Section " + i);
            button.setPreferredSize(new Dimension(widths[i], 60));
            buttons.add(button);
            toolbar.add(button);
        }

        // Screen size in the reported macOS screenshot: all 14 sections must fit.
        checkLayout(toolbar, buttons, 1536, true);
        checkLayout(toolbar, buttons, 1100, true);
        checkLayout(toolbar, buttons, 800, true);
        // Restoring a larger window collapses the additional rows.
        checkLayout(toolbar, buttons, 2400, false);
        System.out.println("PASS: Toolbar sections remain inside window at different widths");
    }

    private static void checkLayout(JToolBar toolbar, List<JButton> buttons,
            int width, boolean expectWrapped) {
        toolbar.setSize(width, 1000);
        Dimension preferred = toolbar.getPreferredSize();
        toolbar.setSize(width, preferred.height);
        toolbar.doLayout();
        boolean wrapped = false;
        int firstY = buttons.get(0).getY();
        for (JButton button : buttons) {
            check(button.getWidth() > 0 && button.getHeight() > 0,
                "Toolbar button must remain visible");
            check(button.getX() >= 0 && button.getX() + button.getWidth() <= width,
                "Toolbar button clips outside window at width " + width);
            check(button.getY() >= 0
                    && button.getY() + button.getHeight() <= toolbar.getHeight(),
                "Toolbar button clips vertically at width " + width);
            if (button.getY() > firstY) wrapped = true;
        }
        check(wrapped == expectWrapped,
            "Incorrect number of toolbar rows at width " + width);
    }

    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
