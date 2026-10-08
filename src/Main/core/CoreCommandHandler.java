package Main.core;

import Main.CPU.CPU;
import Main.Memory.Memory;
import Main.Queue.Queue;
import Main.core.command.Command;
import Main.core.command.CommandMessage;
import Main.core.command.CommandResponse;
import Main.core.state.CoreStateSnapshot;
import Main.instruction.Instruction;

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

    /** Status word reported to the UI (HALTED/RUNNING are derived live). */
    private String status = "READY";

    /** Last executed instruction and the PC it was fetched from (formatted lazily). */
    private int         lastPc;
    private Instruction lastIns;

    /** Minimum gap between STATE pushes while RUN is active. */
    private static final long PUSH_INTERVAL_NANOS = 50_000_000L;

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
            status = "HALTED";
            return CommandResponse.error("CPU is halted");
        }

        stepOnce();
        status = "READY";
        lastError = null;
        return CommandResponse.ok(captureSnapshot());
    }

    /**
     * Executes exactly one instruction and records a readable
     * description of it. Caller must hold the monitor on {@code this}.
     */
    private void stepOnce() {
        int pcBefore = cpu.getRegisters().getPC();
        cpu.step();
        cycleCount++;
        lastPc  = pcBefore;
        lastIns = cpu.getDecodedInstruction();   // formatted only when a snapshot is taken
    }

    private static String describe(int pc, Instruction ins) {
        if (ins == null) {
            return String.format("PC=0x%04X ?", pc);
        }
        StringBuilder sb = new StringBuilder(String.format("PC=0x%04X %s", pc, ins.getOpcode()));
        if (ins.getRegisterIndex() >= 0) {
            sb.append(" R").append(ins.getRegisterIndex());
        }
        sb.append(String.format(" 0x%02X", ins.getOperand() & 0xFF));
        return sb.toString();
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
     * worker thread. Returns immediately with the current state;
     * the worker streams STATE updates (at most one per 50 ms) to the
     * registered {@link StateListener} and a final one when it stops.
     */
    private CommandResponse doRun() {
        if (runningContinuously) {
            return CommandResponse.error("RUN is already in progress");
        }
        if (cpu.isHalted()) {
            return CommandResponse.error("CPU is halted — RESET first");
        }

        runningContinuously = true;
        lastError = null;

        Thread worker = new Thread(this::runLoop, "Core-Execution-Worker");
        worker.setDaemon(true);
        executionThread = worker;
        worker.start();

        return CommandResponse.ok(captureSnapshot());
    }

    /** Body of the execution worker thread. */
    private void runLoop() {
        long lastPush = System.nanoTime();
        try {
            while (runningContinuously) {
                CoreStateSnapshot snap = null;
                synchronized (this) {
                    if (!runningContinuously || cpu.isHalted()) {
                        break;
                    }
                    stepOnce();
                    long now = System.nanoTime();
                    if (now - lastPush >= PUSH_INTERVAL_NANOS || stepDelayMs > 0) {
                        snap = captureSnapshot();
                        lastPush = now;
                    }
                }

                StateListener l = stateListener;
                if (snap != null && l != null) {
                    l.onStateChanged(snap);
                }

                if (stepDelayMs > 0) {
                    try {
                        Thread.sleep(stepDelayMs);
                    } catch (InterruptedException e) {
                        break;      // PAUSE/RESET/LOAD/SHUTDOWN interrupts the delay
                    }
                }
            }
        } catch (Exception e) {
            synchronized (this) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                status = "ERROR";
            }
        } finally {
            CoreStateSnapshot finalSnap;
            synchronized (this) {
                runningContinuously = false;
                if (executionThread == Thread.currentThread()) {
                    executionThread = null;
                }
                finalSnap = captureSnapshot();
                notifyAll();            // wakes a PAUSE that is waiting for us
            }
            StateListener l = stateListener;
            if (l != null) {
                l.onStateChanged(finalSnap);
            }
        }
    }

    /**
     * Stops the RUN worker and waits for it to finish.
     * Caller holds the monitor; {@code wait()} releases it so the
     * worker can run its final synchronized block.
     */
    private CommandResponse doPause() {
        if (!runningContinuously) {
            return CommandResponse.ok(captureSnapshot());    // safe no-op
        }

        stopWorker();
        if (!cpu.isHalted()) {
            status = "PAUSED";
        }
        lastError = null;
        return CommandResponse.ok(captureSnapshot());
    }

    private void stopWorker() {
        runningContinuously = false;
        Thread t = executionThread;
        if (t != null) {
            t.interrupt();                       // cut short a step-delay sleep
        }
        long deadline = System.nanoTime() + 2_000_000_000L;
        try {
            while (executionThread != null) {
                long leftMs = (deadline - System.nanoTime()) / 1_000_000L;
                if (leftMs <= 0) {
                    break;
                }
                wait(leftMs);                    // releases the monitor
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private CommandResponse doReset() {
        if (runningContinuously) {
            stopWorker();
        }

        cpu.reset();
        cycleCount = 0;
        lastError  = null;
        lastIns = null;
        status = "RESET";
        return CommandResponse.ok(captureSnapshot());
    }

    private CommandResponse doLoad(int[] program) {
        if (program == null || program.length == 0) {
            return CommandResponse.error(
                    "LOAD requires a non-empty bytecode payload");
        }

        if (runningContinuously) {
            stopWorker();
        }

        // Reset CPU state, registers, memory and clear halted flag
        cpu.reset();
        cpu.getMemory().loadProgram(program);
        cycleCount = 0;
        lastError  = null;
        lastIns = null;
        status = "LOADED";
        return CommandResponse.ok(captureSnapshot());
    }

    private CommandResponse doGetState() {
        lastError = null;
        return CommandResponse.ok(captureSnapshot());
    }

    private CommandResponse doShutdown() {
        if (runningContinuously) {
            stopWorker();
        }
        lastError = null;
        return CommandResponse.ok();
    }

    // ==================== Snapshot helper ====================

    /**
     * Must be called while holding the monitor on {@code this}.
     */
    private CoreStateSnapshot captureSnapshot() {
        String shown = cpu.isHalted() ? "HALTED"
                     : runningContinuously ? "RUNNING" : status;
        return CoreStateSnapshot.capture(
                cpu, queue,
                runningContinuously,
                lastError,
                cycleCount,
                shown,
                lastIns == null ? null : describe(lastPc, lastIns));
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
