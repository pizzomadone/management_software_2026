import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Runs in a separate JVM with only the packaged application and PDFBox on its classpath. */
public class PdfPackagingProbe {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        try (PdfWriter pdf = new PdfWriter("PDF export: città, quantità, importi", false)) {
            pdf.paragraph("Łódź München São Paulo français", false);
            pdf.startTable(new String[]{"Prodotto", "Quantità", "Importo"}, new float[]{3, 1, 2});
            pdf.row("Łódź", "10", "1.234,56 €");
            pdf.save(output.toFile());
        }
        try (var document = Loader.loadPDF(output.toFile())) {
            String text = new PDFTextStripper().getText(document);
            for (String expected : new String[]{"città", "quantità", "Łódź", "München", "São Paulo", "français", "1.234,56 €"}) {
                if (!text.contains(expected)) throw new AssertionError("Lost PDF content: " + expected);
            }
            var resources = document.getPage(0).getResources();
            for (var name : resources.getFontNames()) {
                var font = resources.getFont(name);
                if (!font.isEmbedded() || !font.getName().contains(args[1]))
                    throw new AssertionError("Unexpected or non-embedded font: " + font.getName());
            }
        }
        System.out.println("PDF export verified with " + args[1]);
    }
}
