import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

/** Keep the desktop's live database owned by one process, including during restore. */
public final class ApplicationLock {
    private static FileChannel channel;
    private static FileLock lock;
    private ApplicationLock() {}
    public static synchronized void acquire() {
        if (lock != null && lock.isValid()) return;
        try {
            channel = FileChannel.open(AppConstants.getAppDataDirectory().resolve("warestat.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = channel.tryLock();
            if (lock == null) { channel.close(); throw new IOException("WareStat is already running with this database"); }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    // Normal exit: close and checkpoint SQLite, then seal its header.
                    DatabaseManager.getInstance().shutdownAndSeal();
                } catch (RuntimeException e) {
                    System.err.println("Unable to close WareStat database safely: " + e.getMessage());
                } finally {
                    try { lock.release(); channel.close(); } catch (IOException ignored) { }
                }
            }, "WareStat-database-shutdown"));
        } catch (IOException | java.nio.channels.OverlappingFileLockException e) {
            throw new IllegalStateException("Unable to open database: WareStat may already be running", e);
        }
    }
}
