package Main.UI;

import Main.IPC.IPCProtocol;
import Main.core.command.Command;
import Main.core.command.CommandMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

/**
 * IPC-based communicator that sends commands to the Core process
 * via its stdin pipe and receives responses from its stdout pipe.
 *
 * <h3>Threading</h3>
 * <ul>
 *   <li>Commands are sent on the Swing EDT (via button clicks)</li>
 *   <li>A background listener thread reads Core responses and
 *       dispatches UI updates via SwingUtilities.invokeLater</li>
 * </ul>
 *
 * <p>The UI never directly executes CPU instructions. All CPU
 * interaction goes through this IPC channel.</p>
 */
public class IPCCoreCommunicator implements CoreCommunicator {

    private final PrintWriter toCore;
    private final BufferedReader fromCore;
    private final SimulatorUI ui;
    private volatile boolean running;
    private Thread listenerThread;

    /**
     * Creates an IPC communicator using standard input and output streams.
     * Used when the UI process is launched as an independent process by
     * the native POSIX launcher.
     *
     * @param ui the UI instance to receive state updates
     */
    public IPCCoreCommunicator(SimulatorUI ui) {
        this(new InputStreamReader(System.in),
             new OutputStreamWriter(System.out),
             ui);
    }

    /**
     * Creates an IPC communicator with custom Reader and Writer streams.
     */
    public IPCCoreCommunicator(Reader in, Writer out, SimulatorUI ui) {
        this.toCore = new PrintWriter(out, true);
        this.fromCore = new BufferedReader(in);
        this.ui = ui;
        this.running = false;
    }

    /**
     * Creates a communicator connected to a Core child process (backward compatibility).
     *
     * @param coreProcess the Core child process
     * @param ui          the UI instance to receive state updates
     */
    public IPCCoreCommunicator(Process coreProcess, SimulatorUI ui) {
        this(new InputStreamReader(coreProcess.getInputStream()),
             new OutputStreamWriter(coreProcess.getOutputStream()),
             ui);
    }

    /**
     * Starts the background listener thread that reads Core responses.
     */
    public void startListening() {
        if (running) return;
        running = true;

        listenerThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = fromCore.readLine()) != null) {
                    handleResponse(line.trim());
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[UI IPC] Listener error: " + e.getMessage());
                }
            }
            running = false;
        }, "UI-IPC-Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Stops the listener thread.
     */
    public void stopListening() {
        running = false;
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }

    @Override
    public void sendCommand(String command) {
        CommandMessage msg;

        // Map UI command strings to CommandMessage objects
        switch (command.toUpperCase()) {
            case "STEP":
                msg = new CommandMessage(Command.STEP);
                break;
            case "RUN":
                msg = new CommandMessage(Command.RUN);
                break;
            case "PAUSE":
                msg = new CommandMessage(Command.PAUSE);
                break;
            case "RESET":
                msg = new CommandMessage(Command.RESET);
                break;
            case "GET_STATE":
                msg = new CommandMessage(Command.GET_STATE);
                break;
            case "SHUTDOWN":
                msg = new CommandMessage(Command.SHUTDOWN);
                break;
            case "LOAD":
                msg = new CommandMessage(Command.LOAD, getDefaultDemoProgram());
                break;
            case "LOAD_STACK_DEMO":
                msg = new CommandMessage(Command.LOAD, getStackDemoProgram());
                break;
            case "LOAD_QUEUE_DEMO":
                msg = new CommandMessage(Command.LOAD, getQueueDemoProgram());
                break;
            default:
                System.err.println("[UI IPC] Unknown command: " + command);
                return;
        }

        String serialized = IPCProtocol.serializeCommand(msg);
        toCore.println(serialized);
    }

    @Override
    public void loadProgram(String programData) {
        // Parse comma-separated hex/decimal bytes
        if (programData == null || programData.trim().isEmpty()) return;
        try {
            String[] parts = programData.trim().split("[,\\s]+");
            int[] program = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i].trim();
                if (p.startsWith("0x") || p.startsWith("0X")) {
                    program[i] = Integer.parseInt(p.substring(2), 16);
                } else {
                    program[i] = Integer.parseInt(p);
                }
            }
            CommandMessage msg = new CommandMessage(Command.LOAD, program);
            toCore.println(IPCProtocol.serializeCommand(msg));
        } catch (Exception e) {
            System.err.println("[UI IPC] Failed to parse program data: " + e.getMessage());
        }
    }

    /**
     * Handles a response line from the Core process.
     */
    private void handleResponse(String line) {
        if (line == null || line.isEmpty()) return;
        if (!line.startsWith(IPCProtocol.RSP_PREFIX)) return;

        IPCProtocol.ParsedResponse resp = IPCProtocol.parseResponse(line);
        if (resp == null) return;

        if (!resp.success && resp.errorMessage != null) {
            ui.onErrorReceived(resp.errorMessage);
            return;
        }

        if (resp.snapshot != null) {
            ui.onSnapshotReceived(resp.snapshot);
        }
    }

    // ==================== Demo programs ====================

    /** Basic demo: MOV A,#0x42, ADD A,#0x05, HALT */
    private int[] getDefaultDemoProgram() {
        return new int[]{ 0x74, 0x42, 0x24, 0x05, 0xFF };
    }

    /**
     * Stack demo: MOV A,#0x42, PUSH A, MOV A,#0x00, POP A, HALT
     */
    private int[] getStackDemoProgram() {
        return new int[]{ 0x74, 0x42, 0xC0, 0x74, 0x00, 0xD0, 0xFF };
    }

    /**
     * Queue demo: Initialize queue metadata and enqueue three values.
     */
    private int[] getQueueDemoProgram() {
        return new int[]{
            // Init: head=0, tail=0, count=0
            0x74, 0x00, 0xF5, 0x30, 0xF5, 0x31, 0xF5, 0x32,
            // Enqueue 0xAA at buffer[0]
            0x74, 0xAA, 0xF5, 0x40,
            0x74, 0x01, 0xF5, 0x31, 0xF5, 0x32,
            // Enqueue 0xBB at buffer[1]
            0x74, 0xBB, 0xF5, 0x41,
            0x74, 0x02, 0xF5, 0x31, 0xF5, 0x32,
            // Enqueue 0xCC at buffer[2]
            0x74, 0xCC, 0xF5, 0x42,
            0x74, 0x03, 0xF5, 0x31, 0xF5, 0x32,
            // HALT
            0xFF
        };
    }
}
