import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * Reversibly changes only SQLite's 16-byte header signature.
 *
 * The live file is restored BEFORE SQLite opens it and only sealed AFTER every
 * SQLite connection has closed. Never rewrite the header on an open database.
 * This is an accidental-open deterrent, not encryption or access protection.
 */
public final class WareStatFileHeader {
    private static final byte[] SQLITE = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WARESTAT = "WareStat DB v01!".getBytes(StandardCharsets.US_ASCII);

    private WareStatFileHeader() {}

    public static boolean isSealed(Path path) throws IOException {
        return Arrays.equals(readHeader(path), WARESTAT);
    }

    public static boolean isSQLite(Path path) throws IOException {
        return Arrays.equals(readHeader(path), SQLITE);
    }

    /** True only if the first 16 bytes are a partially written WareStat/SQLite signature. */
    public static boolean isInterruptedSignature(Path path) throws IOException {
        return isInterruptedHeader(readHeader(path));
    }

    public static void restoreForSQLite(Path path) throws IOException {
        if (!Files.exists(path)) return;
        byte[] header = readHeader(path);
        if (Arrays.equals(header, SQLITE)) return;
        if (!Arrays.equals(header, WARESTAT) && !isInterruptedHeader(header))
            throw new IOException("Unknown WareStat/SQLite database signature: " + path);
        replaceHeader(path, SQLITE);
    }

    public static void seal(Path path) throws IOException {
        byte[] header = readHeader(path);
        if (Arrays.equals(header, WARESTAT)) return;
        if (!Arrays.equals(header, SQLITE))
            throw new IOException("Not a valid SQLite database header: " + path);
        // A nonempty WAL or a journal may contain uncheckpointed transactions.
        // In that case leave the SQLite header intact for automatic recovery.
        Path wal = Path.of(path.toString() + "-wal");
        Path journal = Path.of(path.toString() + "-journal");
        if ((Files.exists(wal) && Files.size(wal) > 0) || Files.exists(journal))
            throw new IOException("Database still has an active WAL/journal: " + path);
        replaceHeader(path, WARESTAT);
    }

    /** Copy an unopened .wsbq to a private, readable staging file. The source is unchanged. */
    public static Path copyReadable(Path original, Path workDirectory) throws IOException {
        Path staging = workDirectory.resolve("readable-" + java.util.UUID.randomUUID() + ".wsbq");
        Files.copy(original, staging, StandardCopyOption.COPY_ATTRIBUTES);
        try {
            restoreForSQLite(staging);
            return staging;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(staging);
            throw e;
        }
    }

    // A crash during a 16-byte signature write can leave a partial mixture.
    // Accept only a byte-for-byte mixture of our two fixed signatures.
    private static boolean isInterruptedHeader(byte[] candidate) {
        boolean warestatByte = false;
        for (int i = 0; i < SQLITE.length; i++) {
            if (candidate[i] != SQLITE[i] && candidate[i] != WARESTAT[i])
                return false;
            if (candidate[i] == WARESTAT[i] && candidate[i] != SQLITE[i])
                warestatByte = true;
        }
        return warestatByte;
    }

    private static byte[] readHeader(Path path) throws IOException {
        if (!Files.isRegularFile(path))
            throw new IOException("Database not found: " + path);
        try (FileChannel c = FileChannel.open(path, java.nio.file.StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(16);
            while (buffer.hasRemaining()) {
                if (c.read(buffer) < 0)
                    throw new IOException("Truncated database header: " + path);
            }
            return buffer.array();
        }
    }

    private static void replaceHeader(Path path, byte[] header) throws IOException {
        if (header.length != 16) throw new IllegalArgumentException("Invalid SQLite header size");
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.seek(0L);
            file.write(header);
            file.getChannel().force(true);
        }
    }
}
