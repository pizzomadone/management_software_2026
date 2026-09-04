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
import java.awt.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
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
        refreshButton.addActionListener(event -> invokeEngine("reload"));
        navigation.add(backButton);
        navigation.add(forwardButton);
        navigation.add(refreshButton);
        navigation.add(statusLabel);
        return navigation;
    }

    private void initializeBrowser() {
        logEnvironment();
        try {
            diagnostic("Initializing JavaFX Swing bridge...");
            Class<?> jfxPanelClass = Class.forName("javafx.embed.swing.JFXPanel");
            Component jfxPanel = (Component) jfxPanelClass.getConstructor().newInstance();
            add(jfxPanel, BorderLayout.CENTER);
            runOnFxThread(() -> createWebView(jfxPanel));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            logFailure("JavaFX Swing bridge initialization", error);
            showUnavailableMessage();
        }
    }

    private void createWebView(Component jfxPanel) {
        try {
            diagnostic("Creating JavaFX WebView on thread " +
                Thread.currentThread().getName() + "...");
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
            diagnostic("Loading " + AppConstants.APP_WEB_URL);
            webEngine.getClass().getMethod("load", String.class)
                .invoke(webEngine, AppConstants.APP_WEB_URL);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            logFailure("WebView creation", error);
            SwingUtilities.invokeLater(this::showUnavailableMessage);
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
            String stateName = String.valueOf(state);
            diagnostic("Load state: " + stateName);
            if ("FAILED".equals(stateName)) logWorkerFailure(workerClass, worker);
            String key = switch (stateName) {
                case "RUNNING", "SCHEDULED" -> "Loading...";
                case "FAILED", "CANCELLED" -> "Unable to load the page";
                default -> "";
            };
            SwingUtilities.invokeLater(() -> statusLabel.setText(I18n.tr(key)));
        } catch (ReflectiveOperationException error) {
            logFailure("load-state inspection", error);
            SwingUtilities.invokeLater(() -> statusLabel.setText(""));
        }
    }

    private void logWorkerFailure(Class<?> workerClass, Object worker) {
        try {
            Object exception = workerClass.getMethod("getException").invoke(worker);
            if (exception instanceof Throwable throwable) {
                logFailure("page load for " + currentUrl, throwable);
            } else {
                diagnostic("Page load failed without a reported exception: " + currentUrl);
            }
        } catch (ReflectiveOperationException error) {
            logFailure("page-load error inspection", error);
        }
    }

    private static void logEnvironment() {
        diagnostic(String.format(Locale.ROOT,
            "Starting diagnostics: url=%s, java=%s, runtime=%s, os=%s %s, arch=%s",
            AppConstants.APP_WEB_URL, System.getProperty("java.version", "unknown"),
            System.getProperty("java.runtime.name", "unknown"),
            System.getProperty("os.name", "unknown"), System.getProperty("os.version", "unknown"),
            System.getProperty("os.arch", "unknown")));
        for (String module : new String[]{"javafx.base", "javafx.graphics", "javafx.swing", "javafx.web"}) {
            diagnostic(String.format(Locale.ROOT, "Module %-15s %s", module,
                ModuleLayer.boot().findModule(module).map(value -> "loaded (" +
                    value.getDescriptor().version().map(Object::toString).orElse("unknown version") + ")")
                    .orElse("MISSING")));
        }
    }

    private static void logFailure(String stage, Throwable error) {
        Throwable cause = error;
        while (cause instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            cause = invocation.getCause();
        }
        java.io.StringWriter trace = new java.io.StringWriter();
        cause.printStackTrace(new java.io.PrintWriter(trace));
        diagnostic("ERROR during " + stage + ": " + cause + System.lineSeparator() + trace);
    }

    private static void diagnostic(String message) {
        String line = "[Discover] " + Instant.now() + " " + message;
        System.err.println(line);
        try {
            Files.writeString(AppConstants.getAppDataDirectory().resolve("discover-debug.log"),
                line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception logError) {
            System.err.println("[Discover] Unable to write discover-debug.log: " + logError);
        }
    }

    private void navigateHistory(int offset) {
        runOnFxThread(() -> {
            try {
                Object history = webEngine.getClass().getMethod("getHistory").invoke(webEngine);
                history.getClass().getMethod("go", int.class).invoke(history, offset);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // The buttons are disabled whenever that history direction is unavailable.
            }
        });
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
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JLabel message = new JLabel(I18n.tr("Discover is temporarily unavailable"), SwingConstants.CENTER);
        JLabel logLocation = new JLabel("Debug: " + AppConstants.getAppDataDirectory()
            .resolve("discover-debug.log"), SwingConstants.CENTER);
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        logLocation.setAlignmentX(Component.CENTER_ALIGNMENT);
        details.add(message);
        details.add(Box.createVerticalStrut(8));
        details.add(logLocation);
        unavailable.add(details);
        add(unavailable, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private static void runOnFxThread(Runnable action) {
        try {
            Class<?> platform = Class.forName("javafx.application.Platform");
            platform.getMethod("runLater", Runnable.class).invoke(null, action);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            logFailure("JavaFX Platform.runLater", ignored);
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
