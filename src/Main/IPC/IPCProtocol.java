package Main.IPC;

import Main.core.command.Command;
import Main.core.command.CommandMessage;
import Main.core.command.CommandResponse;
import Main.core.state.CoreStateSnapshot;

import java.util.Arrays;

/**
 * IPC Message Protocol — text-based, line-oriented.
 *
 * All messages are single-line strings transmitted over process
 * stdin/stdout pipes. This keeps the protocol simple and easy
 * to demonstrate during a college presentation.
 *
 * <h3>UI → Core commands</h3>
 * <pre>
 *   CMD|STEP
 *   CMD|RUN
 *   CMD|PAUSE
 *   CMD|RESET
 *   CMD|LOAD|byte0,byte1,byte2,...
 *   CMD|GET_STATE
 *   CMD|SHUTDOWN
 * </pre>
 *
 * <h3>Core → UI responses</h3>
 * <pre>
 *   RSP|OK|STATUS|snapshot-data
 *   RSP|ERROR|error-message
 *   RSP|SHUTDOWN_ACK
 * </pre>
 *
 * <h3>Core → Logger messages</h3>
 * <pre>
 *   LOG|TYPE|TIMESTAMP|MESSAGE|DETAILS
 * </pre>
 *
 * <h3>IPC Mechanism</h3>
 * OS-level process pipe IPC implemented through Java ProcessBuilder.
 * Each child process communicates via its stdin/stdout streams,
 * which the OS kernel connects through anonymous pipe file descriptors.
 */
public final class IPCProtocol {

    // ==================== Prefixes ====================

    public static final String CMD_PREFIX = "CMD|";
    public static final String RSP_PREFIX = "RSP|";
    public static final String LOG_PREFIX = "LOG|";

    // ==================== UI → Core serialization ====================

    /**
     * Serializes a CommandMessage to a single-line string.
     */
    public static String serializeCommand(CommandMessage msg) {
        Command cmd = msg.getCommand();
        if (cmd == Command.LOAD) {
            int[] payload = msg.getPayload();
            if (payload != null && payload.length > 0) {
                StringBuilder sb = new StringBuilder();
                sb.append(CMD_PREFIX).append("LOAD|");
                for (int i = 0; i < payload.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(payload[i]);
                }
                return sb.toString();
            }
            return CMD_PREFIX + "LOAD|";
        }
        return CMD_PREFIX + cmd.name();
    }

