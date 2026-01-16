import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Utility class for generating PDF reports from JTable data.
 * Supports multi-page reports with automatic page breaks and headers.
 */
public class ReportPDFGenerator {
    private static final float MARGIN = 40f;
    private static final float LINE_HEIGHT = 12f;
    private static final float HEADER_HEIGHT = 60f;
    private static final float FOOTER_HEIGHT = 30f;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");

    private final TableModel tableModel;
    private final String reportTitle;
    private final String subtitle;
    private final String defaultFileName;

    /**
     * Creates a new ReportPDFGenerator.
     *
     * @param tableModel the table model containing the data
     * @param reportTitle the main title of the report
     * @param subtitle optional subtitle (can be null)
     * @param defaultFileName default name for the saved PDF file
     */
    public ReportPDFGenerator(TableModel tableModel, String reportTitle, String subtitle, String defaultFileName) {
        this.tableModel = tableModel;
        this.reportTitle = reportTitle;
        this.subtitle = subtitle;
        this.defaultFileName = defaultFileName != null ? defaultFileName : "report.pdf";
    }

    /**
     * Shows a file chooser and generates the PDF report.
     *
     * @param parent the parent component for dialogs
     */
    public void generateAndSave(Component parent) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Report as PDF");
        fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Files", "pdf"));

        // Use settings system for last directory
        String lastDirectory = getLastDirectory();
        if (lastDirectory != null && !lastDirectory.trim().isEmpty()) {
            File dir = new File(lastDirectory);
            if (dir.exists() && dir.isDirectory()) {
                fileChooser.setCurrentDirectory(dir);
            }
        }

        // Set default file name
        String fileName = defaultFileName;
        if (!fileName.toLowerCase().endsWith(".pdf")) {
            fileName += ".pdf";
        }
        fileChooser.setSelectedFile(new File(fileName));

        int userSelection = fileChooser.showSaveDialog(parent);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            // Ensure .pdf extension
            if (!file.getName().toLowerCase().endsWith(".pdf")) {
                file = new File(file.getAbsolutePath() + ".pdf");
            }

            // Save last directory
            saveLastDirectory(file.getParent());

            try {
                generatePDF(file);

                // Ask to open the file
                int choice = JOptionPane.showConfirmDialog(parent,
                    "PDF report saved successfully!\nWould you like to open the PDF file?",
                    "Open PDF",
                    JOptionPane.YES_NO_OPTION);

                if (choice == JOptionPane.YES_OPTION) {
                    try {
                        Desktop.getDesktop().open(file);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(parent,
                            "Cannot open PDF automatically. File saved to:\n" + file.getAbsolutePath(),
                            "Information",
                            JOptionPane.INFORMATION_MESSAGE);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(parent,
                    "Error generating PDF: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Get the last used PDF directory from settings
     */
    private String getLastDirectory() {
        return SettingsPanel.getGlobalSetting("pdf_last_directory",
               SettingsPanel.getGlobalSetting("pdf_default_directory", System.getProperty("user.home")));
    }

    /**
     * Save the last used PDF directory to settings
     */
    private void saveLastDirectory(String directory) {
        if (directory != null && !directory.trim().isEmpty()) {
            SettingsPanel.setGlobalSetting("pdf_last_directory", directory);
        }
    }

    private void generatePDF(File outputFile) throws IOException {
        try (PDDocument document = new PDDocument()) {
            // Calculate column widths
            int columnCount = tableModel.getColumnCount();
            float[] columnWidths = calculateColumnWidths(columnCount);
            float tableWidth = 0;
            for (float width : columnWidths) {
                tableWidth += width;
            }

            // Generate pages
            int rowCount = tableModel.getRowCount();
            int currentRow = 0;
            int pageNumber = 1;

            while (currentRow < rowCount) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    float yPosition = page.getMediaBox().getHeight() - MARGIN;

                    // Draw header
                    yPosition = drawHeader(contentStream, yPosition, page, pageNumber);
                    yPosition -= 10f;

                    // Draw table header
                    yPosition = drawTableHeader(contentStream, yPosition, columnWidths);
                    yPosition -= 5f;

                    // Draw table rows (as many as fit on this page)
                    int maxRowsPerPage = (int) ((yPosition - FOOTER_HEIGHT - MARGIN) / LINE_HEIGHT) - 2;
                    int rowsOnThisPage = Math.min(maxRowsPerPage, rowCount - currentRow);

                    for (int i = 0; i < rowsOnThisPage && currentRow < rowCount; i++, currentRow++) {
                        yPosition = drawTableRow(contentStream, yPosition, currentRow, columnWidths);
                    }

                    // Draw footer
                    drawFooter(contentStream, page, pageNumber, calculateTotalPages(rowCount, maxRowsPerPage));
                }

                pageNumber++;
            }

            document.save(outputFile);
        }
    }

    private float drawHeader(PDPageContentStream contentStream, float yPosition, PDPage page, int pageNumber) throws IOException {
        // Report title
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18f);
        float titleWidth = reportTitle.length() * 18f * 0.55f;
        float titleX = (page.getMediaBox().getWidth() - titleWidth) / 2;
        contentStream.newLineAtOffset(titleX, yPosition);
        contentStream.showText(reportTitle);
        contentStream.endText();
        yPosition -= 20f;

        // Subtitle (if provided)
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f);
            float subtitleWidth = subtitle.length() * 10f * 0.55f;
            float subtitleX = (page.getMediaBox().getWidth() - subtitleWidth) / 2;
            contentStream.newLineAtOffset(subtitleX, yPosition);
            contentStream.showText(subtitle);
            contentStream.endText();
            yPosition -= 15f;
        }

