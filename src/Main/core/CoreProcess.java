package Main.core;

import Main.CPU.CPU;
import Main.Logger.LogMessage;
import Main.Logger.LogType;
import Main.core.command.Command;
import Main.core.command.CommandMessage;
import Main.core.command.CommandResponse;
import Main.core.state.CoreStateSnapshot;
import Main.IPC.IPCProtocol;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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

    /**
     * Runs the Core in IPC mode: reads commands from stdin,
     * writes responses to stdout, writes log messages to stderr.
     *
     * This is the mode used when the Core runs as a child process
     * spawned by the Launcher via ProcessBuilder.
     */
    private static void runIPCMode() {
        // Raw stdout file descriptor for protocol responses to UI
        PrintWriter responseOut = new PrintWriter(
                new OutputStreamWriter(new java.io.FileOutputStream(java.io.FileDescriptor.out)), true);
        PrintWriter logOut = new PrintWriter(
                new OutputStreamWriter(System.err), true);

        // Redirect standard System.out to System.err so CPU internal prints don't corrupt UI IPC pipe
        System.setOut(new java.io.PrintStream(System.err, true));

        CPU cpu = new CPU();
        CoreCommandHandler handler = new CoreCommandHandler(cpu);

        // Send startup log
        logOut.println(new LogMessage(LogType.SYSTEM, "Core process started").serialize());

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                CommandMessage msg = IPCProtocol.deserializeCommand(line);

                if (msg == null) {
                    // Invalid command
                    responseOut.println(IPCProtocol.serializeResponse(
                            CommandResponse.error("Invalid command: " + line)));
                    logOut.println(new LogMessage(LogType.ERROR,
                            "Invalid command received: " + line).serialize());
                    continue;
                }

                // Log the command
                logOut.println(new LogMessage(LogType.EXECUTION,
                        "Command: " + msg.getCommand().name()).serialize());

                // Process the command
                CommandResponse response = handler.handleCommand(msg);

                // Send response to UI via stdout
                responseOut.println(IPCProtocol.serializeResponse(response));

                // Log the result
                if (response.isSuccess()) {
                    String detail = "";
                    if (response.getSnapshot() != null) {
                        CoreStateSnapshot snap = response.getSnapshot();
                        detail = "PC=0x" + Integer.toHexString(snap.getPC()).toUpperCase()
                                + " A=0x" + Integer.toHexString(snap.getAccumulator()).toUpperCase()
                                + " cycles=" + snap.getCycleCount();
                    }
                    logOut.println(new LogMessage(LogType.EXECUTION,
                            msg.getCommand().name() + " completed", detail).serialize());
                } else {
                    logOut.println(new LogMessage(LogType.ERROR,
                            msg.getCommand().name() + " failed: " + response.getErrorMessage()).serialize());
                }

                // SHUTDOWN terminates the loop
                if (msg.getCommand() == Command.SHUTDOWN) {
                    logOut.println(new LogMessage(LogType.SYSTEM,
                            "Core process shutting down").serialize());
                    break;
                }
            }
        } catch (Exception e) {
            logOut.println(new LogMessage(LogType.ERROR,
                    "Core process error: " + e.getMessage()).serialize());
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
            runIPCMode();
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
