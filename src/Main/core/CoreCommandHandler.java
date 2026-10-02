package Main.core;

import Main.CPU.CPU;
import Main.Memory.Memory;
import Main.Queue.Queue;
import Main.core.command.Command;
import Main.core.command.CommandMessage;
import Main.core.command.CommandResponse;
import Main.core.state.CoreStateSnapshot;

/**
 * Command dispatcher / handler for the Core Process.
 *
 * Owns and orchestrates the live hardware objects (CPU, Memory,
 * Queue) and translates incoming {@link CommandMessage} instances
 * into the correct mutation sequence on those objects.
 *
 * <h3>Threading contract</h3>
 * <ul>
 *   <li>All public methods synchronize on {@code this} so that
 *       concurrent calls from the command thread and the
 *       execution worker are serialized.</li>
 *   <li>The RUN loop executes on a dedicated worker thread; a
 *       volatile flag ({@code runningContinuously}) provides a
 *       cooperative cancellation point checked after each
 *       instruction cycle.</li>
 * </ul>
 */
public class CoreCommandHandler {

    // ==================== Hardware components ====================

    private final CPU   cpu;
    private final Queue queue;

    // ==================== Execution state ====================

    /** Volatile flag: true while RUN is in progress. */
    private volatile boolean runningContinuously;

    /** The worker thread executing the RUN loop, or null. */
    private volatile Thread  executionThread;

    /** Total instruction cycles since last RESET. */
    private long cycleCount;

    /** Last error string, cleared on successful commands. */
    private String lastError;

    // ==================== Constructor ====================

    /**
     * Creates a new handler wrapping the given CPU.
     * A {@link Queue} observer is created automatically from the
     * CPU's Memory instance.
     *
     * @param cpu the live CPU (must not be null)
     */
    public CoreCommandHandler(CPU cpu) {
        if (cpu == null) {
            throw new IllegalArgumentException("CPU must not be null");
        }
        this.cpu   = cpu;
        this.queue = new Queue(cpu.getMemory());
        this.runningContinuously = false;
        this.cycleCount = 0;
        this.lastError  = null;
    }

    // ==================== Command dispatch ====================

    /**
     * Processes a single command message and returns a response.
     *
     * Thread-safe: synchronized on {@code this}.
     *
     * @param message the incoming command (must not be null)
     * @return a response indicating success/failure plus an
     *         optional state snapshot
     */
    public synchronized CommandResponse handleCommand(CommandMessage message) {

        if (message == null) {
            return CommandResponse.error("CommandMessage must not be null");
        }

        Command cmd = message.getCommand();

        try {
            switch (cmd) {

                case STEP:
                    return doStep();

                case RUN:
                    return doRun();

                case PAUSE:
                    return doPause();

                case RESET:
                    return doReset();

                case LOAD:
                    return doLoad(message.getPayload());

                case GET_STATE:
                    return doGetState();

                case SHUTDOWN:
                    return doShutdown();

                default:
                    return CommandResponse.error(
                            "Unrecognized command: " + cmd);
            }
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            return CommandResponse.error(lastError);
        }
    }

    // ==================== Command implementations ====================

    private CommandResponse doStep() {
        if (runningContinuously) {
            return CommandResponse.error(
                    "Cannot STEP while RUN is in progress — send PAUSE first");
        }
        if (cpu.isHalted()) {
            return CommandResponse.error("CPU is halted");
        }

        cpu.step();
        cycleCount++;
        lastError = null;
        return CommandResponse.ok(captureSnapshot());
    }

    // ==================== State Listener & Speed Control ====================

    @FunctionalInterface
    public interface StateListener {
        void onStateChanged(CoreStateSnapshot snapshot);
    }

    private volatile StateListener stateListener;
    private volatile int stepDelayMs = 120;

    public void setStateListener(StateListener listener) {
        this.stateListener = listener;
    }

    public void setStepDelayMs(int stepDelayMs) {
        this.stepDelayMs = Math.max(0, stepDelayMs);
    }

    public int getStepDelayMs() {
        return stepDelayMs;
    }

    /**
     * Launches the continuous execution loop on a dedicated
     * worker thread. Returns immediately with the current state.
     */
    private CommandResponse doRun() {
        if (runningContinuously) {
            return CommandResponse.error("RUN is already in progress");
        }
        if (cpu.isHalted()) {
            return CommandResponse.error("CPU is halted — RESET first");
        }

        runningContinuously = true;

        executionThread = new Thread(() -> {
            try {
                while (runningContinuously && !cpu.isHalted()) {
                    CoreStateSnapshot snap;
                    synchronized (CoreCommandHandler.this) {
                        if (!runningContinuously || cpu.isHalted()) {
                            break;
                        }
                        cpu.step();
                        cycleCount++;
                        snap = captureSnapshot();
                    }

                    if (stateListener != null) {
                        stateListener.onStateChanged(snap);
                    }

                    if (stepDelayMs > 0) {
                        try {
                            Thread.sleep(stepDelayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                synchronized (CoreCommandHandler.this) {
                    lastError = e.getClass().getSimpleName() +
                                ": " + e.getMessage();
                }
            } finally {
                runningContinuously = false;
                CoreStateSnapshot finalSnap;
                synchronized (CoreCommandHandler.this) {
                    finalSnap = captureSnapshot();
                }
                if (stateListener != null) {
                    stateListener.onStateChanged(finalSnap);
                }
            }
        }, "Core-Execution-Worker");

        executionThread.setDaemon(true);
        executionThread.start();

        lastError = null;
        return CommandResponse.ok(captureSnapshot());
    }

    private CommandResponse doPause() {
        if (!runningContinuously) {
            // Safe no-op
            return CommandResponse.ok(captureSnapshot());
        }

        runningContinuously = false;

        // Wait for the worker to finish its current cycle
        Thread t = executionThread;
        if (t != null) {
            try {
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        executionThread = null;

        lastError = null;
        return CommandResponse.ok(captureSnapshot());
    }

    private CommandResponse doReset() {
        // Stop any running execution first
        if (runningContinuously) {
            doPause();
        }

        cpu.reset();
        cycleCount = 0;
        lastError  = null;
        return CommandResponse.ok(captureSnapshot());
    }

    private CommandResponse doLoad(int[] program) {
        if (program == null || program.length == 0) {
            return CommandResponse.error(
                    "LOAD requires a non-empty bytecode payload");
        }

        // Stop execution if running
        if (runningContinuously) {
            doPause();
        }

        // Reset CPU state, registers, memory and clear halted flag
        cpu.reset();
        cpu.getMemory().loadProgram(program);
        cycleCount = 0;
        lastError  = null;
        return CommandResponse.ok(captureSnapshot());
    }

    private CommandResponse doGetState() {
        lastError = null;
        return CommandResponse.ok(captureSnapshot());
    }

    private CommandResponse doShutdown() {
        if (runningContinuously) {
            doPause();
        }
        lastError = null;
        return CommandResponse.ok();
    }

    // ==================== Snapshot helper ====================

    /**
     * Must be called while holding the monitor on {@code this}.
     */
    private CoreStateSnapshot captureSnapshot() {
        return CoreStateSnapshot.capture(
                cpu, queue,
                runningContinuously,
                lastError,
                cycleCount);
    }

    // ==================== Introspection (test support) ====================

    /** Returns true if a RUN loop is currently executing. */
    public boolean isRunningContinuously() {
        return runningContinuously;
    }

    /** Returns the cycle count. */
    public synchronized long getCycleCount() {
        return cycleCount;
    }
}
