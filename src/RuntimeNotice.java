/*
 * Copyright © 2026 WareStat (www.warestat.com). All rights reserved.
 * See LICENSE.txt for full terms.
 */

import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** An intentionally unadvertised in-app notice. No plaintext notice is embedded in the class file. */
final class RuntimeNotice {
    private static final int MODIFIERS = InputEvent.CTRL_DOWN_MASK
        | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
    private static final int KEY = KeyEvent.VK_Y;
    private static final String NONCE = "XTL1lzQZ71w4LegD";
    private static final String ENCRYPTED =
        "BjiFknLhdTGJ82ZQpA3xTDpqTRUtx2mhcVcC4HDVjRyKbVwIgKi0K/Bpe61r7doUPJf7pcrYXoZCVpyXLP3AXOqUO98gp51CM17ZtHPDAUZnrj+VYGRajaYdVAMQnmVCG3YVCLxX+XLSDLprFFFc04jACx6bR/l+Eb7ZVc8OTV6lcr6NHw==";

    private RuntimeNotice() { }

    /** Registers only in the active main window, including when Swing child components have focus. */
    static void install(JFrame owner) {
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        KeyEventDispatcher handler = event -> {
            if (!owner.isActive() || !matches(event)) return false;
            event.consume();
            SwingUtilities.invokeLater(() -> {
                if (owner.isDisplayable()) {
                    try {
                        JOptionPane.showMessageDialog(owner, reveal(), "WareStat", JOptionPane.INFORMATION_MESSAGE);
                    } catch (IllegalStateException error) {
                        JOptionPane.showMessageDialog(owner, "Informazioni non disponibili.",
                            "WareStat", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            return true;
        };
        manager.addKeyEventDispatcher(handler);
        owner.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                manager.removeKeyEventDispatcher(handler);
            }
        });
    }

    static boolean matches(KeyEvent event) {
        return event.getID() == KeyEvent.KEY_PRESSED
            && event.getKeyCode() == KEY
            && (event.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK
                | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK
                | InputEvent.META_DOWN_MASK)) == MODIFIERS;
    }

    /** AES-256-GCM; key parts are recombined only when the notice is requested. */
    static String reveal() {
        byte[] first = {
            (byte)0xDC, (byte)0xC7, (byte)0xCF, (byte)0x4E, (byte)0xBA, (byte)0xEB, (byte)0xC1, (byte)0x13,
            (byte)0x01, (byte)0x1D, (byte)0xD5, (byte)0x10, (byte)0xFA, (byte)0x07, (byte)0x45, (byte)0x5A,
            (byte)0x1A, (byte)0x1C, (byte)0x55, (byte)0x64, (byte)0x2D, (byte)0xD1, (byte)0x46, (byte)0x31,
            (byte)0xF7, (byte)0x34, (byte)0xF7, (byte)0xC1, (byte)0xCC, (byte)0x6F, (byte)0x14, (byte)0xF2
        };
        byte[] second = {
            (byte)0xC6, (byte)0x94, (byte)0xB6, (byte)0xC0, (byte)0x83, (byte)0x3C, (byte)0x98, (byte)0x05,
            (byte)0x84, (byte)0x3C, (byte)0x47, (byte)0x2E, (byte)0x96, (byte)0xD0, (byte)0x3E, (byte)0x79,
            (byte)0x07, (byte)0x29, (byte)0xD4, (byte)0x83, (byte)0x94, (byte)0xCE, (byte)0x0B, (byte)0xC0,
            (byte)0x3D, (byte)0xA4, (byte)0xE8, (byte)0x70, (byte)0x9D, (byte)0x9C, (byte)0x7A, (byte)0x50
        };
        byte[] key = new byte[first.length];
        byte[] clearText = null;
        try {
            for (int index = 0; index < key.length; index++) {
                key[index] = (byte) (first[index] ^ second[index]);
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, Base64.getDecoder().decode(NONCE)));
            cipher.updateAAD("WareStat:internal-notice:v1".getBytes(StandardCharsets.UTF_8));
            clearText = cipher.doFinal(Base64.getDecoder().decode(ENCRYPTED));
            return new String(clearText, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalStateException("Internal notice could not be verified.", error);
        } finally {
            Arrays.fill(first, (byte) 0);
            Arrays.fill(second, (byte) 0);
            Arrays.fill(key, (byte) 0);
            if (clearText != null) Arrays.fill(clearText, (byte) 0);
        }
    }
}
