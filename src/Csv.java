import java.io.PrintWriter;
import java.io.IOException;

public final class Csv {
    private Csv() {}
    public static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    public static void finish(PrintWriter writer) throws IOException {
        writer.flush();
        if (writer.checkError()) throw new IOException("Unable to write the complete CSV file");
    }
}
