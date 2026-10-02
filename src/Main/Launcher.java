package Main;

import Main.UI.SimulatorUI;
import Main.UI.IPCCoreCommunicator;
import Main.UI.MockCoreCommunicator;

import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Master Launcher for the Nuvoton MS51FB9AE 3-Process Simulator.
 *
 * <h3>Architecture:</h3>
 * <pre>
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │                   PROCESS 1: UI PROCESS                     │
 *   │  - Swing Graphical User Interface                          │
 *   │  - IPCCoreCommunicator                                      │
 *   │  - Background Listener Thread (reads Core stdout)          │
 *   └───────────────┬─────────────────────────────▲───────────────┘
 *                   │ stdin (Commands)            │ stdout (Snapshots)
 *                   ▼                             │
 *   ┌─────────────────────────────────────────────┴───────────────┐
 *   │                  PROCESS 2: CORE PROCESS                    │
 *   │  - 8051 CPU Engine (Registers, PC, SP, PSW, RAM)           │
 *   │  - Memory, Stack, and Queue subsystem                      │
 *   │  - Command Execution Loop & Worker                         │
 *   └───────────────────────────────┬─────────────────────────────┘
 *                                   │ stderr (Serialized LogMessages)
 *                                   ▼
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │                 PROCESS 3: LOGGER PROCESS                   │
 *   │  - Dedicated log queue and processing thread                │
 *   │  - Formats EXECUTION, ERROR, and SYSTEM events              │
 *   │  - Writes to simulator.log and console                     │
 *   └─────────────────────────────────────────────────────────────┘
 * </pre>
 */
public class Launcher {

    private static Process coreProcess;
    private static Process loggerProcess;
    private static Thread pipeThread;
    private static Thread loggerConsoleThread;

    public static void main(String[] args) {
        // Check for standalone mode flag
        if (args.length > 0 && "--standalone".equals(args[0])) {
            startStandaloneUI();
            return;
        }

        startThreeProcessSystem();
    }

    /**
     * Starts the full 3-process architecture using real OS processes.
     */
    public static void startThreeProcessSystem() {
        System.out.println("=================================================");
        System.out.println("  Launching Nuvoton MS51FB9AE 3-Process Simulator");
        System.out.println("=================================================");

        String javaBin = getJavaBinary();
        String classpath = System.getProperty("java.class.path");

        try {
            // 1. Spawn Process 3: Logger Process
            System.out.println("[Launcher] Starting Process 3: Logger Process...");
            ProcessBuilder loggerBuilder = new ProcessBuilder(
                    javaBin, "-cp", classpath, "Main.Logger.LoggerProcess", "--ipc"
            );
            loggerProcess = loggerBuilder.start();

            // Pump Logger stdout to launcher console so logs are visible
            loggerConsoleThread = new Thread(() -> {
                try (InputStream in = loggerProcess.getInputStream()) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        System.out.write(buf, 0, len);
                        System.out.flush();
                    }
                } catch (IOException ignored) {
                }
            }, "Logger-Console-Pump");
            loggerConsoleThread.setDaemon(true);
            loggerConsoleThread.start();

            // 2. Spawn Process 2: Core Process
            System.out.println("[Launcher] Starting Process 2: Core Process...");
            ProcessBuilder coreBuilder = new ProcessBuilder(
                    javaBin, "-cp", classpath, "Main.core.CoreProcess", "--ipc"
            );
            coreProcess = coreBuilder.start();

            // 3. Pipe Core's stderr (log stream) to Logger's stdin
            pipeThread = new Thread(() -> {
                try (InputStream coreErr = coreProcess.getErrorStream();
                     OutputStream loggerIn = loggerProcess.getOutputStream()) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = coreErr.read(buf)) != -1) {
                        loggerIn.write(buf, 0, len);
                        loggerIn.flush();
                    }
                } catch (IOException ignored) {
                }
            }, "Core-to-Logger-Pipe");
            pipeThread.setDaemon(true);
            pipeThread.start();

            // 4. Register Shutdown Hook for graceful process cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(Launcher::shutdownChildProcesses, "Shutdown-Hook"));

            // 5. Start Process 1: UI in current JVM connected via IPCCoreCommunicator
            System.out.println("[Launcher] Starting Process 1: UI Process (IPC Connected)...");
            SwingUtilities.invokeLater(() -> {
                SimulatorUI ui = new SimulatorUI();
                IPCCoreCommunicator communicator = new IPCCoreCommunicator(coreProcess, ui);
                ui.setCommunicator(communicator);
                communicator.startListening();

                // On window closing, shut down communicator and child processes
                ui.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        try {
                            communicator.sendCommand("SHUTDOWN");
                            communicator.stopListening();
                        } catch (Exception ignored) {
                        }
                        shutdownChildProcesses();
                    }
                });

                // Request initial snapshot from core
                communicator.sendCommand("GET_STATE");

                ui.setVisible(true);
                System.out.println("[Launcher] All 3 processes initialized and communicating via IPC.");
            });

        } catch (Exception e) {
            System.err.println("[Launcher] Failed to start 3-process architecture: " + e.getMessage());
            e.printStackTrace();
            System.err.println("[Launcher] Falling back to standalone UI mode...");
            startStandaloneUI();
        }
    }

    /**
     * Starts UI in standalone mode with Mock communicator.
     */
    public static void startStandaloneUI() {
        SwingUtilities.invokeLater(() -> {
            SimulatorUI ui = new SimulatorUI();
            ui.setCommunicator(new MockCoreCommunicator(ui));
            ui.setVisible(true);
            System.out.println("[UI Process] Running in standalone mock mode.");
        });
    }

    /**
     * Cleanly shuts down child processes.
     */
    public static void shutdownChildProcesses() {
        if (coreProcess != null && coreProcess.isAlive()) {
            try {
                coreProcess.destroy();
                if (!coreProcess.waitFor(1, TimeUnit.SECONDS)) {
                    coreProcess.destroyForcibly();
                }
            } catch (Exception ignored) {
            }
        }

        if (loggerProcess != null && loggerProcess.isAlive()) {
            try {
                loggerProcess.destroy();
                if (!loggerProcess.waitFor(1, TimeUnit.SECONDS)) {
                    loggerProcess.destroyForcibly();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static String getJavaBinary() {
        String javaHome = System.getProperty("java.home");
        File binDir = new File(javaHome, "bin");
        File javaExe = new File(binDir, "java.exe");
        if (javaExe.exists()) {
            return javaExe.getAbsolutePath();
        }
        File java = new File(binDir, "java");
        if (java.exists()) {
            return java.getAbsolutePath();
        }
        return "java";
    }
}
