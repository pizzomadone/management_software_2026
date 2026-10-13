import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Versioned WareStat binary archive envelope.
 *
 * The compressed ZIP payload is stream-masked so generic ZIP and SQLite
 * applications do not recognize the .wsbq file. This is a proprietary file
 * format, NOT encryption or protection against deliberate reverse engineering.
 */
public final class WareStatArchiveFormat {
    private static final byte[] MAGIC = {'W', 'S', 'B', 'Q', '\r', '\n', 0x1a, '\n'};
    private static final int VERSION = 1;
    private static final int DIGEST_BYTES = 32;
    private static final int HEADER_BYTES = MAGIC.length + 4 + 8 + 8 + DIGEST_BYTES;
    private static final long MAX_PAYLOAD_BYTES = 1024L * 1024L * 1024L;
    private static final long MASK_DOMAIN = 0x575342512D415243L;

    private WareStatArchiveFormat() {}

    public static void wrap(Path zipFile, Path destination) throws IOException {
        long size = Files.size(zipFile);
        if (size <= 0 || size > MAX_PAYLOAD_BYTES)
            throw new IOException("Unsupported WareStat archive size");
        byte[] digest = digest(zipFile);
        long seed = new SecureRandom().nextLong();

        try (InputStream input = new BufferedInputStream(Files.newInputStream(zipFile));
             DataOutputStream output = new DataOutputStream(
                 new BufferedOutputStream(Files.newOutputStream(destination,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {
            output.write(MAGIC);
            output.writeInt(VERSION);
            output.writeLong(size);
            output.writeLong(seed);
            output.write(digest);
            transferMasked(input, output, size, seed, null);
        }
    }

    public static void unwrap(Path archive, Path destination) throws IOException {
        long fileSize = Files.size(archive);
        if (fileSize < HEADER_BYTES)
            throw new IOException("Invalid WareStat archive header");

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(archive)))) {
            byte[] magic = new byte[MAGIC.length];
            input.readFully(magic);
            if (!Arrays.equals(magic, MAGIC))
                throw new IOException("Invalid WareStat archive signature");
            int version = input.readInt();
            if (version != VERSION)
                throw new IOException("Unsupported WareStat archive version: " + version);
            long size = input.readLong();
            long seed = input.readLong();
            byte[] expectedDigest = new byte[DIGEST_BYTES];
            input.readFully(expectedDigest);

            if (size <= 0 || size > MAX_PAYLOAD_BYTES || fileSize - HEADER_BYTES != size)
                throw new IOException("Invalid WareStat archive payload length");
            MessageDigest actualDigest = newDigest();
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(destination,
                     StandardOpenOption.CREATE_NEW))) {
                transferMasked(input, output, size, seed, actualDigest);
            } catch (IOException | RuntimeException error) {
                Files.deleteIfExists(destination);
                throw error;
            }
            if (!MessageDigest.isEqual(expectedDigest, actualDigest.digest())) {
                Files.deleteIfExists(destination);
                throw new IOException("WareStat archive integrity check failed");
            }
        }
    }

    private static void transferMasked(InputStream input, OutputStream output, long size,
            long seed, MessageDigest digest) throws IOException {
        SplittableRandom maskGenerator = new SplittableRandom(seed ^ MASK_DOMAIN);
        long maskWord = 0;
        int bytesRemaining = 0;
        byte[] buffer = new byte[65536];
        while (size > 0) {
            int count = input.read(buffer, 0, (int) Math.min(size, buffer.length));
            if (count < 0) throw new EOFException("Truncated WareStat archive");
            if (count == 0) continue;
            for (int i = 0; i < count; i++) {
                if (bytesRemaining == 0) {
                    maskWord = maskGenerator.nextLong();
                    bytesRemaining = 8;
                }
                buffer[i] ^= (byte) maskWord;
                maskWord >>>= 8;
                bytesRemaining--;
            }
            if (digest != null) digest.update(buffer, 0, count);
            output.write(buffer, 0, count);
            size -= count;
        }
    }

    private static byte[] digest(Path path) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] buffer = new byte[65536];
            for (int read; (read = input.read(buffer)) != -1;)
                digest.update(buffer, 0, read);
        }
        return digest.digest();
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
