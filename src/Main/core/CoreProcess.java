package Main.core;

import Main.CPU.CPU;
import Main.core.command.Command;
import Main.core.command.CommandMessage;
import Main.core.command.CommandResponse;
import Main.core.state.CoreStateSnapshot;

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
 *   │  commandQueue ──► Command Processing Loop    │
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
 *   └──────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Usage</h3>
 * <ol>
 *   <li>Instantiate via {@code new CoreProcess()} or call
 *       {@code CoreProcess.main(args)}.</li>
 *   <li>Submit commands through {@link #submitCommand(CommandMessage)}
 *       from any thread.</li>
 *   <li>Call {@link #start()} to begin the command processing loop
 *       (blocks the calling thread).</li>
 *   <li>Send {@link Command#SHUTDOWN} to terminate cleanly.</li>
 * </ol>
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
     * writes, but per constraints we only provide an abstract
     * interface and an in-memory test harness.
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

    // ==================== Entry point ====================

    /**
     * Standalone entry point.
     *
     * <p>Boots the Core process, prints a ready banner, and blocks
     * on the command loop.  In a real deployment an IPC adapter
     * would feed commands into the queue; for now the process
     * simply waits.</p>
     */
    public static void main(String[] args) {
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
