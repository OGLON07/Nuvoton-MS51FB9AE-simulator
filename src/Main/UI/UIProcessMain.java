package Main.UI;

import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Entry point for the UI Process.
 *
 * Can be run in two modes:
 * <ul>
 *   <li>{@code --ipc}: Communicates with Core process via standard stream pipes (POSIX / Launcher)</li>
 *   <li>Default: Standalone mock mode with MockCoreCommunicator</li>
 * </ul>
 */
public class UIProcessMain {

    public static void main(String[] args) {
        boolean ipcMode = args.length > 0 && "--ipc".equals(args[0]);

        // In IPC mode fd 0 / fd 1 are the pipes to the Core (set up by the native
        // POSIX launcher). Grab them now, then point System.out at stderr so that
        // no println() anywhere in the UI can corrupt the protocol stream.
        final java.io.Reader fromCore;
        final java.io.Writer toCore;
        if (ipcMode) {
            fromCore = new java.io.InputStreamReader(new java.io.FileInputStream(java.io.FileDescriptor.in));
            toCore = new java.io.OutputStreamWriter(new java.io.FileOutputStream(java.io.FileDescriptor.out));
            System.setOut(System.err);
        } else {
            fromCore = null;
            toCore = null;
        }

        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    SimulatorUI ui = new SimulatorUI();

                    if (ipcMode) {
                        IPCCoreCommunicator communicator = new IPCCoreCommunicator(fromCore, toCore, ui);
                        ui.setCommunicator(communicator);
                        communicator.startListening();

                        ui.addWindowListener(new WindowAdapter() {
                            @Override
                            public void windowClosing(WindowEvent e) {
                                try {
                                    communicator.sendCommand("SHUTDOWN");
                                    communicator.stopListening();
                                } catch (Exception ignored) {
                                }
                            }
                        });

                        // Request initial CPU snapshot from Core
                        communicator.sendCommand("GET_STATE");
                        System.err.println("[UI Process] Started in POSIX pipe IPC mode (pid " + ProcessHandle.current().pid() + ").");
                    } else {
                        CoreCommunicator mockCommunicator = new MockCoreCommunicator(ui);
                        ui.setCommunicator(mockCommunicator);
                        System.err.println("[UI Process] Started in standalone mock mode.");
                    }

                    ui.setVisible(true);
                } catch (java.awt.HeadlessException he) {
                    System.err.println("[UI Process] Graphical display unavailable in headless terminal.");
                    System.err.println("[UI Process] Run with WSLg / X11 (export DISPLAY=:0) to display Swing GUI.");
                }
            });
        } catch (Exception e) {
            System.err.println("[UI Process] Failed to initialize UI: " + e.getMessage());
        }
    }
}