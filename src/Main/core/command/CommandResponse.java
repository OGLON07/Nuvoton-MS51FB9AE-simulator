package Main.core.command;

import Main.core.state.CoreStateSnapshot;

/**
 * Response envelope returned by the Core engine after processing
 * a {@link CommandMessage}.
 *
 * Every response carries:
 * <ul>
 *   <li>A boolean success flag</li>
 *   <li>An optional {@link CoreStateSnapshot} (present on success
 *       for state-producing commands)</li>
 *   <li>An optional error message (present on failure)</li>
 * </ul>
 *
 * Immutable once constructed.
 */
public final class CommandResponse {

    private final boolean success;
    private final CoreStateSnapshot snapshot;
    private final String errorMessage;

    // ---------- Constructors (private — use factory methods) ----------

    private CommandResponse(boolean success,
                            CoreStateSnapshot snapshot,
                            String errorMessage) {
        this.success = success;
        this.snapshot = snapshot;
        this.errorMessage = errorMessage;
    }

    // ---------- Factory methods ----------

    /**
     * Creates a successful response with a state snapshot.
     */
    public static CommandResponse ok(CoreStateSnapshot snapshot) {
        return new CommandResponse(true, snapshot, null);
    }

    /**
     * Creates a successful response without a snapshot
     * (e.g., for SHUTDOWN).
     */
    public static CommandResponse ok() {
        return new CommandResponse(true, null, null);
    }

    /**
     * Creates a failure response with an error description.
     */
    public static CommandResponse error(String message) {
        return new CommandResponse(false, null, message);
    }

    // ---------- Accessors ----------

    public boolean isSuccess() {
        return success;
    }

    public CoreStateSnapshot getSnapshot() {
        return snapshot;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (success) {
            return "CommandResponse{OK" +
                   (snapshot != null ? ", hasSnapshot" : "") + '}';
        }
        return "CommandResponse{ERROR: " + errorMessage + '}';
    }
}
