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

        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    SimulatorUI ui = new SimulatorUI();

                    if (ipcMode) {
                        IPCCoreCommunicator communicator = new IPCCoreCommunicator(ui);
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
                        System.err.println("[UI Process] Started in POSIX IPC mode.");
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