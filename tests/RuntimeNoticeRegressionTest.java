import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.swing.JPanel;

/** Tests decryption integrity and exact, unusual key combination without opening any UI. */
public class RuntimeNoticeRegressionTest {
    private static void assertPass(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        System.out.println("PASS: " + label);
    }

    public static void main(String[] args) throws Exception {
        String value = RuntimeNotice.reveal();
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));
        assertPass("5a46163869384b37525bdb90455d5d1201708c3da4530f4eac1e9a99763ee79b"
            .equals(digest), "encrypted text decrypts to expected verified content");
        assertPass(value.length() > 40, "nonempty text decoded");
        JPanel source = new JPanel();
        long timestamp = System.currentTimeMillis();
        int masks = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
        KeyEvent full = new KeyEvent(source, KeyEvent.KEY_PRESSED, timestamp, masks,
            KeyEvent.VK_Y, 'Y');
        assertPass(RuntimeNotice.matches(full), "only chosen combination is recognized");
        assertPass(!RuntimeNotice.matches(new KeyEvent(source, KeyEvent.KEY_RELEASED, timestamp,
            masks, KeyEvent.VK_Y, 'Y')), "key release does not retrigger");
        assertPass(!RuntimeNotice.matches(new KeyEvent(source, KeyEvent.KEY_PRESSED, timestamp,
            InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK, KeyEvent.VK_Y, 'Y')),
            "modifier omission is rejected");
        assertPass(!RuntimeNotice.matches(new KeyEvent(source, KeyEvent.KEY_PRESSED, timestamp,
            masks, KeyEvent.VK_U, 'U')), "different key is rejected");
        assertPass(!RuntimeNotice.matches(new KeyEvent(source, KeyEvent.KEY_PRESSED, timestamp,
            masks | InputEvent.META_DOWN_MASK, KeyEvent.VK_Y, 'Y')),
            "additional Command or Meta modifier is rejected");
    }
}
