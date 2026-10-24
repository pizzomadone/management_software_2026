import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.ToolTipManager;

/** Localized panel tooltips must remain available above disabled icons. */
public final class ToolbarIconLabelRegressionTest {
    public static void main(String[] args) {
        JLabel caption = new JLabel("Customer Orders");
        ToolbarIconLabel icon = new ToolbarIconLabel("*", caption);
        icon.setSize(60, 40);
        MouseEvent hover = new MouseEvent(icon, MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(), 0, 20, 20, 0, false);

        check("Customer Orders".equals(icon.getToolTipText(hover)),
            "Tooltip must identify the panel rather than its description");
        check(icon.getToolTipLocation(hover).y < 0,
            "Tooltip must appear above the icon");

        icon.setEnabled(false);
        check("Customer Orders".equals(icon.getToolTipText(hover)),
            "Disabled buttons must still expose the panel name");
        boolean registered = false;
        for (java.awt.event.MouseListener listener : icon.getMouseListeners()) {
            if (listener == ToolTipManager.sharedInstance()) registered = true;
        }
        check(registered, "Tooltip manager must listen even on disabled icons");

        caption.setText("Ordini clienti");
        check("Ordini clienti".equals(icon.getToolTipText(hover)),
            "Tooltip must follow the localized panel caption");
        check("Ordini clienti".equals(icon.getToolTipText()),
            "Tooltip registration must update after localization");
        check(icon.getToolTipLocation(hover).y < 0,
            "Translated tooltip must still appear above the icon");

        System.out.println("PASS: Toolbar panel tooltip positioning and localization");
    }

    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }
}
