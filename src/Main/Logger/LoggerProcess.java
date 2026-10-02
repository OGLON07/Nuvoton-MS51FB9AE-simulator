package Main.Logger;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Independent Logger Process.
 *
 * Responsibilities:
 * - Receive log messages
 * - Process log messages
 * - Record EXECUTION events
 * - Record ERROR events
 * - Record SYSTEM events
 * - Store logs in a file
 * - Handle invalid messages safely
 *
 * <h3>IPC Mode (--ipc)</h3>
 * When started with {@code --ipc}, reads serialized LogMessages
 * from stdin (piped from Core's stderr). Each line is deserialized
 * and written to the log file.
 *
 * <h3>In-process Mode (default)</h3>
 * Messages are submitted via {@link #receiveLog(LogMessage)}.
 */
public class LoggerProcess {

    /** Log file path; override with -Dsimulator.log=PATH (default: ./simulator.log). */
    private static final String LOG_FILE = System.getProperty("simulator.log", "simulator.log");

    /** Sentinel placed on the queue by stop(); tells the worker to finish. */
    private static final LogMessage STOP_MARKER = new LogMessage(LogType.SYSTEM, "stop");

    private final BlockingQueue<LogMessage> logQueue;

    private volatile boolean running;

    private Thread loggingThread;

    private PrintWriter logWriter;

    private final DateTimeFormatter timeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public LoggerProcess() {
        logQueue = new LinkedBlockingQueue<>();
        running = false;
    }

    /**
     * Starts the independent Logger process.
     */
    public void start() {

        if (running) {
            System.out.println("Logger process is already running.");
            return;
        }

        try {
            logWriter = new PrintWriter(
                    new FileWriter(LOG_FILE, true)
            );
        } catch (IOException e) {
            System.err.println(
                    "Logger error: Unable to open log file."
            );
            return;
        }

        running = true;

        loggingThread = new Thread(
                this::processLogs,
                "Logger-Thread"
        );

        loggingThread.start();

        System.out.println("Logger process started.");
    }

    /**
     * Receives a log message.
     *
     * This is intentionally kept independent of IPC.
     * During integration, the Core/IPC layer can call this method.
     */
    public void receiveLog(LogMessage logMessage) {

        if (!running) {
            System.err.println(
                    "Logger is not running. Message ignored."
            );
            return;
        }

        if (logMessage == null) {
            System.err.println(
                    "Logger received a null message."
            );
            return;
        }

        if (!logMessage.isValid()) {
            System.err.println(
                    "Logger received an invalid message."
            );
            return;
        }

        logQueue.offer(logMessage);
    }

    /**
     * Worker loop: blocks on the queue (no polling, no sleeping) and
     * writes each message until the stop marker arrives. Everything queued
     * before the marker is still written, so no log lines are lost.
     */
    private void processLogs() {
        try {
            while (true) {
                LogMessage logMessage = logQueue.take();
                if (logMessage == STOP_MARKER) {
                    break;
                }
                try {
                    writeLog(logMessage);
                } catch (RuntimeException e) {
                    // A bad message must not kill the logger.
                    System.err.println("Logger processing error: " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Writes a log message to both console and log file.
     */
    private synchronized void writeLog(LogMessage logMessage) {

        if (logMessage == null || !logMessage.isValid()) {
            return;
        }

        String timestamp;
        if (logMessage.getTimestamp() > 0) {
            timestamp = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(logMessage.getTimestamp()),
                    ZoneId.systemDefault()
            ).format(timeFormatter);
        } else {
            timestamp = LocalDateTime.now().format(timeFormatter);
        }

        String formattedLog =
                "[" + timestamp + "] "
                        + logMessage.toString();

        if (logMessage.getDetails() != null && !logMessage.getDetails().isEmpty()) {
            formattedLog += " | " + logMessage.getDetails();
        }

        // Console output (stdout of Logger process)
        System.out.println(formattedLog);

        // File output
        if (logWriter != null) {
            logWriter.println(formattedLog);
            logWriter.flush();
        }
    }

    /**
     * Stops the Logger: refuses new messages, lets the worker drain what is
     * already queued, then closes the file.
     */
    public void stop() {

        if (!running) {
            return;
        }

        running = false;               // receiveLog() now rejects new messages
        logQueue.offer(STOP_MARKER);   // worker exits after writing everything before it

        if (loggingThread != null) {
            try {
                loggingThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (logWriter != null) {
            logWriter.flush();
            logWriter.close();
            logWriter = null;
        }

        System.out.println("Logger process stopped.");
    }

    /**
     * Returns whether the Logger is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the number of messages waiting to be processed.
     */
    public int getPendingLogCount() {
        return logQueue.size();
    }

    // ==================== IPC Mode ====================

    /**
     * Runs the Logger in IPC mode: reads serialized LogMessages
     * from stdin (piped from Core's stderr).
     */
    private static void runIPCMode() {
        LoggerProcess logger = new LoggerProcess();
        logger.start();

        // Write a startup marker
        logger.receiveLog(new LogMessage(LogType.SYSTEM, "Logger process started in IPC mode"));

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                LogMessage msg = LogMessage.deserialize(line);

                if (msg != null && msg.isValid()) {
                    logger.receiveLog(msg);
                } else {
                    // Malformed line: record it (truncated) as an ERROR and keep going.
                    String shown = line.length() > 200 ? line.substring(0, 200) + "..." : line;
                    logger.receiveLog(new LogMessage(LogType.ERROR,
                            "Malformed log message received", shown));
                }
            }
        } catch (Exception e) {
            System.err.println("[Logger] Error reading stdin: " + e.getMessage());
        }

        // EOF on stdin = Core closed its end of the pipe. stop() drains the queue.
        logger.stop();
    }

    // ==================== Entry Point ====================

    /**
     * Entry point. Supports:
     * <ul>
     *   <li>{@code --ipc}: IPC mode — reads LogMessages from stdin</li>
     *   <li>Default: standalone self-test mode</li>
     * </ul>
     */
    public static void main(String[] args) {

        // Check for IPC mode
        if (args.length > 0 && "--ipc".equals(args[0])) {
            runIPCMode();
            return;
        }

        // Standalone self-test (original behavior)
        LoggerProcess logger = new LoggerProcess();

        logger.start();

        // Sample execution logs
        logger.receiveLog(
                new LogMessage(
                        LogType.EXECUTION,
                        "PC=00 Instruction=MOV_A_IMM"
                )
        );

        logger.receiveLog(
                new LogMessage(
                        LogType.EXECUTION,
                        "PC=02 Instruction=ADD_A_IMM"
                )
        );

        // Sample error log
        logger.receiveLog(
                new LogMessage(
                        LogType.ERROR,
                        "Unknown opcode 0xAB"
                )
        );

        // Another system event
        logger.receiveLog(
                new LogMessage(
                        LogType.SYSTEM,
                        "System event test"
                )
        );

        // Invalid messages for testing
        logger.receiveLog(null);

        logger.receiveLog(
                new LogMessage(
                        null,
                        "Invalid log type"
                )
        );

        logger.receiveLog(
                new LogMessage(
                        LogType.ERROR,
                        ""
                )
        );

        logger.stop();
    }
}
