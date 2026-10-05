import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import ca.weblite.webview.swing.WebViewComponent;

/**
 * External GraalVM probe. It is compiled only by the experimental workflow and
 * is never included in the normal WareStat JAR or installers.
 */
public final class GraalProbe {
    private static final String WEBVIEW_OK = "WARESTAT_WEBVIEW_OK";

    private GraalProbe() {}

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "libraries";
        Path workDir = args.length > 1 ? Path.of(args[1]) : Files.createTempDirectory("warestat-graal-probe");
        Files.createDirectories(workDir);

        runLibraries(workDir);

        switch (mode) {
            case "libraries":
                System.out.println("Graal native probe: libraries passed");
                System.exit(0);
                return;
            case "webview":
                runWebViewProbe();
                return;
            case "trace":
            case "startup":
                runActualMainWindowStartup();
                return;
            default:
                throw new IllegalArgumentException("Unknown Graal probe mode: " + mode);
        }
    }

    private static void runLibraries(Path workDir) throws Exception {
        Class<?> mainWindowClass = MainWindow.class;
        Class<?> aboutDialogClass = AboutDialog.class;
        Class<?> webPanelClass = WebPanel.class;
        Class<?> webViewClass = WebViewComponent.class;
        if (mainWindowClass == null || aboutDialogClass == null || webPanelClass == null || webViewClass == null) {
            throw new AssertionError("Required application classes are missing");
        }

        for (String code : new String[]{"en", "it", "de", "es", "fr", "pl", "pt-BR"}) {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag(code));
            if (bundle.keySet().isEmpty()) {
                throw new AssertionError("Empty language bundle: " + code);
            }
        }

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

        String userAgent = WebPanel.buildUserAgent();
        if (!userAgent.contains("WareStat/")) {
            throw new AssertionError("Unexpected embedded browser user-agent");
        }
    }

    /**
     * Proves that the native WebView is actually attached by evaluating
     * JavaScript in a visible browser peer. Process liveness alone is not
     * sufficient because peer-attach failures occur asynchronously on the EDT.
     */
    private static void runWebViewProbe() throws Exception {
        installFatalUncaughtHandler();
        AtomicBoolean completed = new AtomicBoolean(false);

        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(20000);
                if (completed.compareAndSet(false, true)) {
                    System.err.println("Graal native WebView probe timed out");
                    Runtime.getRuntime().halt(2);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "graal-webview-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        SwingUtilities.invokeLater(() -> {
            try {
                JFrame frame = new JFrame("WareStat Graal WebView probe");
                WebViewComponent webView = WebViewComponent.create();
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setLayout(new BorderLayout());
                frame.add(webView, BorderLayout.CENTER);
                frame.setSize(640, 420);
                frame.setLocationRelativeTo(null);
                webView.setUrl("about:blank");
                frame.setVisible(true);

                final boolean[] requestPending = {false};
                final int[] attempts = {0};
                Timer poll = new Timer(250, null);
                poll.addActionListener(event -> {
                    if (completed.get() || requestPending[0] || !webView.isShowing()) {
                        return;
                    }
                    requestPending[0] = true;
                    attempts[0]++;
                    webView.evalAsync("return '" + WEBVIEW_OK + "';").whenComplete((value, error) -> {
                        requestPending[0] = false;
                        if (completed.get()) {
                            return;
                        }
                        if (error != null) {
                            if (isNotDisplayedError(error) && attempts[0] < 40) {
                                return;
                            }
                            failProbe("WebView JavaScript evaluation failed", error);
                            return;
                        }
                        if (value != null && value.contains(WEBVIEW_OK)) {
                            completed.set(true);
                            poll.stop();
                            webView.dispose();
                            frame.dispose();
                            System.out.println("Graal native WebView probe: JavaScript evaluation passed");
                            System.exit(0);
                        }
                    });
                });
                poll.start();
            } catch (Throwable error) {
                failProbe("WebView creation failed", error);
            }
        });

        Thread.sleep(30000);
    }

    private static boolean isNotDisplayedError(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("WebViewComponent not displayed")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs the real application entry point so reachability tracing also
     * covers code outside the constructor, notably Taskbar/ShellFolderManager.
     */
    private static void runActualMainWindowStartup() throws Exception {
        installFatalUncaughtHandler();

        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(30000);
                System.err.println("Graal startup probe timed out");
                Runtime.getRuntime().halt(2);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "graal-startup-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        MainWindow.main(new String[0]);
        Thread.sleep(10000);

        final MainWindow[] mainWindow = new MainWindow[1];
        final WebViewComponent[] embeddedWebView = new WebViewComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            for (Window open : Window.getWindows()) {
                if (open instanceof MainWindow && open.isShowing()) {
                    mainWindow[0] = (MainWindow) open;
                    embeddedWebView[0] = findWebView(open);
                    break;
                }
            }
        });
        if (mainWindow[0] == null) {
            throw new IllegalStateException("MainWindow was not showing after startup");
        }
        if (embeddedWebView[0] == null) {
            throw new IllegalStateException("MainWindow did not contain an embedded WebViewComponent");
        }

        verifyJavaScript(embeddedWebView[0], "WARESTAT_MAINWINDOW_WEBVIEW_OK", 15000);

        SwingUtilities.invokeAndWait(() -> {
            for (Window open : Window.getWindows()) {
                open.dispose();
            }
        });
        System.out.println("Graal startup probe: MainWindow and embedded WebView passed");
        System.exit(0);
    }

    private static WebViewComponent findWebView(Component component) {
        if (component instanceof WebViewComponent) {
            return (WebViewComponent) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                WebViewComponent found = findWebView(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void verifyJavaScript(WebViewComponent webView, String marker, long timeoutMillis)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        Throwable lastError = null;
        while (System.nanoTime() < deadline) {
            CompletableFuture<String> future = webView.evalAsync("return '" + marker + "';");
            try {
                String value = future.get(2, TimeUnit.SECONDS);
                if (value != null && value.contains(marker)) {
                    return;
                }
                lastError = new IllegalStateException("Unexpected WebView JavaScript result: " + value);
            } catch (ExecutionException error) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                lastError = cause;
                if (!isNotDisplayedError(cause)) {
                    throw new IllegalStateException("Embedded WebView JavaScript evaluation failed", cause);
                }
            } catch (TimeoutException error) {
                lastError = error;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Embedded WebView never became ready for JavaScript", lastError);
    }

    private static void installFatalUncaughtHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            System.err.println("Uncaught failure on " + thread.getName());
            error.printStackTrace();
            Runtime.getRuntime().halt(1);
        });
    }

    private static void failProbe(String message, Throwable error) {
        System.err.println(message);
        if (error != null) {
            error.printStackTrace();
        }
        Runtime.getRuntime().halt(1);
    }
}
