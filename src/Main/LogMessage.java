package Main.Logger;

/**
 * Represents a single log message received by the Logger.
 */
public class LogMessage {

    private final LogType type;
    private final String message;

    public LogMessage(LogType type, String message) {
        this.type = type;
        this.message = message;
    }

    public LogType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Checks whether the log message is valid.
     */
    public boolean isValid() {
        return type != null
                && message != null
                && !message.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "[" + type + "] " + message;
    }
}
