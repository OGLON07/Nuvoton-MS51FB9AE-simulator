import java.io.*;

/**
 * UI stand-in that simply stays alive, blocked on its Core pipe, so a test can
 * kill it abruptly (SIGKILL) and verify the rest of the system shuts down by itself.
 */
public class UiIdleClient {
    public static void main(String[] args) throws Exception {
        System.err.println("[UiIdleClient] alive, pid=" + ProcessHandle.current().pid());
        InputStream in = new FileInputStream(FileDescriptor.in);
        while (in.read() != -1) { /* blocks in read(); no sleeping */ }
        System.err.println("[UiIdleClient] Core closed the pipe, exiting");
    }
}
