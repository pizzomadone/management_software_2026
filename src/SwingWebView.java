import ca.weblite.webview.swing.WebViewComponent;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Swing wrapper around the native WebView supplied by webview-1.0.10. */
public final class SwingWebView extends JPanel {
    public interface NavigationListener {
        void changed(boolean canGoBack, boolean canGoForward, boolean loading);
    }

    private enum NavigationDirection { NONE, BACK, FORWARD }

    private final WebViewComponent webView;
    private final NavigationListener navigationListener;
    private final String userAgentScript;
    private final Timer stateTimer;
    private final Timer loadingTimeoutTimer;
    private final List<String> history = new ArrayList<>();
    private boolean stateRequestPending;
    private int statePollAttempts;
    private int historyIndex = -1;
    private NavigationDirection pendingNavigation = NavigationDirection.NONE;
    private int pendingNavigationPolls;

    public SwingWebView(String url, String userAgent, NavigationListener navigationListener,
            java.util.function.Consumer<String> failureListener) {
        super(new BorderLayout());
        this.navigationListener = navigationListener;
        this.userAgentScript = "window.wareStatUserAgent = " + javascriptString(userAgent) + ";";
        webView = WebViewComponent.create();
        add(webView, BorderLayout.CENTER);

        // Do not call synchronous eval() before the AWT peer is displayed.
        // Windows WebView2 can wait for a native window/message loop here,
        // blocking the EDT while the containing frame is still constructing.
        // Browser-side metadata is set asynchronously once the peer is showing.
        webView.setUrl(url);
        stateTimer = new Timer(350, event -> pollDocumentState(failureListener));
        loadingTimeoutTimer = new Timer(8_000, event ->
            navigationListener.changed(canGoBack(), canGoForward(), false));
        loadingTimeoutTimer.setRepeats(false);
        stateTimer.start();
        loadingTimeoutTimer.start();
    }

    private void pollDocumentState(java.util.function.Consumer<String> failureListener) {
        // Native WebViews cannot evaluate JavaScript until their AWT peer is visible.
        // This also prevents background cards from producing false startup errors.
        if (!webView.isShowing() || stateRequestPending) return;
        stateRequestPending = true;
        webView.evalAsync(userAgentScript + " return document.readyState + '|' + location.href;").whenComplete((state, error) ->
            SwingUtilities.invokeLater(() -> {
                stateRequestPending = false;
                if (error == null) {
                    String[] parts = state == null ? new String[0] : state.replace("\"", "").split("\\|", 2);
                    boolean loading = parts.length == 0 || !isDocumentReady(parts[0]);
                    if (parts.length > 1) updateHistory(parts[1]);
                    navigationListener.changed(canGoBack(), canGoForward(), loading);
                    if (!loading) loadingTimeoutTimer.stop();
                } else if (webView.isShowing() && !isNotDisplayedError(error)) {
                    failureListener.accept("SwingWebView error: " + error.getMessage());
                }
                statePollAttempts++;
                // A remote page may keep document.readyState incomplete because
                // of a slow third-party resource. Clear the loading indication,
                // but keep polling so browser history remains functional.
                if (statePollAttempts >= 30 && loadingTimeoutTimer.isRunning()) {
                    navigationListener.changed(canGoBack(), canGoForward(), false);
                    loadingTimeoutTimer.stop();
                }
            }));
    }

    private void updateHistory(String currentUrl) {
        if (currentUrl == null || currentUrl.isBlank()) return;

        if (historyIndex >= 0 && currentUrl.equals(history.get(historyIndex))) {
            if (pendingNavigation != NavigationDirection.NONE && ++pendingNavigationPolls >= 20) {
                pendingNavigation = NavigationDirection.NONE;
                pendingNavigationPolls = 0;
            }
            return;
        }

        if (pendingNavigation == NavigationDirection.BACK && historyIndex > 0) {
            historyIndex--;
            if (!currentUrl.equals(history.get(historyIndex))) history.set(historyIndex, currentUrl);
        } else if (pendingNavigation == NavigationDirection.FORWARD &&
                historyIndex >= 0 && historyIndex < history.size() - 1) {
            historyIndex++;
            if (!currentUrl.equals(history.get(historyIndex))) history.set(historyIndex, currentUrl);
        } else {
            while (history.size() > historyIndex + 1) history.remove(history.size() - 1);
            history.add(currentUrl);
            historyIndex = history.size() - 1;
        }

        pendingNavigation = NavigationDirection.NONE;
        pendingNavigationPolls = 0;
    }

    private boolean canGoBack() {
        return historyIndex > 0;
    }

    private boolean canGoForward() {
        return historyIndex >= 0 && historyIndex < history.size() - 1;
    }

    private static boolean isNotDisplayedError(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("WebViewComponent not displayed")) return true;
        }
        return false;
    }

    public void goBack() {
        if (!canGoBack()) return;
        pendingNavigation = NavigationDirection.BACK;
        pendingNavigationPolls = 0;
        statePollAttempts = 0;
        navigationListener.changed(canGoBack(), canGoForward(), true);
        webView.eval("history.back()");
        stateTimer.restart();
        loadingTimeoutTimer.restart();
    }

    public void goForward() {
        if (!canGoForward()) return;
        pendingNavigation = NavigationDirection.FORWARD;
        pendingNavigationPolls = 0;
        statePollAttempts = 0;
        navigationListener.changed(canGoBack(), canGoForward(), true);
        webView.eval("history.forward()");
        stateTimer.restart();
        loadingTimeoutTimer.restart();
    }

    public void reload() {
        statePollAttempts = 0;
        navigationListener.changed(canGoBack(), canGoForward(), true);
        webView.eval("location.reload()");
        stateTimer.restart();
        loadingTimeoutTimer.restart();
    }

    public void showError(String heading, String detail) {
        stateTimer.stop();
        loadingTimeoutTimer.stop();
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
