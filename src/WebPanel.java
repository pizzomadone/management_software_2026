import javax.swing.*;
import java.awt.*;
import java.time.ZoneId;
import java.util.Locale;

/** Discover page hosted by the native SwingWebView component. */
public class WebPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JButton backButton = new JButton("←");
    private final JButton forwardButton = new JButton("→");
    private final JButton refreshButton = new JButton("Refresh");
    private final JLabel statusLabel = new JLabel(I18n.tr("Loading..."));
    private SwingWebView browser;

    public WebPanel() {
        super(new BorderLayout());
        add(createNavigationBar(), BorderLayout.NORTH);
        browser = new SwingWebView(AppConstants.APP_WEB_URL, buildUserAgent(),
            this::updateNavigation, this::showUnavailableMessage);
        add(browser, BorderLayout.CENTER);
    }

    private JComponent createNavigationBar() {
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        backButton.setEnabled(false);
        forwardButton.setEnabled(false);
        backButton.setToolTipText("Go back in browser history");
        forwardButton.setToolTipText("Go forward in browser history");
        refreshButton.setToolTipText("Reload Discover");
        backButton.addActionListener(event -> browser.goBack());
        forwardButton.addActionListener(event -> browser.goForward());
        refreshButton.addActionListener(event -> browser.reload());
        navigation.add(backButton);
        navigation.add(forwardButton);
        navigation.add(refreshButton);
        navigation.add(statusLabel);
        return navigation;
    }

    private void updateNavigation(boolean canGoBack, boolean canGoForward, boolean loading) {
        backButton.setEnabled(canGoBack);
        forwardButton.setEnabled(canGoForward);
        statusLabel.setText(loading ? I18n.tr("Loading...") : "");
    }

    private void showUnavailableMessage(String detail) {
        statusLabel.setText("");
        System.err.println("[Discover] " + detail);
        browser.showError(I18n.tr("Discover is temporarily unavailable"), detail);
    }

    static String buildUserAgent() {
        Locale locale = Locale.getDefault();
        Dimension screen = GraphicsEnvironment.isHeadless()
            ? new Dimension(0, 0) : Toolkit.getDefaultToolkit().getScreenSize();
        String theme = SettingsPanel.getGlobalSetting("theme", "System Default");
        String value = String.format(Locale.ROOT,
            "%s/%s (Java/%s; %s/%s; %s; lang=%s; locale=%s; timezone=%s; screen=%dx%d; theme=%s; embedded-webview)",
            AppConstants.SOFTWARE_NAME, AppConstants.VERSION,
            System.getProperty("java.version", "unknown"),
            System.getProperty("os.name", "unknown"),
            System.getProperty("os.version", "unknown"),
            System.getProperty("os.arch", "unknown"), I18n.getLanguage(), locale.toLanguageTag(),
            ZoneId.systemDefault().getId(), screen.width, screen.height, theme);
        return value.replaceAll("[^\\x20-\\x7E]", "_");
    }
}
