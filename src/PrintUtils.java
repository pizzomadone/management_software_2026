import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.swing.*;
import java.awt.*;
import java.awt.print.PrinterException;
import java.text.MessageFormat;

/**
 * Utility class for handling printing operations with better error handling
 * and user feedback when print services are not available.
 */
public class PrintUtils {

    /**
     * Checks if any print services are available on the system.
     * @return true if at least one print service is available, false otherwise
     */
    public static boolean isPrintServiceAvailable() {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        return printServices != null && printServices.length > 0;
    }

    /**
     * Gets the default print service.
     * @return the default print service, or null if none is available
     */
    public static PrintService getDefaultPrintService() {
        return PrintServiceLookup.lookupDefaultPrintService();
    }

    /**
     * Prints a JTable with automatic print service detection and error handling.
     * Shows user-friendly error messages when print services are not available.
     *
     * @param table the JTable to print
     * @param parent the parent component for dialog positioning
     * @param title optional title for error dialogs (can be null)
     * @return true if printing was successful, false otherwise
     */
    public static boolean printTable(JTable table, Component parent, String title) {
        return printTable(table, parent, title, null, null, JTable.PrintMode.NORMAL);
    }

    /**
     * Prints a JTable with custom header and footer.
     * Shows user-friendly error messages when print services are not available.
     *
     * @param table the JTable to print
     * @param parent the parent component for dialog positioning
     * @param title optional title for error dialogs (can be null)
     * @param header header format for the printed page
     * @param footer footer format for the printed page
     * @param printMode the print mode (NORMAL or FIT_WIDTH)
     * @return true if printing was successful, false otherwise
     */
    public static boolean printTable(JTable table, Component parent, String title,
                                    MessageFormat header, MessageFormat footer,
                                    JTable.PrintMode printMode) {
        // Check if print services are available
        if (!isPrintServiceAvailable()) {
            showNoPrintServiceDialog(parent);
            return false;
        }

        try {
            boolean complete;
            if (header != null || footer != null) {
                complete = table.print(printMode, header, footer);
            } else {
                complete = table.print();
            }

            if (!complete) {
                // User cancelled the print job
                return false;
            }

            return true;

        } catch (PrinterException e) {
            showPrintErrorDialog(parent, e, title);
            return false;
        } catch (Exception e) {
            showPrintErrorDialog(parent, e, title);
            return false;
        }
    }

    /**
     * Shows a user-friendly dialog when no print services are available.
     * Provides guidance on how to resolve the issue.
     *
     * @param parent the parent component for dialog positioning
     */
    private static void showNoPrintServiceDialog(Component parent) {
        String message = "<html><body style='width: 400px'>" +
            "<h3>No Print Service Found</h3>" +
            "<p>No printers are currently configured on this system.</p>" +
            "<br>" +
            "<p><b>To resolve this issue:</b></p>" +
            "<ul>" +
            "<li>Install a physical printer or a virtual PDF printer (e.g., Microsoft Print to PDF, Adobe PDF)</li>" +
            "<li>Make sure the printer is properly configured in your operating system</li>" +
            "<li>On Windows: Settings → Devices → Printers & scanners</li>" +
            "<li>On Linux: Check CUPS configuration</li>" +
            "<li>On macOS: System Preferences → Printers & Scanners</li>" +
            "</ul>" +
            "<br>" +
            "<p><b>Alternative:</b> You can export the report to CSV format instead.</p>" +
            "</body></html>";

        JOptionPane.showMessageDialog(
            parent,
            message,
            "No Print Service Available",
            JOptionPane.WARNING_MESSAGE
        );
    }

    /**
     * Shows an error dialog when printing fails.
     *
     * @param parent the parent component for dialog positioning
     * @param exception the exception that occurred
     * @param title optional custom title for the dialog
     */
    private static void showPrintErrorDialog(Component parent, Exception exception, String title) {
        String dialogTitle = (title != null) ? title : "Print Error";
        String message = "<html><body style='width: 350px'>" +
            "<h3>Error During Printing</h3>" +
            "<p>" + exception.getMessage() + "</p>" +
            "<br>" +
            "<p>Please check your printer configuration and try again.</p>" +
            "<p>Alternatively, you can export the report to CSV format.</p>" +
            "</body></html>";

        JOptionPane.showMessageDialog(
            parent,
            message,
            dialogTitle,
            JOptionPane.ERROR_MESSAGE
        );

        exception.printStackTrace();
    }

    /**
     * Returns information about available print services.
     * Useful for debugging or showing printer information to users.
     *
     * @return a string describing available print services
     */
    public static String getPrintServiceInfo() {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);

        if (printServices == null || printServices.length == 0) {
            return "No print services available";
        }

        StringBuilder info = new StringBuilder();
        info.append("Available print services:\n");
        for (PrintService service : printServices) {
            info.append("  - ").append(service.getName()).append("\n");
        }

        PrintService defaultService = getDefaultPrintService();
        if (defaultService != null) {
            info.append("\nDefault printer: ").append(defaultService.getName());
        }

        return info.toString();
    }

    // Private constructor to prevent instantiation
    private PrintUtils() {
        throw new AssertionError("PrintUtils class cannot be instantiated");
    }
}
