import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Paginated, lossless text layout shared by invoices and table reports. */
public final class PdfWriter implements AutoCloseable {
    private static final float MARGIN=40, BOTTOM=50, SIZE=9, LINE=13;
    private final PDDocument document=new PDDocument();
    private final PDFont regular, bold;
    private final PDRectangle paper;
    private final String title;
    private final String pageLabel;
    private String continuationTitle="";
    private PDPageContentStream stream;
    private float y;
    private String[] headers;
    private float[] widths;

    public PdfWriter(String title, boolean landscape) throws IOException {
        this(title,landscape,"Page");
    }

    public PdfWriter(String title, boolean landscape, String pageLabel) throws IOException {
        this.title=Objects.toString(title,"");
        this.pageLabel=Objects.toString(pageLabel,"Page").isBlank()?"Page":pageLabel;
        paper=landscape?new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()):PDRectangle.A4;
        try {
            regular=font("DejaVuSans.ttf"); bold=font("DejaVuSans-Bold.ttf");
            newPage();
        } catch (IOException | RuntimeException e) { document.close(); throw e; }
    }
    public void setContinuationTitle(String value) {
        continuationTitle=Objects.toString(value,"");
    }

    private PDFont font(String name) throws IOException {
        try(InputStream in=openFont(name)) {
            return PDType0Font.load(document,in);
        }
    }

    private InputStream openFont(String name) throws IOException {
        InputStream packaged = PdfWriter.class.getResourceAsStream("/fonts/" + name);
        if (packaged != null) return packaged;

        // Eclipse/manual launches may omit the resource folder from the classpath.
        Path sourceFont = Path.of("resources", "fonts", name);
        if (Files.isRegularFile(sourceFont) && Files.isReadable(sourceFont))
            return Files.newInputStream(sourceFont);

        // PDFBox bundles an embeddable Unicode font, also available in an incomplete app package.
        // This fallback uses a regular face for both weights; it never replaces unsupported characters.
        InputStream fallback = PDDocument.class.getResourceAsStream(
            "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf");
        if (fallback != null) return fallback;
        throw new IOException("PDF fonts are unavailable. Restore resources/fonts and the PDFBox library.");
    }
    private float measure(String text,PDFont font,float size) throws IOException {
        try {return font.getStringWidth(text)*size/1000;}
        catch(IllegalArgumentException e) {throw new IOException("The PDF font does not support a character in: "+text,e);}
    }
    private void text(String value,float x,float baseline,PDFont font,float size) throws IOException {
        // Validate before beginning the text object, so errors cannot produce a partial export.
        measure(value,font,size);
        stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(x,baseline);stream.showText(value);stream.endText();
    }
    private void newPage() throws IOException {
        if(stream!=null)stream.close();
        PDPage page=new PDPage(paper);document.addPage(page);
        stream=new PDPageContentStream(document,page);
        text("WareStat | "+pageLabel+" "+document.getNumberOfPages(),MARGIN,28,regular,8);
        y=paper.getHeight()-MARGIN;
        String pageTitle=!title.isBlank()?title:(document.getNumberOfPages()>1?continuationTitle:"");
        if(!pageTitle.isBlank()) {
            for(String line:wrap(pageTitle,paper.getWidth()-2*MARGIN,bold,14)) {
                if(y<BOTTOM+60) throw new IOException("Report title is too long");
                text(line,MARGIN,y,bold,14);y-=19;
            }
            y-=12;
        }
        if(headers!=null)header();
    }
    private java.util.List<String> wrap(String value,float width,PDFont font,float size) throws IOException {
        java.util.List<String> lines=new ArrayList<>();
        String normalized=Objects.toString(value,"").replace("\r\n","\n").replace('\r','\n').replace('\t',' ');
        for(String paragraph:normalized.split("\n",-1)) {
            StringBuilder current=new StringBuilder();
            for(int offset=0;offset<paragraph.length();) {
                int code=paragraph.codePointAt(offset);offset+=Character.charCount(code);
                if(Character.isISOControl(code)) throw new IOException("Unsupported control character in PDF text");
                String next=new String(Character.toChars(code));
                if(measure(current.toString()+next,font,size)>width) {
                    if(current.isEmpty()) throw new IOException("PDF column is too narrow for its content");
                    int space=current.lastIndexOf(" ");
                    if(space>0 && space>current.length()/2) {
                        lines.add(current.substring(0,space));
                        current.delete(0,space+1);
                    } else {lines.add(current.toString());current.setLength(0);}
                }
                current.append(next);
            }
            lines.add(current.toString());
        }
        return lines;
    }
    public void paragraph(String value,boolean strong) throws IOException {
        if(headers!=null)y-=12;
        headers=null; widths=null;
        for(String line:wrap(value,paper.getWidth()-2*MARGIN,strong?bold:regular,SIZE)) {
            if(y-LINE<BOTTOM)newPage();
            text(line,MARGIN,y,strong?bold:regular,SIZE);y-=LINE;
        }
        y-=6;
    }
    public void logo(byte[] bytes) throws IOException {
        if(bytes==null || bytes.length==0)return;
        PDImageXObject logo=PDImageXObject.createFromByteArray(document,bytes,"Company logo");
        float scale=Math.min(110f/logo.getWidth(),55f/logo.getHeight());
        float h=logo.getHeight()*scale;
        if(y-h<BOTTOM)newPage();
        stream.drawImage(logo,MARGIN,y-h,logo.getWidth()*scale,h);y-=h+12;
    }
    private void rightText(String value,float right,float baseline,PDFont font,float size) throws IOException {
        text(value,right-measure(value,font,size),baseline,font,size);
    }
    private java.util.List<String> wrappedLines(java.util.List<String> values,float width,PDFont font,float size) throws IOException {
        java.util.List<String> result=new ArrayList<>();
        for(String value:values) {
            if(value==null || value.isBlank())continue;
            result.addAll(wrap(value,width,font,size));
        }
        return result;
    }
    public void invoiceHeader(byte[] logoBytes,String documentTitle,String number,
            String dateLabel,String date,String statusLabel,String status,String currencyLabel,String currency,
            String fromLabel,String fromName,java.util.List<String> fromLines,
            String billToLabel,String billToName,java.util.List<String> billToLines) throws IOException {
        headers=null;widths=null;
        float top=y;
        if(logoBytes!=null && logoBytes.length>0) {
            try {
                PDImageXObject logo=PDImageXObject.createFromByteArray(document,logoBytes,"Company logo");
                float scale=Math.min(112f/logo.getWidth(),50f/logo.getHeight());
                float h=logo.getHeight()*scale;
                stream.drawImage(logo,MARGIN,top-h,logo.getWidth()*scale,h);
            } catch(IOException | IllegalArgumentException invalidLogo) {
                // A damaged historical logo must never prevent the invoice itself from being exported.
            }
        }
        float right=paper.getWidth()-MARGIN;
        rightText(documentTitle,right,top-2,bold,22);
        if(number!=null && !number.isBlank()) rightText(number,right,top-24,bold,11);
        String dateText=dateLabel+": "+date;
        String currencyText=currencyLabel+": "+currency;
        rightText(dateText,right,top-41,regular,8.5f);
        rightText(currencyText,right,top-54,regular,8.5f);
        String statusText=statusLabel+": "+status;
        float statusWidth=measure(statusText,bold,8.5f)+12;
        stream.setNonStrokingColor(java.awt.Color.LIGHT_GRAY);
        stream.addRect(right-statusWidth,top-72,statusWidth,13);stream.fill();
        stream.setNonStrokingColor(java.awt.Color.BLACK);
        rightText(statusText,right-6,top-68,bold,8.5f);

        y=top-88;
        stream.setStrokingColor(java.awt.Color.LIGHT_GRAY);
        stream.moveTo(MARGIN,y);stream.lineTo(right,y);stream.stroke();
        y-=18;

        float gap=24, col=(paper.getWidth()-2*MARGIN-gap)/2, rightX=MARGIN+col+gap;
        text(fromLabel,MARGIN,y,bold,9);text(billToLabel,rightX,y,bold,9);y-=16;

        java.util.List<String> leftName=wrap((fromName==null||fromName.isBlank())?"—":fromName,col,bold,10);
        java.util.List<String> rightName=wrap((billToName==null||billToName.isBlank())?"—":billToName,col,bold,10);
        int nameRows=Math.max(leftName.size(),rightName.size());
        for(int row=0;row<nameRows;row++) {
            if(y-LINE<BOTTOM+70)newPage();
            if(row<leftName.size())text(leftName.get(row),MARGIN,y,bold,10);
            if(row<rightName.size())text(rightName.get(row),rightX,y,bold,10);
            y-=LINE;
        }
        y-=2;

        java.util.List<String> left=wrappedLines(fromLines,col,regular,SIZE);
        java.util.List<String> rightLines=wrappedLines(billToLines,col,regular,SIZE);
        int rows=Math.max(left.size(),rightLines.size());
        for(int row=0;row<rows;row++) {
            if(y-LINE<BOTTOM+70)newPage();
            if(row<left.size())text(left.get(row),MARGIN,y,regular,SIZE);
            if(row<rightLines.size())text(rightLines.get(row),rightX,y,regular,SIZE);
            y-=LINE;
        }
        y-=10;
        stream.setStrokingColor(java.awt.Color.LIGHT_GRAY);
        stream.moveTo(MARGIN,y);stream.lineTo(right,y);stream.stroke();
        y-=16;
    }
    public void totals(String[] labels,String[] values) throws IOException {
        if(labels.length==0 || labels.length!=values.length)throw new IOException("Invalid invoice totals");
        headers=null;widths=null;
        float boxWidth=230, right=paper.getWidth()-MARGIN, left=right-boxWidth;
        float needed=labels.length*18+16;
        if(y-needed<BOTTOM)newPage();
        stream.setStrokingColor(java.awt.Color.LIGHT_GRAY);
        stream.moveTo(left,y);stream.lineTo(right,y);stream.stroke();y-=15;
        for(int i=0;i<labels.length;i++) {
            boolean finalRow=i==labels.length-1;
            PDFont font=finalRow?bold:regular;float size=finalRow?11:9;
            text(labels[i],left+6,y,font,size);
            rightText(values[i],right-6,y,font,size);
            y-=finalRow?19:17;
        }
        stream.moveTo(left,y+7);stream.lineTo(right,y+7);stream.stroke();y-=8;
    }
    public void note(String value) throws IOException {
        if(value==null || value.isBlank())return;
        headers=null;widths=null;
        y-=8;
        stream.setStrokingColor(java.awt.Color.LIGHT_GRAY);
        stream.moveTo(MARGIN,y);stream.lineTo(paper.getWidth()-MARGIN,y);stream.stroke();y-=12;
        for(String line:wrap(value,paper.getWidth()-2*MARGIN,regular,7.5f)) {
            if(y-11<BOTTOM)newPage();
            text(line,MARGIN,y,regular,7.5f);y-=11;
        }
        y-=4;
    }

    public void startTable(String[] labels,float[] weights) throws IOException {
        if(labels.length==0 || labels.length!=weights.length)throw new IOException("Invalid report columns");
        headers=labels.clone();widths=new float[weights.length];float sum=0;
        for(float weight:weights)sum+=weight;
        for(int i=0;i<weights.length;i++) widths[i]=(paper.getWidth()-2*MARGIN)*weights[i]/sum;
        if(y<BOTTOM+70)newPage();else header();
    }
    private void header() throws IOException {
        java.util.List<java.util.List<String>> cells=new ArrayList<>();int lines=1;
        for(int i=0;i<headers.length;i++) {var cell=wrap(headers[i],widths[i]-8,bold,SIZE);cells.add(cell);lines=Math.max(lines,cell.size());}
        float height=lines*LINE+8;
        if(y-height<BOTTOM+LINE)throw new IOException("Report headers leave no space for data");
        stream.setNonStrokingColor(java.awt.Color.LIGHT_GRAY);stream.addRect(MARGIN,y-height,paper.getWidth()-2*MARGIN,height);stream.fill();stream.setNonStrokingColor(java.awt.Color.BLACK);
        float x=MARGIN;
        for(int i=0;i<headers.length;i++) {
            for(int j=0;j<cells.get(i).size();j++)text(cells.get(i).get(j),x+4,y-12-j*LINE,bold,SIZE);
            x+=widths[i];
        }
        y-=height;
    }
    public void row(String... values) throws IOException {
        if(headers==null || values.length!=headers.length)throw new IOException("Incorrect PDF row shape");
        java.util.List<java.util.List<String>> cells=new ArrayList<>();int lines=1;
        for(int i=0;i<values.length;i++) {var cell=wrap(values[i],widths[i]-8,regular,SIZE);cells.add(cell);lines=Math.max(lines,cell.size());}
        if(y-(lines*LINE+8)<BOTTOM && lines*LINE+8<150)newPage();
        for(int line=0;line<lines;line++) {
            if(y-LINE-8<BOTTOM)newPage();
            float x=MARGIN;
            for(int col=0;col<values.length;col++) {
                if(line<cells.get(col).size())text(cells.get(col).get(line),x+4,y-12,regular,SIZE);
                x+=widths[col];
            }
            y-=LINE;
        }
        y-=8;stream.setStrokingColor(java.awt.Color.LIGHT_GRAY);stream.moveTo(MARGIN,y+3);stream.lineTo(paper.getWidth()-MARGIN,y+3);stream.stroke();
    }
    public void save(File file) throws IOException {
        if(stream!=null){stream.close();stream=null;}
        Path target=file.toPath().toAbsolutePath();Path temporary=Files.createTempFile(target.getParent(),"warestat-pdf-",".pdf");
        try {
            document.save(temporary.toFile());
            Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        } finally {Files.deleteIfExists(temporary);}
    }
    @Override public void close() throws IOException {
        try {if(stream!=null)stream.close();} finally {document.close();}
    }
}