        // Date and page info
        String dateStr = "Generated: " + DATE_FORMAT.format(new Date());
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9f);
        contentStream.newLineAtOffset(MARGIN, yPosition);
        contentStream.showText(dateStr);
        contentStream.endText();

        yPosition -= 15f;

        // Separator line
        contentStream.moveTo(MARGIN, yPosition);
        contentStream.lineTo(page.getMediaBox().getWidth() - MARGIN, yPosition);
        contentStream.setLineWidth(1f);
        contentStream.stroke();

        return yPosition - 10f;
    }

    private float drawTableHeader(PDPageContentStream contentStream, float yPosition, float[] columnWidths) throws IOException {
        float xPosition = MARGIN;

        // Background for header
        contentStream.setNonStrokingColor(0.9f, 0.9f, 0.9f);
        contentStream.addRect(MARGIN, yPosition - LINE_HEIGHT + 2,
                            calculateTotalWidth(columnWidths), LINE_HEIGHT);
        contentStream.fill();
        contentStream.setNonStrokingColor(0f, 0f, 0f);

        // Header text
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 9f);
        contentStream.newLineAtOffset(xPosition + 2, yPosition - LINE_HEIGHT + 5);

        for (int col = 0; col < tableModel.getColumnCount(); col++) {
            String columnName = tableModel.getColumnName(col);

            // Truncate if too long
            if (columnName.length() * 9f * 0.55f > columnWidths[col] - 4) {
                int maxChars = (int) ((columnWidths[col] - 10) / (9f * 0.55f));
                if (maxChars > 3) {
                    columnName = columnName.substring(0, maxChars - 3) + "...";
                }
            }

            contentStream.showText(columnName);

            if (col < tableModel.getColumnCount() - 1) {
                xPosition += columnWidths[col];
                contentStream.newLineAtOffset(columnWidths[col], 0);
            }
        }
        contentStream.endText();

        // Border lines
        xPosition = MARGIN;
        for (int col = 0; col <= tableModel.getColumnCount(); col++) {
            contentStream.moveTo(xPosition, yPosition);
            contentStream.lineTo(xPosition, yPosition - LINE_HEIGHT);
            contentStream.stroke();
            if (col < tableModel.getColumnCount()) {
                xPosition += columnWidths[col];
            }
        }

        // Horizontal lines
        contentStream.moveTo(MARGIN, yPosition);
        contentStream.lineTo(MARGIN + calculateTotalWidth(columnWidths), yPosition);
        contentStream.stroke();
        contentStream.moveTo(MARGIN, yPosition - LINE_HEIGHT);
        contentStream.lineTo(MARGIN + calculateTotalWidth(columnWidths), yPosition - LINE_HEIGHT);
        contentStream.stroke();

        return yPosition - LINE_HEIGHT;
    }

    private float drawTableRow(PDPageContentStream contentStream, float yPosition, int row, float[] columnWidths) throws IOException {
        float xPosition = MARGIN;

        // Zebra striping
        if (row % 2 == 0) {
            contentStream.setNonStrokingColor(0.97f, 0.97f, 0.97f);
            contentStream.addRect(MARGIN, yPosition - LINE_HEIGHT + 2,
                                calculateTotalWidth(columnWidths), LINE_HEIGHT);
            contentStream.fill();
            contentStream.setNonStrokingColor(0f, 0f, 0f);
        }

        // Row text
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8f);
        contentStream.newLineAtOffset(xPosition + 2, yPosition - LINE_HEIGHT + 4);

        for (int col = 0; col < tableModel.getColumnCount(); col++) {
            Object value = tableModel.getValueAt(row, col);
            String cellText = value != null ? value.toString() : "";

            // Truncate if too long
            if (cellText.length() * 8f * 0.55f > columnWidths[col] - 4) {
                int maxChars = (int) ((columnWidths[col] - 10) / (8f * 0.55f));
                if (maxChars > 3) {
                    cellText = cellText.substring(0, maxChars - 3) + "...";
                }
            }

            contentStream.showText(cellText);

            if (col < tableModel.getColumnCount() - 1) {
                xPosition += columnWidths[col];
                contentStream.newLineAtOffset(columnWidths[col], 0);
            }
        }
        contentStream.endText();

        // Vertical borders
        xPosition = MARGIN;
        for (int col = 0; col <= tableModel.getColumnCount(); col++) {
            contentStream.moveTo(xPosition, yPosition);
            contentStream.lineTo(xPosition, yPosition - LINE_HEIGHT);
            contentStream.setLineWidth(0.5f);
            contentStream.stroke();
            if (col < tableModel.getColumnCount()) {
                xPosition += columnWidths[col];
            }
        }

        // Bottom horizontal border
        contentStream.moveTo(MARGIN, yPosition - LINE_HEIGHT);
        contentStream.lineTo(MARGIN + calculateTotalWidth(columnWidths), yPosition - LINE_HEIGHT);
        contentStream.setLineWidth(0.5f);
        contentStream.stroke();

        return yPosition - LINE_HEIGHT;
    }

    private void drawFooter(PDPageContentStream contentStream, PDPage page, int pageNumber, int totalPages) throws IOException {
        float yPosition = MARGIN - 10;

        // Page number
        String pageText = String.format("Page %d of %d", pageNumber, totalPages);
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8f);
        float pageTextWidth = pageText.length() * 8f * 0.55f;
        float pageTextX = (page.getMediaBox().getWidth() - pageTextWidth) / 2;
        contentStream.newLineAtOffset(pageTextX, yPosition);
        contentStream.showText(pageText);
        contentStream.endText();

        // Software branding
        String brandText = "Generated by " + AppConstants.FULL_TITLE;
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 7f);
        float brandTextWidth = brandText.length() * 7f * 0.55f;
        float brandTextX = (page.getMediaBox().getWidth() - brandTextWidth) / 2;
        contentStream.newLineAtOffset(brandTextX, yPosition - 10);
        contentStream.showText(brandText);
        contentStream.endText();
    }

    private float[] calculateColumnWidths(int columnCount) {
        float pageWidth = PDRectangle.A4.getWidth() - (2 * MARGIN);
        float[] widths = new float[columnCount];
        float equalWidth = pageWidth / columnCount;

        for (int i = 0; i < columnCount; i++) {
            widths[i] = equalWidth;
        }

        return widths;
    }

    private float calculateTotalWidth(float[] columnWidths) {
        float total = 0;
        for (float width : columnWidths) {
            total += width;
        }
        return total;
    }

    private int calculateTotalPages(int rowCount, int maxRowsPerPage) {
        if (maxRowsPerPage <= 0) return 1;
        return (int) Math.ceil((double) rowCount / maxRowsPerPage);
    }
}
