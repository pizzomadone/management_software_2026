import javax.swing.*;
import java.awt.*;
import java.io.File;

/** Platform-aware helpers for file and directory selection. */
public final class FileChooserUtil {
    private static final Object MAC_FILE_DIALOG_LOCK = new Object();

    private FileChooserUtil() {}

    public static File chooseDirectory(Component parent, String title, File initialDirectory) {
        File initial = validDirectory(initialDirectory);
        if (isMac()) return chooseMacDirectory(parent, title, initial);

        JFileChooser chooser = new JFileChooser(initial);
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
            ? chooser.getSelectedFile() : null;
    }

    private static File chooseMacDirectory(Component parent, String title, File initialDirectory) {
        synchronized (MAC_FILE_DIALOG_LOCK) {
            String property = "apple.awt.fileDialogForDirectories";
            String previous = System.getProperty(property);
            System.setProperty(property, "true");
            FileDialog dialog = null;
            try {
                Window owner = parent instanceof Window window
                    ? window : SwingUtilities.getWindowAncestor(parent);
                if (owner instanceof Dialog ownerDialog) {
                    dialog = new FileDialog(ownerDialog, title, FileDialog.LOAD);
                } else {
                    dialog = new FileDialog(owner instanceof Frame frame ? frame : null,
                        title, FileDialog.LOAD);
                }
                dialog.setDirectory(initialDirectory.getAbsolutePath());
                dialog.setVisible(true);
                if (dialog.getFile() == null) return null;
                return new File(dialog.getDirectory(), dialog.getFile());
            } finally {
                if (dialog != null) dialog.dispose();
                if (previous == null) System.clearProperty(property);
                else System.setProperty(property, previous);
            }
        }
    }

    private static File validDirectory(File directory) {
        if (directory != null && directory.isDirectory()) return directory;
        return new File(System.getProperty("user.home", "."));
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
            .contains("mac");
    }
}
