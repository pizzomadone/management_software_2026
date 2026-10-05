import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** External probe: never added to the production JAR or installer. */
public final class RuntimeSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[1].equals("startup")) {
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> { error.printStackTrace(); System.exit(1); });
            Class.forName("MainWindow").getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            long deadline = System.currentTimeMillis() + 20000;
            while (System.currentTimeMillis() < deadline) {
                final boolean[] shown = {false};
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    for (java.awt.Window window : java.awt.Window.getWindows()) {
                        if (window.isShowing() && window instanceof javax.swing.JDialog dialog
                                && "WareStat - Language".equals(dialog.getTitle())) shown[0] = true;
                    }
                });
                if (shown[0]) { System.out.println("Protected MainWindow opened the first-run language dialog"); System.exit(0); }
                Thread.sleep(100);
            }
            throw new AssertionError("First-run language dialog did not appear");
        }
        Class.forName("MainWindow", false, RuntimeSmoke.class.getClassLoader())
            .getMethod("main", String[].class);
        Class.forName("AboutDialog", false, RuntimeSmoke.class.getClassLoader());
        Class.forName("ca.weblite.webview.swing.WebViewComponent", false, RuntimeSmoke.class.getClassLoader());
        for (String code : new String[]{"en", "it", "de", "es", "fr", "pl", "pt-BR"}) {
            if (ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag(code)).keySet().isEmpty())
                throw new AssertionError("Empty language bundle: " + code);
        }
        // Exercise ServiceLoader, JNI_OnLoad, statement execution and rollback.
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + args[0] + "/smoke.db")) {
            try (Statement s = c.createStatement()) { s.execute("CREATE TABLE smoke (id INTEGER PRIMARY KEY, value TEXT)"); }
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement("INSERT INTO smoke(value) VALUES (?)")) {
                p.setString(1, "città Łódź"); p.executeUpdate();
            }
            c.rollback();
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM smoke")) {
                if (!r.next() || r.getInt(1) != 0) throw new AssertionError("SQLite rollback failed");
            }
        }
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(); doc.addPage(page);
            try (PDPageContentStream out = new PDPageContentStream(doc, page)) {
                int y = 740;
                for (String name : new String[]{"DejaVuSans.ttf", "DejaVuSans-Bold.ttf"}) {
                    try (InputStream in = RuntimeSmoke.class.getResourceAsStream("/fonts/" + name)) {
                        if (in == null) throw new AssertionError("Missing font " + name);
                        var font = PDType0Font.load(doc, in);
                        out.beginText(); out.setFont(font, 12); out.newLineAtOffset(40, y);
                        out.showText("città Łódź München São Paulo français 1.234,56 €"); out.endText();
                        y -= 30;
                    }
                }
                BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
                ByteArrayOutputStream png = new ByteArrayOutputStream(); ImageIO.write(image, "png", png);
                out.drawImage(PDImageXObject.createFromByteArray(doc, png.toByteArray(), "smoke.png"), 40, 600, 8, 8);
            }
            doc.save(new File(args[0], "smoke.pdf"));
        }
        if (Files.size(Path.of(args[0], "smoke.pdf")) < 1000) throw new AssertionError("Empty PDF");
        System.out.println("Packaged runtime: main, SQLite, seven languages, Unicode fonts and PDF image passed");
    }
}
