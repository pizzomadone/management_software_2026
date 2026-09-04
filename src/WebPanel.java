/*
 * Copyright © 2026 WareStat (www.warestat.com). All rights reserved.
 *
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or reverse engineering
 * is strictly prohibited.
 *
 * See LICENSE.txt for full terms.
 */

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.lang.reflect.*;
import java.net.URI;
import java.net.URL;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Swing panel hosting the optional JavaFX WebView used for Discover. */
public class WebPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JButton backButton = new JButton("←");
    private final JButton forwardButton = new JButton("→");
    private final JButton refreshButton = new JButton("Refresh");
    private final JLabel statusLabel = new JLabel("Loading...");
    private Object webEngine;
    private volatile String currentUrl = AppConstants.APP_WEB_URL;
    private JEditorPane swingBrowser;
    private final List<String> swingHistory = new ArrayList<>();
    private int swingHistoryIndex = -1;

    public WebPanel() {
        super(new BorderLayout());
        add(createNavigationBar(), BorderLayout.NORTH);
        initializeBrowser();
    }

    private JComponent createNavigationBar() {
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        backButton.setEnabled(false);
        forwardButton.setEnabled(false);
        backButton.setToolTipText("Go back in browser history");
        forwardButton.setToolTipText("Go forward in browser history");
        refreshButton.setToolTipText("Reload Discover");
        backButton.addActionListener(event -> navigateHistory(-1));
        forwardButton.addActionListener(event -> navigateHistory(1));
        refreshButton.addActionListener(event -> refresh());
        navigation.add(backButton);
        navigation.add(forwardButton);
        navigation.add(refreshButton);
        navigation.add(statusLabel);
        return navigation;
    }

    private void initializeBrowser() {
        try {
            Class<?> jfxPanelClass = Class.forName("javafx.embed.swing.JFXPanel");
            Component jfxPanel = (Component) jfxPanelClass.getConstructor().newInstance();
            add(jfxPanel, BorderLayout.CENTER);
            runOnFxThread(() -> createWebView(jfxPanel));
        } catch (ReflectiveOperationException | LinkageError error) {
            initializeSwingBrowser();
        }
    }

    private void createWebView(Component jfxPanel) {
        try {
            Class<?> webViewClass = Class.forName("javafx.scene.web.WebView");
            Object webView = webViewClass.getConstructor().newInstance();
            webEngine = webViewClass.getMethod("getEngine").invoke(webView);
            webEngine.getClass().getMethod("setUserAgent", String.class)
                .invoke(webEngine, buildUserAgent());
            installBrowserListeners();

            Class<?> parentClass = Class.forName("javafx.scene.Parent");
            Class<?> sceneClass = Class.forName("javafx.scene.Scene");
            Object scene = sceneClass.getConstructor(parentClass).newInstance(webView);
            jfxPanel.getClass().getMethod("setScene", sceneClass).invoke(jfxPanel, scene);
            webEngine.getClass().getMethod("load", String.class)
                .invoke(webEngine, AppConstants.APP_WEB_URL);
        } catch (ReflectiveOperationException | LinkageError error) {
            SwingUtilities.invokeLater(this::initializeSwingBrowser);
        }
    }

    private void installBrowserListeners() throws ReflectiveOperationException {
        Object history = webEngine.getClass().getMethod("getHistory").invoke(webEngine);
        Object indexProperty = history.getClass().getMethod("currentIndexProperty").invoke(history);
        addChangeListener(indexProperty, () -> updateHistoryButtons(history));

        Object locationProperty = webEngine.getClass().getMethod("locationProperty").invoke(webEngine);
        addChangeListener(locationProperty, () -> updateCurrentLocation(locationProperty));

        Object worker = webEngine.getClass().getMethod("getLoadWorker").invoke(webEngine);
        Class<?> workerClass = Class.forName("javafx.concurrent.Worker");
        Object stateProperty = workerClass.getMethod("stateProperty").invoke(worker);
        addChangeListener(stateProperty, () -> updateLoadStatus(worker));
    }

    private void addChangeListener(Object observable, Runnable callback) throws ReflectiveOperationException {
        Class<?> listenerClass = Class.forName("javafx.beans.value.ChangeListener");
        Object listener = Proxy.newProxyInstance(listenerClass.getClassLoader(),
            new Class<?>[]{listenerClass}, (proxy, method, args) -> {
                if ("changed".equals(method.getName())) callback.run();
                return null;
            });
        Class<?> observableClass = Class.forName("javafx.beans.value.ObservableValue");
        observableClass.getMethod("addListener", listenerClass).invoke(observable, listener);
    }

    private void updateHistoryButtons(Object history) {
        try {
            int index = (int) history.getClass().getMethod("getCurrentIndex").invoke(history);
            List<?> entries = (List<?>) history.getClass().getMethod("getEntries").invoke(history);
            int size = entries.size();
            SwingUtilities.invokeLater(() -> {
                backButton.setEnabled(index > 0);
                forwardButton.setEnabled(index >= 0 && index < size - 1);
            });
        } catch (ReflectiveOperationException ignored) {
            // Navigation remains usable through direct page links.
        }
    }

    private void updateCurrentLocation(Object locationProperty) {
        try {
            Class<?> observableClass = Class.forName("javafx.beans.value.ObservableValue");
            Object location = observableClass.getMethod("getValue").invoke(locationProperty);
            if (location != null && !location.toString().isBlank()) currentUrl = location.toString();
        } catch (ReflectiveOperationException ignored) {
            // Keep the last successfully observed URL.
        }
    }

    private void updateLoadStatus(Object worker) {
        try {
            Class<?> workerClass = Class.forName("javafx.concurrent.Worker");
            Object state = workerClass.getMethod("getState").invoke(worker);
            String key = switch (String.valueOf(state)) {
                case "RUNNING", "SCHEDULED" -> "Loading...";
                case "FAILED", "CANCELLED" -> "Unable to load the page";
                default -> "";
            };
            SwingUtilities.invokeLater(() -> statusLabel.setText(I18n.tr(key)));
        } catch (ReflectiveOperationException ignored) {
            SwingUtilities.invokeLater(() -> statusLabel.setText(""));
        }
    }

    private void navigateHistory(int offset) {
        if (webEngine == null) {
            int destination = swingHistoryIndex + offset;
            if (destination >= 0 && destination < swingHistory.size()) {
                swingHistoryIndex = destination;
                loadSwingPage(swingHistory.get(destination), false);
            }
            return;
        }
        runOnFxThread(() -> {
            try {
                Object history = webEngine.getClass().getMethod("getHistory").invoke(webEngine);
                history.getClass().getMethod("go", int.class).invoke(history, offset);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // The buttons are disabled whenever that history direction is unavailable.
            }
        });
    }

    private void refresh() {
        if (webEngine != null) invokeEngine("reload");
        else loadSwingPage(currentUrl, false);
    }

    private void initializeSwingBrowser() {
        if (swingBrowser != null) return;
        removeCenterComponents();
        swingBrowser = new JEditorPane();
        swingBrowser.setEditable(false);
        swingBrowser.setContentType("text/html");
        swingBrowser.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getURL() != null) {
                loadSwingPage(event.getURL().toExternalForm(), true);
            }
        });
        add(new JScrollPane(swingBrowser), BorderLayout.CENTER);
        loadSwingPage(AppConstants.APP_WEB_URL, true);
        revalidate();
        repaint();
    }

    private void loadSwingPage(String address, boolean addToHistory) {
        statusLabel.setText(I18n.tr("Loading..."));
        backButton.setEnabled(false);
        forwardButton.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                URL url = URI.create(address).toURL();
                swingBrowser.setPage(url);
                return url.toExternalForm();
            }

            @Override
            protected void done() {
                try {
                    currentUrl = get();
                    if (addToHistory) {
                        while (swingHistory.size() > swingHistoryIndex + 1) {
                            swingHistory.remove(swingHistory.size() - 1);
                        }
                        swingHistory.add(currentUrl);
                        swingHistoryIndex = swingHistory.size() - 1;
                    }
                    statusLabel.setText("");
                    updateSwingHistoryButtons();
                } catch (Exception error) {
                    showUnavailableMessage();
                }
            }
        }.execute();
    }

    private void updateSwingHistoryButtons() {
        backButton.setEnabled(swingHistoryIndex > 0);
        forwardButton.setEnabled(swingHistoryIndex >= 0 && swingHistoryIndex < swingHistory.size() - 1);
    }

    private void removeCenterComponents() {
        for (Component component : getComponents()) {
            if (component != getComponent(0)) remove(component);
        }
    }

    private void invokeEngine(String method) {
        if (webEngine == null) return;
        runOnFxThread(() -> {
            try {
                webEngine.getClass().getMethod(method).invoke(webEngine);
            } catch (ReflectiveOperationException ignored) {
                // A subsequent navigation can still recover the embedded browser.
            }
        });
    }

    private void showUnavailableMessage() {
        statusLabel.setText("");
        removeCenterComponents();
        JPanel unavailable = new JPanel(new GridBagLayout());
        JLabel message = new JLabel(I18n.tr("Discover is temporarily unavailable"));
        unavailable.add(message);
        add(unavailable, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private static void runOnFxThread(Runnable action) {
        try {
            Class<?> platform = Class.forName("javafx.application.Platform");
            platform.getMethod("runLater", Runnable.class).invoke(null, action);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // JavaFX availability is reported by initializeBrowser().
        }
    }

    static String buildUserAgent() {
        Locale locale = Locale.getDefault();
        Dimension screen = GraphicsEnvironment.isHeadless()
            ? new Dimension(0, 0) : Toolkit.getDefaultToolkit().getScreenSize();
        String theme = SettingsPanel.getGlobalSetting("theme", "System Default");
        String value = String.format(Locale.ROOT,
            "%s/%s (Java/%s; java-vendor=%s; runtime=%s; %s/%s; %s; data-model=%s; lang=%s; locale=%s; country=%s; timezone=%s; screen=%dx%d; theme=%s; embedded-webview)",
            AppConstants.SOFTWARE_NAME, AppConstants.VERSION,
            System.getProperty("java.version", "unknown"),
            System.getProperty("java.vendor", "unknown"),
            System.getProperty("java.runtime.name", "unknown"),
            System.getProperty("os.name", "unknown"),
            System.getProperty("os.version", "unknown"),
            System.getProperty("os.arch", "unknown"),
            System.getProperty("sun.arch.data.model", "unknown"),
            I18n.getLanguage(), locale.toLanguageTag(), locale.getCountry(), ZoneId.systemDefault().getId(),
            screen.width, screen.height, theme);
        return value.replaceAll("[^\\x20-\\x7E]", "_");
    }
}
