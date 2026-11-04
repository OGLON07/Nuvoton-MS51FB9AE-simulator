package Main.core;

import Main.CPU.CPU;
import Main.Logger.LogMessage;
import Main.Logger.LogType;
import Main.core.command.Command;
import Main.core.command.CommandMessage;
import Main.core.command.CommandResponse;
import Main.core.state.CoreStateSnapshot;
import Main.IPC.IPCProtocol;
import Main.instruction.AssemblyAssembler;
import Main.instruction.DemoPrograms;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standalone headless entry point for the simulator Core process.
 *
 * <h3>Architecture</h3>
 * <pre>
 *   ┌──────────────────────────────────────────────┐
 *   │              CoreProcess (main)              │
 *   │                                              │
 *   │  stdin  ──► Command Processing Loop          │
 *   │                       │                      │
 *   │                       ▼                      │
 *   │              CoreCommandHandler              │
 *   │                  │          │                 │
 *   │            ┌─────┘          └─────┐          │
 *   │            ▼                      ▼          │
 *   │         CPU (existing)     Execution Worker  │
 *   │        Registers            (RUN thread)     │
 *   │        Memory                                │
 *   │        Queue                                 │
 *   │                                              │
 *   │  stdout ◄── Responses to UI                  │
 *   │  stderr ◄── Log messages to Logger           │
 *   └──────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>IPC Mode (--ipc)</h3>
 * When started with {@code --ipc}, the Core reads commands from
 * stdin and writes responses to stdout (for the UI) and log
 * messages to stderr (for the Logger).
 *
 * <h3>In-process Mode (default)</h3>
 * Commands are submitted via {@link #submitCommand(CommandMessage)}
 * and processed on the command loop thread. Useful for testing.
 *
 * <p>No Swing/AWT/UI imports or dependencies exist in this class.</p>
 */
public class CoreProcess {

    // ==================== Components ====================

    private final CPU                cpu;
    private final CoreCommandHandler handler;

    /** Inbound command queue — producers are any thread; the
     *  consumer is the command processing loop. */
    private final BlockingQueue<CommandMessage> commandQueue;

    /** Lifecycle flag. */
    private volatile boolean running;

    /**
     * Optional callback invoked on the command-loop thread after
     * every command response.  Allows in-process test harnesses
     * to observe results without IPC.
     */
    private volatile ResponseListener responseListener;

    // ==================== Listener interface ====================

    /**
     * Functional callback for in-memory test harnesses.
     * A real IPC transport would replace this with socket/pipe
     * writes.
     */
    @FunctionalInterface
    public interface ResponseListener {
        void onResponse(CommandResponse response);
    }

    // ==================== Constructor ====================

    /**
     * Creates a new Core process with a fresh CPU, Memory, Stack,
     * and Queue initialised to power-on-reset defaults.
     */
    public CoreProcess() {
        this.cpu          = new CPU();
        this.handler      = new CoreCommandHandler(cpu);
        this.commandQueue = new LinkedBlockingQueue<>();
        this.running      = false;
    }

    // ==================== Lifecycle ====================

    /**
     * Starts the command processing loop on the <em>calling</em>
     * thread. Blocks until a {@link Command#SHUTDOWN} command is
     * processed.
     *
     * <p>The loop drains the {@link #commandQueue}, dispatches
     * each message to the {@link CoreCommandHandler}, and
     * optionally notifies the {@link ResponseListener}.</p>
     */
    public void start() {
        if (running) {
            System.out.println("[Core] Already running.");
            return;
        }

        running = true;
        System.out.println("[Core] Process started.");

        while (running) {
            try {
                // Block until a command arrives
                CommandMessage message = commandQueue.take();

                CommandResponse response = handler.handleCommand(message);

                // Notify listener (if any)
                ResponseListener listener = this.responseListener;
                if (listener != null) {
                    listener.onResponse(response);
                }

                // SHUTDOWN terminates the loop
                if (message.getCommand() == Command.SHUTDOWN) {
                    running = false;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }

        System.out.println("[Core] Process stopped.");
    }

    /**
     * Starts the command processing loop on a new daemon thread
     * and returns immediately. Useful for testing.
     *
     * @return the daemon thread running the command loop
     */
    public Thread startAsync() {
        Thread t = new Thread(this::start, "Core-Process-Loop");
        t.setDaemon(true);
        t.start();
        return t;
    }

    // ==================== Command submission ====================

    /**
     * Enqueues a command for asynchronous processing.
     * Thread-safe; may be called from any thread.
     *
     * @param message the command to enqueue (must not be null)
     */
    public void submitCommand(CommandMessage message) {
        if (message == null) {
            throw new IllegalArgumentException(
                    "CommandMessage must not be null");
        }
        commandQueue.offer(message);
    }

    /**
     * Convenience: enqueues a payload-less command.
     */
    public void submitCommand(Command command) {
        submitCommand(new CommandMessage(command));
    }

    /**
     * Submits a command and blocks until the response is produced.
     * Useful for synchronous testing.
     *
     * @param message the command to process
     * @return the response from the handler
     */
    public CommandResponse submitAndWait(CommandMessage message) {
        return handler.handleCommand(message);
    }

    /**
     * Convenience overload for payload-less commands.
     */
    public CommandResponse submitAndWait(Command command) {
        return submitAndWait(new CommandMessage(command));
    }

    // ==================== Configuration ====================

    /**
     * Registers an in-memory response listener (test harness).
     */
    public void setResponseListener(ResponseListener listener) {
        this.responseListener = listener;
    }

    // ==================== Introspection ====================

    /** Returns true if the command loop is active. */
    public boolean isRunning() {
        return running;
    }

    /** Exposes the handler for advanced introspection / testing. */
    public CoreCommandHandler getHandler() {
        return handler;
    }

    // ==================== IPC Mode ====================

    /** Writes one protocol line atomically (command thread and RUN worker both send). */
    private static void send(PrintWriter out, String line) {
        synchronized (out) {
            out.println(line);
        }
    }

    private static void log(PrintWriter out, LogType type, String msg, String details) {
        synchronized (out) {
            out.println(new LogMessage(type, msg, details).serialize());
        }
    }

    /** Returns the value following {@code flag} in args, or null. */
    private static String argValue(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    /**
     * Runs the Core in IPC mode. File descriptors (set up by the native
     * POSIX launcher with pipe()/fork()/dup2()/exec):
     * <pre>
     *   fd 0 (stdin)  : commands   from the UI process     (pipe read end)
     *   fd 1 (stdout) : responses  to   the UI process     (pipe write end)
     *   fd 3          : log lines  to   the Logger process (pipe write end)
     *   fd 2 (stderr) : diagnostics, left on the terminal
     * </pre>
     * {@code --log-fd N} selects the log descriptor; if it is absent
     * (e.g. when started by the Java fallback launcher) logs go to stderr.
     * {@code --step-delay-ms N} sets the pause between instructions
     * during RUN (default keeps the UI animation visible; 0 = full speed).
     */
    private static void runIPCMode(String[] args) {
        PrintWriter responseOut = new PrintWriter(
                new OutputStreamWriter(new java.io.FileOutputStream(java.io.FileDescriptor.out)), true);

        String logFd = argValue(args, "--log-fd");
        PrintWriter logOut;
        try {
            java.io.OutputStream logStream = (logFd != null)
                    ? new java.io.FileOutputStream("/dev/fd/" + logFd)
                    : System.err;
            logOut = new PrintWriter(new OutputStreamWriter(logStream), true);
        } catch (java.io.IOException e) {
            System.err.println("[Core] Cannot open log descriptor " + logFd + ": " + e.getMessage());
            logOut = new PrintWriter(new OutputStreamWriter(System.err), true);
        }
        final PrintWriter logs = logOut;
        final AtomicBoolean hammingResultLogged = new AtomicBoolean(false);

        // The Week-3 CPU prints FETCH/DECODE lines to System.out. Those must
        // never reach the IPC channels, so they are discarded here.
        System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));

        CPU cpu = new CPU();
        CoreCommandHandler handler = new CoreCommandHandler(cpu);

        String delay = argValue(args, "--step-delay-ms");
        if (delay != null) {
            try {
                handler.setStepDelayMs(Integer.parseInt(delay));
            } catch (NumberFormatException e) {
                System.err.println("[Core] Ignoring bad --step-delay-ms: " + delay);
            }
        }

        // RUN worker pushes STATE updates; log once when it stops.
        handler.setStateListener(snapshot -> {
            send(responseOut, IPCProtocol.serializeResponse(CommandResponse.ok(snapshot)));
            logHammingResultIfComplete(logs, snapshot, hammingResultLogged);
            if (!snapshot.isExecuting()) {
                log(logs, snapshot.getLastError() != null ? LogType.ERROR : LogType.EXECUTION,
                        "RUN stopped: " + snapshot.getStatus(),
                        "PC=0x" + Integer.toHexString(snapshot.getPC()).toUpperCase()
                        + " cycles=" + snapshot.getCycleCount()
                        + (snapshot.getLastError() != null ? " error=" + snapshot.getLastError() : ""));
            }
        });

        log(logs, LogType.SYSTEM, "Core process started", "pid=" + ProcessHandle.current().pid());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    CommandMessage msg = IPCProtocol.deserializeCommand(line);
                    if (msg == null) {
                        send(responseOut, IPCProtocol.serializeResponse(
                                CommandResponse.error("Invalid command: " + line)));
                        log(logs, LogType.ERROR, "Invalid command received", line);
                        continue;
                    }

                    CommandResponse response = handler.handleCommand(msg);
                    send(responseOut, IPCProtocol.serializeResponse(response));
                    if (msg.getCommand() == Command.LOAD || msg.getCommand() == Command.RESET) {
                        hammingResultLogged.set(false);
                    }
                    if (response.isSuccess() && response.getSnapshot() != null) {
                        logHammingResultIfComplete(logs, response.getSnapshot(), hammingResultLogged);
                    }
                    logCommandResult(logs, msg, response);

                    if (msg.getCommand() == Command.SHUTDOWN) {
                        break;
                    }
                } catch (RuntimeException e) {
                    // Never let one bad command kill the Core.
                    send(responseOut, IPCProtocol.serializeResponse(
                            CommandResponse.error("Internal error: " + e)));
                    log(logs, LogType.ERROR, "Internal error handling command", String.valueOf(e));
                }
            }
        } catch (java.io.IOException e) {
            log(logs, LogType.ERROR, "Core I/O error", String.valueOf(e));
        } finally {
            // stdin closed without SHUTDOWN (UI died) or after SHUTDOWN: stop the worker.
            handler.handleCommand(new CommandMessage(Command.SHUTDOWN));
            log(logs, LogType.SYSTEM, "Core process shutting down", null);
            logs.close();           // closes fd 3 -> Logger sees EOF and exits
        }
    }

    /**
     * Records the result produced by the Hamming assembly routine.
     * The assembly program writes signature 0x48 to RAM[0x2D] only after
     * its parity/syndrome routine reaches completion. Core only transports
     * the already-computed assembly result; it does not calculate parity.
     */
    private static void logHammingResultIfComplete(PrintWriter logs,
                                                    CoreStateSnapshot snapshot,
                                                    AtomicBoolean alreadyLogged) {
        if (snapshot == null || !snapshot.isHalted() || alreadyLogged.get()) {
            return;
        }
        int[] ram = snapshot.getDataMemory();
        if (ram.length <= 0x2D || ram[0x2D] != 0x48) {
            return;
        }
        if (!alreadyLogged.compareAndSet(false, true)) {
            return;
        }

        int input = ram[0x20] & 0x0F;
        int codeword = ram[0x24] & 0x7F;
        int syndrome = ram[0x2B] & 0x07;
        int correctionMarker = ram[0x2C] & 0xFF;
        String status = syndrome == 0 ? "NO ERROR DETECTED" : "ERROR DETECTED";
        String syndromeBits = String.format("%3s", Integer.toBinaryString(syndrome)).replace(' ', '0');
        String details = String.format(
                "input=0x%X codeword=0x%02X syndrome=%d (0b%s) correctionMarker=%d; " +
                "scope=Hamming demo codeword in data RAM (not the entire program memory)",
                input, codeword, syndrome, syndromeBits, correctionMarker);
        log(logs, LogType.HAMMING, status, details);
        logHammingProgramByteReport(logs, snapshot);
    }

    /**
     * Produces an instruction-by-instruction report for the loaded Hamming demo.
     * Each program byte is split into two nibbles. For each possible nibble,
     * the existing Hamming assembly routine is executed once and its syndrome
     * is read from RAM. Java only coordinates the runs and formats the report;
     * parity and syndrome calculations remain in the assembly program.
     *
     * This is an assembly-codeword self-check, not a comparison with parity
     * saved before a possible ROM corruption.
     */
    private static void logHammingProgramByteReport(PrintWriter logs, CoreStateSnapshot snapshot) {
        int[] program = snapshot.getProgramMemory();
        int size = Math.min(snapshot.getProgramSize(), program.length);
        if (size == 0) return;

        boolean[] nibblePassed = new boolean[16];
        String assembly = DemoPrograms.hamming();
        for (int nibble = 0; nibble < 16; nibble++) {
            String input = String.format("MOV A,#0x%02X", nibble);
            String testProgram = assembly.replaceFirst("MOV A,#0x0B", input);
            try {
                CPU verifier = new CPU();
                verifier.getMemory().loadProgram(AssemblyAssembler.assemble(testProgram));
                verifier.run();
                int completion = verifier.getMemory().readData(0x2D);
                int syndrome = verifier.getMemory().readData(0x2B) & 0x07;
                nibblePassed[nibble] = verifier.isHalted()
                        && completion == 0x48
                        && syndrome == 0;
            } catch (RuntimeException ex) {
                nibblePassed[nibble] = false;
            }
        }

        int instructionCount = 0;
        int byteAddress = 0;
        int passedInstructions = 0;
        while (byteAddress < size) {
            int opcode = program[byteAddress] & 0xFF;
            int length = instructionLength(opcode);
            if (byteAddress + length > size) length = 1;
            boolean passed = true;
            StringBuilder bytes = new StringBuilder();
            for (int i = 0; i < length; i++) {
                int value = program[byteAddress + i] & 0xFF;
                if (i > 0) bytes.append(' ');
                bytes.append(String.format("%02X", value));
                passed &= nibblePassed[(value >>> 4) & 0x0F] && nibblePassed[value & 0x0F];
            }
            String mnemonic = instructionName(opcode);
            String result = passed ? "PASS" : "CHECK FAILED";
            if (passed) passedInstructions++;
            log(logs, LogType.HAMMING,
                    String.format("Instruction %03d %s | %s", ++instructionCount, mnemonic, result),
                    String.format("address=0x%04X bytes=[%s] hammingNibbleChecks=%s; "
                            + "validation=assembly-generated-codeword-self-check; "
                            + "doesNotCompareAgainstPreviouslyStoredParity",
                            byteAddress, bytes, passed ? "PASS" : "FAIL"));
            byteAddress += length;
        }
        log(logs, LogType.HAMMING,
                passedInstructions == instructionCount ? "HAMMING DEMO PROGRAM PASSED" : "HAMMING DEMO PROGRAM CHECK FAILED",
                String.format("instructionsChecked=%d passed=%d failed=%d programBytes=%d; "
                        + "scope=loaded Hamming demo program bytes; no stored pre-corruption parity reference",
                        instructionCount, passedInstructions, instructionCount - passedInstructions, size));
    }

    private static int instructionLength(int opcode) {
        switch (opcode) {
            case 0x74: case 0x78: case 0x79: case 0x7A: case 0x7B:
            case 0x7C: case 0x7D: case 0x7E: case 0x7F:
            case 0x24: case 0x94: case 0x54: case 0x44: case 0x64:
            case 0x60: case 0x70: case 0x80: case 0xE5: case 0xF5:
            case 0xD2: case 0xC2: case 0xB2: return 2;
            case 0x20: case 0x30: case 0x75: return 3;
            default: return 1;
        }
    }

    private static String instructionName(int opcode) {
        switch (opcode) {
            case 0x74: return "MOV A,#imm";
            case 0x78: case 0x79: case 0x7A: case 0x7B: case 0x7C: case 0x7D: case 0x7E: case 0x7F: return "MOV Rn,#imm";
            case 0x75: return "MOV direct,#imm";
            case 0xE5: return "MOV A,direct";
            case 0xF5: return "MOV direct,A";
            case 0x24: return "ADD A,#imm";
            case 0x94: return "SUBB A,#imm";
            case 0x54: return "ANL A,#imm";
            case 0x44: return "ORL A,#imm";
            case 0x64: return "XRL A,#imm";
            case 0x30: return "JNB bit,rel";
            case 0x20: return "JB bit,rel";
            case 0x60: return "JZ rel";
            case 0x70: return "JNZ rel";
            case 0x80: return "SJMP rel";
            case 0xD2: return "SETB bit";
            case 0xC2: return "CLR bit";
            case 0xB2: return "CPL bit";
            case 0x04: return "INC A";
            case 0x14: return "DEC A";
            case 0xA4: return "MUL AB";
            case 0xC0: return "PUSH A";
            case 0xD0: return "POP A";
            case 0x00: return "NOP";
            case 0xFF: return "HALT";
            default: return String.format("DB 0x%02X", opcode);
        }
    }

    /** Emits one meaningful log line per command (GET_STATE is not logged: it is polling). */
    private static void logCommandResult(PrintWriter logs, CommandMessage msg, CommandResponse r) {
        Command c = msg.getCommand();
        if (!r.isSuccess()) {
            log(logs, LogType.ERROR, c.name() + " failed", r.getErrorMessage());
            return;
        }
        CoreStateSnapshot s = r.getSnapshot();
        switch (c) {
            case LOAD:
                log(logs, LogType.EXECUTION, "Program loaded",
                        (s != null ? s.getProgramSize() : 0) + " bytes");
                break;
            case STEP:
                log(logs, LogType.EXECUTION, "STEP executed",
                        s.getLastInstruction() + " -> A=0x" + Integer.toHexString(s.getAccumulator()).toUpperCase()
                        + " cycles=" + s.getCycleCount());
                break;
            case RUN:
                log(logs, LogType.EXECUTION, "RUN started", null);
                break;
            case PAUSE:
                log(logs, LogType.EXECUTION, "PAUSE", "cycles=" + (s != null ? s.getCycleCount() : 0));
                break;
            case RESET:
                log(logs, LogType.EXECUTION, "RESET", null);
                break;
            case SHUTDOWN:
                log(logs, LogType.SYSTEM, "SHUTDOWN received", null);
                break;
            default:
                break;
        }
    }

    // ==================== Entry point ====================

    /**
     * Standalone entry point.
     *
     * <p>Supports two modes:</p>
     * <ul>
     *   <li>{@code --ipc}: IPC mode — reads stdin, writes stdout/stderr</li>
     *   <li>{@code --self-test}: Self-test mode for verification</li>
     *   <li>Default: starts command loop waiting for in-process commands</li>
     * </ul>
     */
    public static void main(String[] args) {
        // Check for IPC mode
        if (args.length > 0 && "--ipc".equals(args[0])) {
            runIPCMode(args);
            return;
        }

        System.out.println("========================================");
        System.out.println("  Nuvoton MS51FB9AE — Core Process");
        System.out.println("  Headless Simulator Engine (Week 4)");
        System.out.println("========================================");

        CoreProcess core = new CoreProcess();

        // In standalone mode there is no IPC feeder yet.
        // Submit a SHUTDOWN after a brief delay so the process
        // doesn't hang when started with no external controller.
        if (args.length > 0 && "--self-test".equals(args[0])) {
            Thread feeder = new Thread(() -> {
                try {
                    Thread.sleep(100);
                    core.submitCommand(Command.GET_STATE);
                    Thread.sleep(100);
                    core.submitCommand(Command.SHUTDOWN);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Core-SelfTest-Feeder");
            feeder.setDaemon(true);
            feeder.start();
        }

        core.start();
    }
}
