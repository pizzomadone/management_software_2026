import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.lang.reflect.*;
import java.util.function.Consumer;

/**
 * Swing Chromium view backed by JCEF Maven. Chromium provides the JavaScript,
 * HTML5 media and iframe support required by the Discover page and YouTube.
 */
public final class SwingWebView extends JPanel {
    public interface NavigationListener {
        void changed(boolean canGoBack, boolean canGoForward, boolean loading);
    }

    private volatile Object cefBrowser;
    private final NavigationListener navigationListener;
    private final Consumer<String> failureListener;
    private final Timer stateTimer;

    public SwingWebView(String url, String userAgent, NavigationListener navigationListener,
            Consumer<String> failureListener) {
        super(new BorderLayout());
        this.navigationListener = navigationListener;
        this.failureListener = failureListener;
        add(new JLabel(I18n.tr("Loading..."), SwingConstants.CENTER), BorderLayout.CENTER);
        stateTimer = new Timer(300, event -> publishNavigationState());
        initialize(url, userAgent);
    }

    private void initialize(String url, String userAgent) {
        new SwingWorker<Component, Void>() {
            @Override
            protected Component doInBackground() throws Exception {
                Class<?> builderClass = Class.forName("me.friwi.jcefmaven.CefAppBuilder");
                Object builder = builderClass.getConstructor().newInstance();
                File installDirectory = AppConstants.getAppDataDirectory().resolve("jcef").toFile();
                builderClass.getMethod("setInstallDir", File.class).invoke(builder, installDirectory);
                builderClass.getMethod("addJcefArgs", String[].class).invoke(builder,
                    (Object) new String[]{"--user-agent=" + userAgent, "--autoplay-policy=no-user-gesture-required"});

                Object cefApp = builderClass.getMethod("build").invoke(builder);
                Object client = cefApp.getClass().getMethod("createClient").invoke(cefApp);
                cefBrowser = client.getClass().getMethod("createBrowser",
                    String.class, boolean.class, boolean.class).invoke(client, url, false, false);
                return (Component) cefBrowser.getClass().getMethod("getUIComponent").invoke(cefBrowser);
            }

            @Override
            protected void done() {
                try {
                    Component view = get();
                    removeAll();
                    add(view, BorderLayout.CENTER);
                    revalidate();
                    repaint();
                    stateTimer.start();
                } catch (Exception error) {
                    Throwable cause = unwrap(error);
                    failureListener.accept("SwingWebView/JCEF initialization failed: " + cause);
                    cause.printStackTrace(System.err);
                }
            }
        }.execute();
    }

    public void goBack() { invokeBrowser("goBack"); }
    public void goForward() { invokeBrowser("goForward"); }
    public void reload() { invokeBrowser("reload"); }

    public void showError(String heading, String detail) {
        removeAll();
        JPanel message = new JPanel();
        message.setLayout(new BoxLayout(message, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(heading, SwingConstants.CENTER);
        JLabel reason = new JLabel(detail, SwingConstants.CENTER);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        reason.setAlignmentX(Component.CENTER_ALIGNMENT);
        message.add(title);
        message.add(Box.createVerticalStrut(8));
        message.add(reason);
        add(message, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void publishNavigationState() {
        Object browser = cefBrowser;
        if (browser == null) return;
        try {
            boolean back = (boolean) browser.getClass().getMethod("canGoBack").invoke(browser);
            boolean forward = (boolean) browser.getClass().getMethod("canGoForward").invoke(browser);
            boolean loading = (boolean) browser.getClass().getMethod("isLoading").invoke(browser);
            navigationListener.changed(back, forward, loading);
        } catch (ReflectiveOperationException error) {
            stateTimer.stop();
            failureListener.accept("Unable to inspect SwingWebView navigation: " + unwrap(error));
        }
    }

    private void invokeBrowser(String method) {
        Object browser = cefBrowser;
        if (browser == null) return;
        try {
            browser.getClass().getMethod(method).invoke(browser);
        } catch (ReflectiveOperationException error) {
            failureListener.accept("SwingWebView operation " + method + " failed: " + unwrap(error));
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable result = error;
        while ((result instanceof InvocationTargetException || result instanceof java.util.concurrent.ExecutionException)
                && result.getCause() != null) result = result.getCause();
        return result;
    }
}
