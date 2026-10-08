package Main.Logger;

/**
 * Represents a single log message received by the Logger.
 * Can be serialized to/from a simple text format for IPC transport.
 */
public class LogMessage {

    private final LogType type;
    private final long timestamp;
    private final String message;
    private final String details;

    public LogMessage(LogType type, String message) {
        this(type, System.currentTimeMillis(), message, null);
    }

    public LogMessage(LogType type, String message, String details) {
        this(type, System.currentTimeMillis(), message, details);
    }

    public LogMessage(LogType type, long timestamp, String message, String details) {
        this.type = type;
        this.timestamp = timestamp;
        this.message = message;
        this.details = details;
    }

    public LogType getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    /**
     * Checks whether the log message is valid.
     */
    public boolean isValid() {
        return type != null
                && message != null
                && !message.trim().isEmpty();
    }

    /**
     * Serializes this log message to a single-line string for IPC transport.
     * Format: LOG|TYPE|TIMESTAMP|MESSAGE|DETAILS
     * Pipe characters in message/details are escaped as \p
     */
    public String serialize() {
        String safeMessage = escape(message != null ? message : "");
        String safeDetails = escape(details != null ? details : "");
        return "LOG|" + type.name() + "|" + timestamp + "|" + safeMessage + "|" + safeDetails;
    }

    /**
     * Deserializes a log message from its serialized string form.
     * Returns null if the string is malformed.
     */
    public static LogMessage deserialize(String line) {
        if (line == null || !line.startsWith("LOG|")) {
            return null;
        }
        // Split with limit to preserve empty trailing fields
        String[] parts = line.split("\\|", 5);
        if (parts.length < 4) {
            return null;
        }
        try {
            LogType type = LogType.valueOf(parts[1]);
            long timestamp = Long.parseLong(parts[2]);
            String message = unescape(parts[3]);
            String details = (parts.length > 4 && !parts[4].isEmpty()) ? unescape(parts[4]) : null;
            return new LogMessage(type, timestamp, message, details);
        } catch (Exception e) {
            return null;
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == 'p') { sb.append('|'); i++; }
                else if (next == 'n') { sb.append('\n'); i++; }
                else if (next == 'r') { sb.append('\r'); i++; }
                else if (next == '\\') { sb.append('\\'); i++; }
                else { sb.append(s.charAt(i)); }
            } else {
                sb.append(s.charAt(i));
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "[" + type + "] " + message;
    }
}

