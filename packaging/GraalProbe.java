import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * External GraalVM probe. It is compiled only by the experimental workflow and
 * is never included in the normal WareStat JAR or installers.
 */
public final class GraalProbe {
    private GraalProbe() {}

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "libraries";
        Path workDir = args.length > 1 ? Path.of(args[1]) : Files.createTempDirectory("warestat-graal-probe");
        Files.createDirectories(workDir);

        runLibraries(workDir);

        if ("trace".equals(mode) || "startup".equals(mode)) {
            runStartup();
            return;
        }

        System.out.println("Graal native probe: libraries passed");
        // AWT can keep a native event thread alive even after the probe has completed.
        // Exit only after every library/resource assertion above has passed.
        System.exit(0);
    }

    private static void runLibraries(Path workDir) throws Exception {
        // Direct references keep the real application entry points reachable.
        Class<?> mainWindowClass = MainWindow.class;
        Class<?> aboutDialogClass = AboutDialog.class;
        Class<?> webPanelClass = WebPanel.class;
        Class<?> webViewClass = ca.weblite.webview.swing.WebViewComponent.class;
        if (mainWindowClass == null || aboutDialogClass == null || webPanelClass == null || webViewClass == null) {
            throw new AssertionError("Required application classes are missing");
        }

        for (String code : new String[]{"en", "it", "de", "es", "fr", "pl", "pt-BR"}) {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag(code));
            if (bundle.keySet().isEmpty()) {
                throw new AssertionError("Empty language bundle: " + code);
            }
        }

        // Exercise SQLite ServiceLoader/JNI, SQL execution and rollback.
        Path db = workDir.resolve("smoke.db");
        Files.deleteIfExists(db);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE smoke (id INTEGER PRIMARY KEY, value TEXT)");
            }
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO smoke(value) VALUES (?)")) {
                statement.setString(1, "città Łódź");
                statement.executeUpdate();
            }
            connection.rollback();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT count(*) FROM smoke")) {
                if (!result.next() || result.getInt(1) != 0) {
                    throw new AssertionError("SQLite rollback failed");
                }
            }
        }

        // Exercise PDFBox, embedded Unicode fonts and ImageIO providers.
        Path pdf = workDir.resolve("smoke.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream out = new PDPageContentStream(document, page)) {
                int y = 740;
                for (String name : new String[]{"DejaVuSans.ttf", "DejaVuSans-Bold.ttf"}) {
                    try (InputStream in = GraalProbe.class.getResourceAsStream("/fonts/" + name)) {
                        if (in == null) {
                            throw new AssertionError("Missing font " + name);
                        }
                        PDType0Font font = PDType0Font.load(document, in);
                        out.beginText();
                        out.setFont(font, 12);
                        out.newLineAtOffset(40, y);
                        out.showText("città Łódź München São Paulo français 1.234,56 €");
                        out.endText();
                        y -= 30;
                    }
                }
                BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", png)) {
                    throw new AssertionError("No PNG ImageIO writer");
                }
                out.drawImage(PDImageXObject.createFromByteArray(
                        document, png.toByteArray(), "smoke.png"), 40, 600, 8, 8);
            }
            document.save(pdf.toFile());
        }
        if (!Files.exists(pdf) || Files.size(pdf) < 1000) {
            throw new AssertionError("PDF smoke output is empty");
        }

        // Exercise application-side code that reads locale, settings and desktop properties.
        String userAgent = WebPanel.buildUserAgent();
        if (!userAgent.contains("WareStat/")) {
            throw new AssertionError("Unexpected embedded browser user-agent");
        }
    }

    private static void runStartup() throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            System.exit(1);
        });

        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(30000);
                System.err.println("Graal startup probe timed out");
                Runtime.getRuntime().halt(2);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "graal-probe-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        SwingUtilities.invokeLater(() -> {
            try {
                MainWindow window = new MainWindow();
                window.setVisible(true);

                Timer timer = new Timer(8000, event -> {
                    for (Window open : Window.getWindows()) {
                        open.dispose();
                    }
                    System.out.println("Graal startup probe: MainWindow and embedded WebView initialized");
                    System.exit(0);
                });
                timer.setRepeats(false);
                timer.start();
            } catch (Throwable error) {
                error.printStackTrace();
                System.exit(1);
            }
        });

        // Keep the launcher thread alive while the Swing event thread performs the probe.
        Thread.sleep(30000);
    }
}
