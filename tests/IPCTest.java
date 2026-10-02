import Main.IPC.IPCProtocol;
import Main.Logger.LogMessage;
import Main.Logger.LogType;
import Main.core.command.Command;
import Main.core.command.CommandMessage;
import Main.core.command.CommandResponse;
import Main.core.state.CoreStateSnapshot;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Week-04 Master Integration Test Suite — Inter-Process Communication (IPC).
 *
 * Tests:
 * 1. Protocol serialization/deserialization for commands, responses, and log messages.
 * 2. Real subprocess execution of CoreProcess in --ipc mode.
 * 3. Real subprocess execution of LoggerProcess in --ipc mode.
 * 4. Piped multi-process interaction (UI -> Core -> Logger).
 * 5. Error handling and malformed message resilience.
 */
public class IPCTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println(" Week-04 IPC & Multi-Process Test Suite");
        System.out.println("========================================");

        testCommandSerialization();
        testResponseSerialization();
        testLogMessageSerialization();
        testCoreProcessSubprocessIPC();
        testLoggerProcessSubprocessIPC();
        testMalformedMessageResilience();

        System.out.println("\n========================================");
        System.out.printf(" Results: %d PASSED, %d FAILED\n", passed, failed);
        System.out.println("========================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void pass(String testName) {
        System.out.println("  PASS  " + testName);
        passed++;
    }

    private static void fail(String testName, String reason) {
        System.err.println("  FAIL  " + testName + " — " + reason);
        failed++;
    }

    // ==================== Test 1: Command Protocol ====================

    private static void testCommandSerialization() {
        try {
            // Test simple command
            CommandMessage step = new CommandMessage(Command.STEP);
            String serStep = IPCProtocol.serializeCommand(step);
            CommandMessage deStep = IPCProtocol.deserializeCommand(serStep);
            assert deStep != null && deStep.getCommand() == Command.STEP : "STEP deserialize mismatch";

            // Test command with payload
            int[] program = { 0x74, 0x42, 0x24, 0x05, 0xFF };
            CommandMessage load = new CommandMessage(Command.LOAD, program);
            String serLoad = IPCProtocol.serializeCommand(load);
            CommandMessage deLoad = IPCProtocol.deserializeCommand(serLoad);
            assert deLoad != null && deLoad.getCommand() == Command.LOAD : "LOAD deserialize mismatch";
            assert deLoad.getPayload() != null && deLoad.getPayload().length == 5 : "Payload length mismatch";
            assert deLoad.getPayload()[1] == 0x42 : "Payload byte mismatch";

            pass("TC01 Command serialization & deserialization");
        } catch (Throwable t) {
            fail("TC01 Command serialization", t.getMessage());
        }
    }

    // ==================== Test 2: Response Protocol ====================

    private static void testResponseSerialization() {
        try {
            // Test error response
            CommandResponse errResp = CommandResponse.error("Test error message");
            String serErr = IPCProtocol.serializeResponse(errResp);
            IPCProtocol.ParsedResponse parsedErr = IPCProtocol.parseResponse(serErr);
            assert parsedErr != null && !parsedErr.success : "Error response should not be success";
            assert "Test error message".equals(parsedErr.errorMessage) : "Error message mismatch";

            Main.CPU.CPU cpu = new Main.CPU.CPU();
            cpu.getRegisters().setAccumulator(0x42);
            cpu.getRegisters().setPC(0x1234);
            cpu.getRegisters().setSP(0x09);
            cpu.getRegisters().setCarry(true);
            cpu.getRegisters().setOverflow(true);
            cpu.getRegisters().setR(0, 0x01);
            cpu.getRegisters().setR(7, 0x08);

            // Stack setup (SP=0x09 -> depth 2: RAM[0x08], RAM[0x09])
            cpu.getMemory().writeData(0x08, 0x42);
            cpu.getMemory().writeData(0x09, 0x99);

            // Data memory test byte
            cpu.getMemory().writeData(0x50, 0xAA);

            // Set up queue in RAM: head=0, tail=2, count=2, buffer=[0x11, 0x22]
            cpu.getMemory().writeData(0x30, 0);
            cpu.getMemory().writeData(0x31, 2);
            cpu.getMemory().writeData(0x32, 2);
            cpu.getMemory().writeData(0x40, 0x11);
            cpu.getMemory().writeData(0x41, 0x22);

            Main.Queue.Queue queue = new Main.Queue.Queue(cpu.getMemory());

            CoreStateSnapshot snapshot = CoreStateSnapshot.capture(
                    cpu, queue, false, null, 42
            );

            CommandResponse okResp = CommandResponse.ok(snapshot);
            String serOk = IPCProtocol.serializeResponse(okResp);
            IPCProtocol.ParsedResponse parsedOk = IPCProtocol.parseResponse(serOk);

            assert parsedOk != null && parsedOk.success : "OK response should be success";
            assert parsedOk.snapshot != null : "Snapshot must not be null";
            assert parsedOk.snapshot.pc == 0x1234 : "PC mismatch: " + parsedOk.snapshot.pc;
            assert parsedOk.snapshot.acc == 0x42 : "ACC mismatch";
            assert parsedOk.snapshot.sp == 0x09 : "SP mismatch";
            assert parsedOk.snapshot.cy : "CY mismatch";
            assert !parsedOk.snapshot.ac : "AC mismatch";
            assert parsedOk.snapshot.ov : "OV mismatch";
            assert parsedOk.snapshot.r[0] == 0x01 && parsedOk.snapshot.r[7] == 0x08 : "R registers mismatch";
            assert parsedOk.snapshot.dataMemory[0x50] == 0xAA : "RAM byte mismatch";
            assert parsedOk.snapshot.stackContents.length == 2 : "Stack length mismatch";
            assert parsedOk.snapshot.queueContents.length == 2 : "Queue length mismatch";
            assert parsedOk.snapshot.cycleCount == 42 : "Cycle count mismatch";

            pass("TC02 Response & Snapshot serialization & deserialization");
        } catch (Throwable t) {
            fail("TC02 Response serialization", t.getMessage());
        }
    }

    // ==================== Test 3: LogMessage Protocol ====================

    private static void testLogMessageSerialization() {
        try {
            LogMessage msg = new LogMessage(LogType.SYSTEM, "Simulator started", "Version 4.0");
            String ser = msg.serialize();
            LogMessage de = LogMessage.deserialize(ser);

            assert de != null : "Deserialized LogMessage is null";
            assert de.getType() == LogType.SYSTEM : "LogType mismatch: " + de.getType();
            assert "Simulator started".equals(de.getMessage()) : "Message mismatch: " + de.getMessage();
            assert "Version 4.0".equals(de.getDetails()) : "Details mismatch: " + de.getDetails();
            assert de.getTimestamp() == msg.getTimestamp() : "Timestamp mismatch";

            // Test execution and error types
            LogMessage execMsg = new LogMessage(LogType.EXECUTION, "PC=0x0002 MOV A,#42");
            LogMessage deExec = LogMessage.deserialize(execMsg.serialize());
            assert deExec != null && deExec.getType() == LogType.EXECUTION : "EXECUTION type mismatch";

            LogMessage errMsg = new LogMessage(LogType.ERROR, "Unknown opcode 0xEE");
            LogMessage deErr = LogMessage.deserialize(errMsg.serialize());
            assert deErr != null && deErr.getType() == LogType.ERROR : "ERROR type mismatch";

            pass("TC03 LogMessage serialization & deserialization (all types)");
        } catch (Throwable t) {
            fail("TC03 LogMessage serialization", t.getMessage());
        }
    }

    // ==================== Test 4: Subprocess Core IPC ====================

    private static void testCoreProcessSubprocessIPC() {
        Process core = null;
        try {
            String javaBin = getJavaBinary();
            String classpath = System.getProperty("java.class.path");

            ProcessBuilder pb = new ProcessBuilder(
                    javaBin, "-cp", classpath, "Main.core.CoreProcess", "--ipc"
            );
            core = pb.start();

            PrintWriter toCore = new PrintWriter(new OutputStreamWriter(core.getOutputStream()), true);
            BufferedReader fromCore = new BufferedReader(new InputStreamReader(core.getInputStream()));

            // Send GET_STATE
            toCore.println(IPCProtocol.serializeCommand(new CommandMessage(Command.GET_STATE)));
            String line = fromCore.readLine();
            assert line != null : "Core stdout returned null";

            IPCProtocol.ParsedResponse resp = IPCProtocol.parseResponse(line);
            assert resp != null && resp.success : "Core GET_STATE failed";
            assert resp.snapshot != null && resp.snapshot.pc == 0x0000 : "Initial PC != 0";
            assert resp.snapshot.sp == 0x07 : "Initial SP != 0x07";

            // Send LOAD: MOV A, #0x55, HALT
            int[] prog = { 0x74, 0x55, 0xFF };
            toCore.println(IPCProtocol.serializeCommand(new CommandMessage(Command.LOAD, prog)));
            line = fromCore.readLine();
            resp = IPCProtocol.parseResponse(line);
            assert resp != null && resp.success : "Core LOAD failed";

            // Send STEP
            toCore.println(IPCProtocol.serializeCommand(new CommandMessage(Command.STEP)));
            line = fromCore.readLine();
            resp = IPCProtocol.parseResponse(line);
            assert resp != null && resp.success : "Core STEP failed";
            assert resp.snapshot.acc == 0x55 : "ACC expected 0x55, got: " + resp.snapshot.acc;

            // Send SHUTDOWN
            toCore.println(IPCProtocol.serializeCommand(new CommandMessage(Command.SHUTDOWN)));
            line = fromCore.readLine();
            resp = IPCProtocol.parseResponse(line);
            assert resp != null && resp.success : "Core SHUTDOWN failed";

            // Wait for core to terminate
            boolean exited = core.waitFor(3, TimeUnit.SECONDS);
            assert exited : "Core process did not exit within timeout";
            assert core.exitValue() == 0 : "Core process non-zero exit code: " + core.exitValue();

            pass("TC04 CoreProcess subprocess IPC execution (GET_STATE, LOAD, STEP, SHUTDOWN)");
        } catch (Throwable t) {
            fail("TC04 CoreProcess subprocess IPC", t.getMessage());
        } finally {
            if (core != null && core.isAlive()) {
                core.destroyForcibly();
            }
        }
    }

    // ==================== Test 5: Subprocess Logger IPC ====================

    private static void testLoggerProcessSubprocessIPC() {
        Process logger = null;
        try {
            String javaBin = getJavaBinary();
            String classpath = System.getProperty("java.class.path");

            ProcessBuilder pb = new ProcessBuilder(
                    javaBin, "-cp", classpath, "Main.Logger.LoggerProcess", "--ipc"
            );
            logger = pb.start();

            PrintWriter toLogger = new PrintWriter(new OutputStreamWriter(logger.getOutputStream()), true);

            // Send Log messages
            toLogger.println(new LogMessage(LogType.SYSTEM, "Test suite started").serialize());
            toLogger.println(new LogMessage(LogType.EXECUTION, "PC=0x0000 MOV A,#0x42", "A=0x42").serialize());
            toLogger.println(new LogMessage(LogType.ERROR, "Simulated Error Event").serialize());

            // Close stdin to signal EOF and graceful shutdown
            toLogger.close();

            boolean exited = logger.waitFor(3, TimeUnit.SECONDS);
            assert exited : "Logger process did not exit after stdin closed";

            // Verify simulator.log file exists and has content
            File logFile = new File("simulator.log");
            assert logFile.exists() : "simulator.log file does not exist";
            String logContent = new String(Files.readAllBytes(Paths.get("simulator.log")));
            assert logContent.contains("SYSTEM") : "Log missing SYSTEM entry";
            assert logContent.contains("EXECUTION") : "Log missing EXECUTION entry";
            assert logContent.contains("ERROR") : "Log missing ERROR entry";

            pass("TC05 LoggerProcess subprocess IPC execution and log file writing");
        } catch (Throwable t) {
            fail("TC05 LoggerProcess subprocess IPC", t.getMessage());
        } finally {
            if (logger != null && logger.isAlive()) {
                logger.destroyForcibly();
            }
        }
    }

    // ==================== Test 6: Malformed Input Resilience ====================

    private static void testMalformedMessageResilience() {
        try {
            // Malformed commands
            assert IPCProtocol.deserializeCommand(null) == null : "null command should return null";
            assert IPCProtocol.deserializeCommand("CMD|NOT_A_VALID_COMMAND") == null : "garbage command should return null";
            assert IPCProtocol.deserializeCommand("CMD|LOAD|NOT_A_NUMBER") == null : "LOAD with bad number should return null";
            assert IPCProtocol.deserializeCommand("CMD|LOAD") != null : "LOAD without payload should return LOAD command";

            // Malformed responses
            assert IPCProtocol.parseResponse(null) == null : "null response should return null";
            assert IPCProtocol.parseResponse("") == null : "empty response should return null";
            assert IPCProtocol.parseResponse("UNKNOWN_HEADER|abc") == null : "bad header should return null";

            // Malformed log messages
            assert LogMessage.deserialize(null) == null : "null log should return null";
            assert LogMessage.deserialize("") == null : "empty log should return null";
            assert LogMessage.deserialize("NOT_ENOUGH_PARTS") == null : "short log string should return null";

            pass("TC06 Malformed message resilience and edge case handling");
        } catch (Throwable t) {
            fail("TC06 Malformed message resilience", t.getMessage());
        }
    }

    private static String getJavaBinary() {
        String javaHome = System.getProperty("java.home");
        File binDir = new File(javaHome, "bin");
        File javaExe = new File(binDir, "java.exe");
        if (javaExe.exists()) {
            return javaExe.getAbsolutePath();
        }
        File java = new File(binDir, "java");
        if (java.exists()) {
            return java.getAbsolutePath();
        }
        return "java";
    }
}
