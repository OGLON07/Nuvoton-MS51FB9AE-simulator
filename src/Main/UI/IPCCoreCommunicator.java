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

    /**
     * Default LOAD program (27 instructions). Uses every non-stack instruction and
     * changes every value in the CPU STATE panel (A, B, R0-R7, PSW, CY, AC, OV, PC)
     * plus data memory:
     *
     *   MOV A,#0x0F ; MOV R0..R7,#imm          -> R0-R7 = 01 02 03 05 08 0D 15 22
     *   ADD A,#0x01     A=10  AC=1             (carry out of bit 3)
     *   ADD A,#0xF0     A=00  CY=1             (carry out of bit 7)
     *   MOV A,#0x50 ; ADD A,#0x50  A=A0 OV=1   (signed overflow)
     *   SUBB A,#0xB0    A=F0  CY=1             (borrow)
     *   MOV A,#0x20 ; MOV B,#0x10 ; MUL AB     A=00 B=02 OV=1 (0x20*0x10=0x200)
     *   MOV A,#0x3C ; ANL A,#0x0F ; INC A ; DEC A      A=0C
     *   MOV 0x30,A ; MOV A,#0 ; MOV A,0x30     RAM[0x30]=0C, A=0C again
     *   NOP ; SJMP +2 (skips the dead MOV A,#0xFF) ; HALT
     */
    public static int[] getDefaultDemoProgram() {
        return new int[]{
            0x74, 0x0F,             // MOV A,#0x0F
            0x78, 0x01,             // MOV R0,#0x01
            0x79, 0x02,             // MOV R1,#0x02
            0x7A, 0x03,             // MOV R2,#0x03
            0x7B, 0x05,             // MOV R3,#0x05
            0x7C, 0x08,             // MOV R4,#0x08
            0x7D, 0x0D,             // MOV R5,#0x0D
            0x7E, 0x15,             // MOV R6,#0x15
            0x7F, 0x22,             // MOV R7,#0x22
            0x24, 0x01,             // ADD A,#0x01      -> A=0x10, AC=1
            0x24, 0xF0,             // ADD A,#0xF0      -> A=0x00, CY=1
            0x74, 0x50,             // MOV A,#0x50
            0x24, 0x50,             // ADD A,#0x50      -> A=0xA0, OV=1
            0x94, 0xB0,             // SUBB A,#0xB0     -> A=0xF0, CY=1 (borrow)
            0x74, 0x20,             // MOV A,#0x20
            0x75, 0xF0, 0x10,       // MOV B,#0x10
            0xA4,                   // MUL AB           -> A=0x00, B=0x02, OV=1
            0x74, 0x3C,             // MOV A,#0x3C
            0x54, 0x0F,             // ANL A,#0x0F      -> A=0x0C
            0x04,                   // INC A            -> 0x0D
            0x14,                   // DEC A            -> 0x0C
            0xF5, 0x30,             // MOV 0x30,A       -> RAM[0x30]=0x0C
            0x74, 0x00,             // MOV A,#0x00
            0xE5, 0x30,             // MOV A,0x30       -> A=0x0C (read back from RAM)
            0x00,                   // NOP
            0x80, 0x02,             // SJMP +2          (skips the next instruction)
            0x74, 0xFF,             // MOV A,#0xFF      (dead code - proves the jump worked)
            0xFF                    // HALT
        };
    }

    /**
     * Stack demo: two PUSHes, an ADD that sets CY/AC, a register write, then two POPs
     * (LIFO order: 0xFF comes back first, then 0x42).
     */
    public static int[] getStackDemoProgram() {
        return new int[]{
            0x74, 0x42,             // MOV A,#0x42
            0xC0,                   // PUSH A           SP=08, RAM[08]=42
            0x74, 0xFF,             // MOV A,#0xFF
            0xC0,                   // PUSH A           SP=09, RAM[09]=FF
            0x24, 0x01,             // ADD A,#0x01      A=00, CY=1, AC=1 (PSW=C0)
            0x78, 0x09,             // MOV R0,#0x09     R0 remembers SP after two pushes
            0xD0,                   // POP A            A=FF, SP=08
            0xD0,                   // POP A            A=42, SP=07
            0xFF                    // HALT
        };
    }

    /**
     * Queue demo: Initialize queue metadata and enqueue three values.
     */
    public static int[] getQueueDemoProgram() {
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
            // Registers mirror the queue state
            0x78, 0x03,             // MOV R0,#0x03     R0 = number of items enqueued
            0x79, 0x40,             // MOV R1,#0x40     R1 = buffer base address
            // HALT
            0xFF
        };
    }
}
