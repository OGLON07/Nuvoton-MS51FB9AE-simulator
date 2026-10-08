package Main.core.command;

/**
 * Command contract for the Core Process.
 *
 * Each enum constant represents a distinct operation that can be
 * dispatched to the Core engine. Commands are the sole input
 * mechanism for controlling the headless simulator.
 *
 * Commands may optionally carry a payload (e.g., LOAD supplies
 * the program bytecode). The payload is transported via
 * {@link CommandMessage}.
 */
public enum Command {

    /**
     * Execute a single Fetch → Decode → Execute cycle.
     * Advances PC by the width of the consumed instruction.
     */
    STEP,

    /**
     * Begin continuous execution until HALT, breakpoint, or
     * an explicit PAUSE command is received.
     */
    RUN,

    /**
     * Interrupt a RUN in progress and return to idle state.
     * If no RUN is active this command is a safe no-op.
     */
    PAUSE,

    /**
     * Reset all CPU registers (R0–R7, A, B, PC, SP, PSW),
     * data memory, stack, queue, and internal flags to
     * their power-on-reset defaults.
     * Program memory is NOT cleared; use LOAD to replace it.
     */
    RESET,

    /**
     * Load compiled opcode/bytecode into Program Memory.
     * Requires an {@code int[]} payload in the accompanying
     * {@link CommandMessage}.
     */
    LOAD,

    /**
     * Capture and return an immutable snapshot of the entire
     * simulator state without mutating any registers or memory.
     */
    GET_STATE,

    /**
     * Gracefully terminate the command processing loop and
     * the execution worker thread, then release all resources.
     */
    SHUTDOWN
}
