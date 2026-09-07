import ca.weblite.webview.swing.WebViewComponent;
import javax.swing.*;
import java.awt.*;

/** Swing wrapper around the native WebView supplied by webview-1.0.10. */
public final class SwingWebView extends JPanel {
    public interface NavigationListener {
        void changed(boolean canGoBack, boolean canGoForward, boolean loading);
    }

    private final WebViewComponent webView;
    private final NavigationListener navigationListener;
    private final Timer stateTimer;
    private boolean stateRequestPending;
    private int statePollAttempts;

    public SwingWebView(String url, String userAgent, NavigationListener navigationListener,
            java.util.function.Consumer<String> failureListener) {
        super(new BorderLayout());
        this.navigationListener = navigationListener;
        webView = WebViewComponent.create();
        add(webView, BorderLayout.CENTER);

        // eval() configures browser-side behaviour; evalAsync() monitors loading.
        webView.eval("window.wareStatUserAgent = " + javascriptString(userAgent) + ";");
        webView.setUrl(url);
        stateTimer = new Timer(350, event -> pollDocumentState(failureListener));
        stateTimer.start();
    }

    private void pollDocumentState(java.util.function.Consumer<String> failureListener) {
        // Native WebViews cannot evaluate JavaScript until their AWT peer is visible.
        // This also prevents background cards from producing false startup errors.
        if (!webView.isShowing() || stateRequestPending) return;
        stateRequestPending = true;
        webView.evalAsync("document.readyState").whenComplete((state, error) ->
            SwingUtilities.invokeLater(() -> {
                stateRequestPending = false;
                if (error == null) {
                    boolean loading = !isDocumentReady(state);
                    navigationListener.changed(true, true, loading);
                    if (!loading) stateTimer.stop();
                } else if (webView.isShowing() && !isNotDisplayedError(error)) {
                    failureListener.accept("SwingWebView error: " + error.getMessage());
                }
                statePollAttempts++;
                // A remote page may keep document.readyState incomplete because
                // of a slow third-party resource. Do not leave a permanent
                // "Loading..." message once the page has had time to render.
                if (statePollAttempts >= 30) {
                    navigationListener.changed(true, true, false);
                    stateTimer.stop();
                }
            }));
    }

    private static boolean isNotDisplayedError(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("WebViewComponent not displayed")) return true;
        }
        return false;
    }

    public void goBack() { webView.eval("history.back()"); }
    public void goForward() { webView.eval("history.forward()"); }
    public void reload() {
        statePollAttempts = 0;
        navigationListener.changed(false, false, true);
        webView.eval("location.reload()");
        stateTimer.restart();
    }

    public void showError(String heading, String detail) {
        stateTimer.stop();
        String html = "<main style='font-family:sans-serif;text-align:center;padding-top:20vh'>" +
            "<h2>" + html(heading) + "</h2><p>" + html(detail) + "</p></main>";
        webView.eval("document.body.innerHTML = " + javascriptString(html));
    }

    static boolean isDocumentReady(String state) {
        if (state == null) return false;
        String normalized = state.trim().toLowerCase(java.util.Locale.ROOT)
            .replace("\"", "").replace("'", "");
        return normalized.contains("complete") || normalized.contains("interactive");
    }

    /** Retained for compatibility with existing readiness tests. */
    static boolean isDocumentComplete(String state) {
        return isDocumentReady(state);
    }

    private static String javascriptString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
