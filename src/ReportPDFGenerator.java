/*
 * Copyright © 2026 WareStat (www.warestat.com). All rights reserved.
 *
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or reverse engineering
 * is strictly prohibited.
 *
 * See LICENSE.txt for full terms.
 */

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
        try(PdfWriter pdf=new PdfWriter(reportTitle,tableModel.getColumnCount()>6)) {
            if(subtitle!=null && !subtitle.isBlank())pdf.paragraph(subtitle,false);
            int columns=tableModel.getColumnCount();
            if(columns>0) {
                String[] headers=new String[columns];float[] weights=new float[columns];
                for(int i=0;i<columns;i++) {headers[i]=tableModel.getColumnName(i);weights[i]=1;}
                pdf.startTable(headers,weights);
                for(int row=0;row<tableModel.getRowCount();row++) {
                    String[] values=new String[columns];
                    for(int col=0;col<columns;col++)values[col]=java.util.Objects.toString(tableModel.getValueAt(row,col),"");
                    pdf.row(values);
                }
            }
            if(tableModel.getRowCount()==0)pdf.paragraph("No data for the selected criteria.",false);
            pdf.save(outputFile);
        } catch(IllegalArgumentException e) {throw new IOException("Unable to export report: "+e.getMessage(),e);}
    }
}