    /**
     * Deserializes a command line into a CommandMessage.
     * Returns null if the line is malformed.
     */
    public static CommandMessage deserializeCommand(String line) {
        if (line == null || !line.startsWith(CMD_PREFIX)) {
            return null;
        }
        String body = line.substring(CMD_PREFIX.length());

        // Check for LOAD with payload
        if (body.startsWith("LOAD|")) {
            String payloadStr = body.substring(5);
            if (payloadStr.isEmpty()) {
                return new CommandMessage(Command.LOAD);
            }
            try {
                String[] parts = payloadStr.split(",");
                int[] payload = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    payload[i] = Integer.parseInt(parts[i].trim());
                }
                return new CommandMessage(Command.LOAD, payload);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        // Simple payload-less command
        // Handle LOAD with no pipe separator (empty payload)
        if (body.equals("LOAD")) {
            return new CommandMessage(Command.LOAD);
        }

        try {
            Command cmd = Command.valueOf(body.trim());
            return new CommandMessage(cmd);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ==================== Core → UI serialization ====================

    /**
     * Serializes a CommandResponse to a single-line string.
     * State snapshots are encoded in a compact key=value format.
     */
    public static String serializeResponse(CommandResponse response) {
        if (!response.isSuccess()) {
            String errMsg = response.getErrorMessage();
            return RSP_PREFIX + "ERROR|" + escape(errMsg != null ? errMsg : "Unknown error");
        }

        CoreStateSnapshot snap = response.getSnapshot();
        if (snap == null) {
            // SHUTDOWN_ACK or similar no-state response
            return RSP_PREFIX + "SHUTDOWN_ACK";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(RSP_PREFIX).append("OK|");

        // Encode snapshot fields
        sb.append("PC=").append(snap.getPC()).append(',');
        sb.append("SP=").append(snap.getSP()).append(',');
        sb.append("A=").append(snap.getAccumulator()).append(',');
        sb.append("B=").append(snap.getBRegister()).append(',');
        sb.append("PSW=").append(snap.getPSW()).append(',');

        // R0-R7
        int[] regs = snap.getRegisters();
        for (int i = 0; i < 8; i++) {
            sb.append("R").append(i).append('=').append(regs[i]).append(',');
        }

        // Flags
        sb.append("CY=").append(snap.isCarryFlag() ? 1 : 0).append(',');
        sb.append("AC=").append(snap.isAuxCarryFlag() ? 1 : 0).append(',');
        sb.append("OV=").append(snap.isOverflowFlag() ? 1 : 0).append(',');

        // Status
        sb.append("HALTED=").append(snap.isHalted() ? 1 : 0).append(',');
        sb.append("EXEC=").append(snap.isExecuting() ? 1 : 0).append(',');
        sb.append("CYCLES=").append(snap.getCycleCount()).append(',');
        sb.append("PSIZE=").append(snap.getProgramSize()).append(',');
        sb.append("ST=").append(escape(snap.getStatus() != null ? snap.getStatus() : "READY")).append(',');
        if (snap.getLastInstruction() != null) {
            sb.append("LAST=").append(escape(snap.getLastInstruction())).append(',');
        }

        // Data memory (256 bytes as hex string)
        sb.append("DM=");
        appendHex(sb, snap.getDataMemory());
        sb.append(',');

        // Program memory (only loaded portion as hex)
        sb.append("PM=");
        appendHex(sb, snap.getProgramMemory());
        sb.append(',');

        // Stack contents
        sb.append("STK=");
        int[] stk = snap.getStackContents();
        for (int i = 0; i < stk.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(stk[i]);
        }
        sb.append(',');

        // Queue
        sb.append("QH=").append(snap.getQueueHead()).append(',');
        sb.append("QT=").append(snap.getQueueTail()).append(',');
        sb.append("QC=").append(snap.getQueueCount()).append(',');
        sb.append("QCAP=").append(snap.getQueueCapacity()).append(',');
        sb.append("QD=");
        int[] qd = snap.getQueueContents();
        for (int i = 0; i < qd.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(qd[i]);
        }

        // Error
        if (snap.getLastError() != null) {
            sb.append(",ERR=").append(escape(snap.getLastError()));
        }

        return sb.toString();
    }

    /**
     * Parses a response line from the Core process.
     * Returns a simple ParsedResponse with success/error/snapshot data.
     */
    public static ParsedResponse parseResponse(String line) {
        if (line == null || !line.startsWith(RSP_PREFIX)) {
            return null;
        }
        String body = line.substring(RSP_PREFIX.length());

        if (body.startsWith("ERROR|")) {
            return new ParsedResponse(false, unescape(body.substring(6)), null);
        }

        if (body.equals("SHUTDOWN_ACK")) {
            return new ParsedResponse(true, null, null);
        }

        if (body.startsWith("OK|")) {
            String data = body.substring(3);
            return new ParsedResponse(true, null, parseSnapshotData(data));
        }

        return null;
    }

    // ==================== Snapshot parsing ====================

    private static SnapshotData parseSnapshotData(String data) {
        SnapshotData sd = new SnapshotData();
        // Parse key=value pairs separated by commas
        // Some values contain colons (stack, queue) or hex (DM, PM)
        String[] pairs = data.split(",");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = pair.substring(0, eq);
            String val = pair.substring(eq + 1);
            try {
                switch (key) {
                    case "PC":   sd.pc = Integer.parseInt(val); break;
                    case "SP":   sd.sp = Integer.parseInt(val); break;
                    case "A":    sd.acc = Integer.parseInt(val); break;
                    case "B":    sd.b = Integer.parseInt(val); break;
                    case "PSW":  sd.psw = Integer.parseInt(val); break;
                    case "R0":   sd.r[0] = Integer.parseInt(val); break;
                    case "R1":   sd.r[1] = Integer.parseInt(val); break;
                    case "R2":   sd.r[2] = Integer.parseInt(val); break;
                    case "R3":   sd.r[3] = Integer.parseInt(val); break;
                    case "R4":   sd.r[4] = Integer.parseInt(val); break;
                    case "R5":   sd.r[5] = Integer.parseInt(val); break;
                    case "R6":   sd.r[6] = Integer.parseInt(val); break;
                    case "R7":   sd.r[7] = Integer.parseInt(val); break;
                    case "CY":   sd.cy = "1".equals(val); break;
                    case "AC":   sd.ac = "1".equals(val); break;
                    case "OV":   sd.ov = "1".equals(val); break;
                    case "HALTED": sd.halted = "1".equals(val); break;
                    case "EXEC":   sd.executing = "1".equals(val); break;
                    case "CYCLES": sd.cycleCount = Long.parseLong(val); break;
                    case "PSIZE":  sd.programSize = Integer.parseInt(val); break;
                    case "ST":     sd.status = unescape(val); break;
                    case "LAST":   sd.lastInstruction = unescape(val); break;
                    case "DM":     sd.dataMemory = hexToBytes(val); break;
                    case "PM":     sd.programMemory = hexToBytes(val); break;
                    case "STK":    sd.stackContents = colonInts(val); break;
                    case "QH":     sd.queueHead = Integer.parseInt(val); break;
                    case "QT":     sd.queueTail = Integer.parseInt(val); break;
                    case "QC":     sd.queueCount = Integer.parseInt(val); break;
                    case "QCAP":   sd.queueCapacity = Integer.parseInt(val); break;
                    case "QD":     sd.queueContents = colonInts(val); break;
                    case "ERR":    sd.lastError = unescape(val); break;
                }
            } catch (Exception e) {
                // Skip malformed fields
            }
        }
        return sd;
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /** Fast hex encoder (String.format per byte cost ~1.8 ms for an 8 KB program). */
    private static void appendHex(StringBuilder sb, int[] bytes) {
        sb.ensureCapacity(sb.length() + bytes.length * 2);
        for (int b : bytes) {
            sb.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
        }
    }

    private static int[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new int[0];
        int[] result = new int[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static int[] colonInts(String s) {
        if (s == null || s.isEmpty()) return new int[0];
        String[] parts = s.split(":");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i]);
        }
        return result;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\p").replace(",", "\\c").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == 'p') { sb.append('|'); i++; }
                else if (next == 'c') { sb.append(','); i++; }
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

    // ==================== Data classes ====================

    /**
     * Parsed response from Core process.
     */
    public static final class ParsedResponse {
        public final boolean success;
        public final String errorMessage;
        public final SnapshotData snapshot;

        public ParsedResponse(boolean success, String errorMessage, SnapshotData snapshot) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.snapshot = snapshot;
        }
    }

    /**
     * Deserialized snapshot data — plain fields for UI consumption.
     * This is the UI-side representation of CoreStateSnapshot.
     */
    public static final class SnapshotData {
        public int pc, sp, acc, b, psw;
        public int[] r = new int[8];
        public boolean cy, ac, ov;
        public boolean halted, executing;
        public long cycleCount;
        public int programSize;
        public int[] dataMemory = new int[0];
        public int[] programMemory = new int[0];
        public int[] stackContents = new int[0];
        public int queueHead, queueTail, queueCount, queueCapacity;
        public int[] queueContents = new int[0];
        public String lastError;
        /** Core status word: READY, LOADED, RESET, RUNNING, PAUSED, HALTED. */
        public String status;
        /** Description of the last executed instruction, or null. */
        public String lastInstruction;

        /**
         * Returns a status string for UI display.
         */
        public String getStatusString() {
            if (status != null) return status;
            if (halted) return "HALTED";
            if (executing) return "RUNNING";
            return "READY";
        }
    }

    private IPCProtocol() {} // utility class
}
