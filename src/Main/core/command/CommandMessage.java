package Main.core.command;

/**
 * Transport envelope for a {@link Command} plus optional payload.
 *
 * Immutable once constructed.  The payload is required only for
 * {@link Command#LOAD}; all other command types ignore it.
 */
public final class CommandMessage {

    private final Command command;
    private final int[] payload;

    // ---------- Constructors ----------

    /**
     * Creates a command message without a payload.
     *
     * @param command the command type (must not be null)
     */
    public CommandMessage(Command command) {
        this(command, null);
    }

    /**
     * Creates a command message with an optional bytecode payload.
     *
     * @param command the command type (must not be null)
     * @param payload opcode bytes for LOAD, or null for other commands
     */
    public CommandMessage(Command command, int[] payload) {
        if (command == null) {
            throw new IllegalArgumentException("Command must not be null");
        }
        this.command = command;
        // Defensive copy to guarantee immutability
        this.payload = (payload != null) ? payload.clone() : null;
    }

    // ---------- Accessors ----------

    public Command getCommand() {
        return command;
    }

    /**
     * Returns a defensive copy of the payload, or null if none.
     */
    public int[] getPayload() {
        return (payload != null) ? payload.clone() : null;
    }

    @Override
    public String toString() {
        return "CommandMessage{" + command +
               (payload != null ? ", payloadLength=" + payload.length : "") +
               '}';
    }
}
