import java.util.concurrent.CountDownLatch;

/**
 * Windows Native Image launcher.
 *
 * MainWindow.main() schedules Swing startup on the EDT and returns immediately.
 * A regular JVM remains alive for the AWT threads, but the Windows Native Image
 * runtime can begin VM teardown while Win32GraphicsEnvironment is still
 * initializing. Keeping the primary thread alive avoids that race.
 *
 * WareStat uses JFrame.EXIT_ON_CLOSE/System.exit(0), so normal application exit
 * still terminates the process.
 */
public final class WareStatNativeLauncher {
    private WareStatNativeLauncher() {}

    public static void main(String[] args) throws Exception {
        MainWindow.main(args);
        new CountDownLatch(1).await();
    }
}
