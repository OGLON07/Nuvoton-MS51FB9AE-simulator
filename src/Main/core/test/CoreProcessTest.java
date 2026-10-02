package Main.core.test;

import Main.CPU.CPU;
import Main.core.CoreCommandHandler;
import Main.core.CoreProcess;
import Main.core.command.Command;
import Main.core.command.CommandMessage;
import Main.core.command.CommandResponse;
import Main.core.state.CoreStateSnapshot;

/**
 * Standalone integration test suite for the headless Core Process.
 *
 * Executes entirely without Swing/AWT or any UI dependency.
 * Uses plain {@code assert} statements — run with {@code -ea}.
 *
 * <h3>Verification checklist covered</h3>
 * <ol>
 *   <li>Independent Startup</li>
 *   <li>Memory &amp; Register Persistence (LOAD → STEP)</li>
 *   <li>Instruction Execution (STEP updates PC/registers/stack/flags)</li>
 *   <li>Continuous Run &amp; Pause (RUN + PAUSE thread transitions)</li>
 *   <li>Stack &amp; Queue Behaviour</li>
 *   <li>Snapshot Accuracy (GET_STATE)</li>
 *   <li>Invalid Command Resilience</li>
 *   <li>Clean Shutdown</li>
 * </ol>
 */
public class CoreProcessTest {

    private static int passed = 0;
    private static int failed = 0;

    // ================================================================
    //  main
    // ================================================================

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  Core Process — Integration Test Suite");
        System.out.println("==============================================\n");

        // 1. Independent Startup
        test_IndependentStartup();

        // 2. Memory & Register Persistence (LOAD)
        test_LoadPersistence();

        // 3. Instruction Execution (STEP)
        test_StepExecution();

        // 4. Continuous Run & Pause
        test_RunAndPause();

        // 5. Stack & Queue Behaviour
        test_StackBehaviour();
        test_QueueBehaviour();

        // 6. Snapshot Accuracy
        test_SnapshotAccuracy();

        // 7. Invalid Command Resilience
        test_InvalidCommandResilience();

        // 8. Clean Shutdown
        test_CleanShutdown();

        // 9. Async command loop
        test_AsyncCommandLoop();

        // 10. RESET clears all state
        test_ResetClearsState();

        // Summary
        System.out.println("\n==============================================");
        System.out.println("  Results: " + passed + " passed, " +
                           failed + " failed");
        System.out.println("==============================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // ================================================================
    //  1. Independent Startup
    // ================================================================

    private static void test_IndependentStartup() {
        try {
            CoreProcess core = new CoreProcess();
            // Must not throw NPE or reference any UI class
            CommandResponse resp = core.submitAndWait(Command.GET_STATE);
            assert resp.isSuccess() : "GET_STATE should succeed";
            assert resp.getSnapshot() != null : "Snapshot must not be null";
            pass("test_IndependentStartup");
        } catch (Throwable t) {
            fail("test_IndependentStartup", t);
        }
    }

    // ================================================================
    //  2. Memory & Register Persistence
    // ================================================================

    private static void test_LoadPersistence() {
        try {
            CoreProcess core = new CoreProcess();

            // MOV A, #0x42 → 0x74 0x42, then HALT → 0xFF
            int[] program = { 0x74, 0x42, 0xFF };

            CommandResponse loadResp = core.submitAndWait(
                    new CommandMessage(Command.LOAD, program));
            assert loadResp.isSuccess() : "LOAD should succeed";

            CoreStateSnapshot snap = loadResp.getSnapshot();
            assert snap != null : "LOAD must return a snapshot";
            assert snap.getProgramSize() == 3 :
                    "Program size should be 3, got " + snap.getProgramSize();

            // Verify the bytes persisted in program memory
            int[] pm = snap.getProgramMemory();
            assert pm[0] == 0x74 : "PM[0] should be 0x74";
            assert pm[1] == 0x42 : "PM[1] should be 0x42";
            assert pm[2] == 0xFF : "PM[2] should be 0xFF";

            // PC should be 0 after LOAD (reset-like)
            assert snap.getPC() == 0 : "PC should be 0 after LOAD";

            pass("test_LoadPersistence");
        } catch (Throwable t) {
            fail("test_LoadPersistence", t);
        }
    }

    // ================================================================
    //  3. Instruction Execution (STEP)
    // ================================================================

    private static void test_StepExecution() {
        try {
            CoreProcess core = new CoreProcess();

            // Program: MOV A,#0x10 (0x74 0x10),
            //          ADD A,#0x05 (0x24 0x05),
            //          HALT (0xFF)
            int[] program = { 0x74, 0x10, 0x24, 0x05, 0xFF };
            core.submitAndWait(new CommandMessage(Command.LOAD, program));

            // STEP 1: MOV A,#0x10
            CommandResponse r1 = core.submitAndWait(Command.STEP);
            assert r1.isSuccess();
            CoreStateSnapshot s1 = r1.getSnapshot();
            assert s1.getAccumulator() == 0x10 :
                    "A should be 0x10 after MOV, got 0x" +
                    Integer.toHexString(s1.getAccumulator());
            assert s1.getPC() == 2 :
                    "PC should be 2 after 2-byte MOV, got " + s1.getPC();

            // STEP 2: ADD A,#0x05
            CommandResponse r2 = core.submitAndWait(Command.STEP);
            assert r2.isSuccess();
            CoreStateSnapshot s2 = r2.getSnapshot();
            assert s2.getAccumulator() == 0x15 :
                    "A should be 0x15 after ADD, got 0x" +
                    Integer.toHexString(s2.getAccumulator());
            assert s2.getPC() == 4 :
                    "PC should be 4, got " + s2.getPC();

            // STEP 3: HALT
            CommandResponse r3 = core.submitAndWait(Command.STEP);
            assert r3.isSuccess();
            assert r3.getSnapshot().isHalted() :
                    "CPU should be halted after HALT";

            // STEP after HALT should return error
            CommandResponse r4 = core.submitAndWait(Command.STEP);
            assert !r4.isSuccess() :
                    "STEP on halted CPU should return error";

            pass("test_StepExecution");
        } catch (Throwable t) {
            fail("test_StepExecution", t);
        }
    }

    // ================================================================
    //  4. Continuous Run & Pause
    // ================================================================

    private static void test_RunAndPause() {
        try {
            CoreProcess core = new CoreProcess();

            // Longer program to give RUN time to execute:
            // MOV A,#0x01 (0x74 0x01)
            // INC A       (0x04)       — repeated 50 times
            // HALT        (0xFF)
            int[] program = new int[2 + 50 + 1];
            program[0] = 0x74;  // MOV A,#0x01
            program[1] = 0x01;
            for (int i = 0; i < 50; i++) {
                program[2 + i] = 0x04; // INC A
            }
            program[52] = 0xFF; // HALT

            core.submitAndWait(new CommandMessage(Command.LOAD, program));
            core.getHandler().setStepDelayMs(0);

            // Start RUN
            CommandResponse runResp = core.submitAndWait(Command.RUN);
            assert runResp.isSuccess() : "RUN should succeed";

            // Wait for RUN to complete (HALT will stop it)
            Thread.sleep(500);

            // PAUSE after HALT is a safe no-op
            CommandResponse pauseResp = core.submitAndWait(Command.PAUSE);
            assert pauseResp.isSuccess() : "PAUSE should succeed";

            CoreStateSnapshot snap = pauseResp.getSnapshot();
            assert snap.isHalted() :
                    "CPU should be halted after running to HALT";
            assert snap.getAccumulator() == 0x33 :
                    "A should be 0x33 (1 + 50 incs = 51 = 0x33), got 0x" +
                    Integer.toHexString(snap.getAccumulator());

            pass("test_RunAndPause");
        } catch (Throwable t) {
            fail("test_RunAndPause", t);
        }
    }

    // ================================================================
    //  5a. Stack Behaviour
    // ================================================================

    private static void test_StackBehaviour() {
        try {
            CoreProcess core = new CoreProcess();

            // Program: MOV A,#0x42 (0x74 0x42)
            //          PUSH A      (0xC0)
            //          MOV A,#0x00 (0x74 0x00)  — corrupt ACC
            //          POP A       (0xD0)       — should restore 0x42
            //          HALT        (0xFF)
            int[] program = { 0x74, 0x42, 0xC0, 0x74, 0x00, 0xD0, 0xFF };
            core.submitAndWait(new CommandMessage(Command.LOAD, program));

            // Step through each instruction
            core.submitAndWait(Command.STEP); // MOV A,#0x42
            CommandResponse r2 = core.submitAndWait(Command.STEP); // PUSH A
            CoreStateSnapshot afterPush = r2.getSnapshot();
            assert afterPush.getSP() == 0x08 :
                    "SP should be 0x08 after PUSH, got 0x" +
                    Integer.toHexString(afterPush.getSP());
            // Verify stack contents
            int[] stack = afterPush.getStackContents();
            assert stack.length == 1 :
                    "Stack should have 1 element, got " + stack.length;
            assert stack[0] == 0x42 :
                    "Stack[0] should be 0x42, got 0x" +
                    Integer.toHexString(stack[0]);

            core.submitAndWait(Command.STEP); // MOV A,#0x00
            CommandResponse r4 = core.submitAndWait(Command.STEP); // POP A
            CoreStateSnapshot afterPop = r4.getSnapshot();
            assert afterPop.getAccumulator() == 0x42 :
                    "A should be restored to 0x42 after POP, got 0x" +
                    Integer.toHexString(afterPop.getAccumulator());
            assert afterPop.getSP() == 0x07 :
                    "SP should return to 0x07 after POP, got 0x" +
                    Integer.toHexString(afterPop.getSP());

            pass("test_StackBehaviour");
        } catch (Throwable t) {
            fail("test_StackBehaviour", t);
        }
    }

    // ================================================================
    //  5b. Queue Behaviour
    // ================================================================

    private static void test_QueueBehaviour() {
        try {
            CoreProcess core = new CoreProcess();

            // The Queue reads from fixed RAM locations:
            //   HEAD = RAM[0x30], TAIL = RAM[0x31], COUNT = RAM[0x32]
            //   Buffer = RAM[0x40..0x47]
            //
            // We'll load a program that writes queue metadata via
            // MOV addr,A instructions, simulating an enqueue.
            //
            // Plan:
            //   MOV A,#0x00  → set head = 0
            //   MOV 0x30,A   → write HEAD
            //   MOV A,#0x01  → set tail = 1
            //   MOV 0x31,A   → write TAIL
            //   MOV A,#0x01  → set count = 1
            //   MOV 0x32,A   → write COUNT
            //   MOV A,#0xBB  → value to enqueue
            //   MOV 0x40,A   → write buffer[0]
            //   HALT

            int[] program = {
                0x74, 0x00,       // MOV A,#0x00
                0xF5, 0x30,       // MOV 0x30,A  (HEAD=0)
                0x74, 0x01,       // MOV A,#0x01
                0xF5, 0x31,       // MOV 0x31,A  (TAIL=1)
                0x74, 0x01,       // MOV A,#0x01
                0xF5, 0x32,       // MOV 0x32,A  (COUNT=1)
                0x74, 0xBB,       // MOV A,#0xBB
                0xF5, 0x40,       // MOV 0x40,A  (buffer[0]=0xBB)
                0xFF              // HALT
            };

            core.submitAndWait(new CommandMessage(Command.LOAD, program));
            core.getHandler().setStepDelayMs(0);
            // RUN to completion
            core.submitAndWait(Command.RUN);
            Thread.sleep(300);

            CommandResponse stateResp = core.submitAndWait(Command.GET_STATE);
            CoreStateSnapshot snap = stateResp.getSnapshot();

            assert snap.getQueueHead() == 0 :
                    "Queue head should be 0, got " + snap.getQueueHead();
            assert snap.getQueueTail() == 1 :
                    "Queue tail should be 1, got " + snap.getQueueTail();
            assert snap.getQueueCount() == 1 :
                    "Queue count should be 1, got " + snap.getQueueCount();

            int[] qContents = snap.getQueueContents();
            assert qContents.length == 1 :
                    "Queue should have 1 element, got " + qContents.length;
            assert qContents[0] == 0xBB :
                    "Queue[0] should be 0xBB, got 0x" +
                    Integer.toHexString(qContents[0]);

            pass("test_QueueBehaviour");
        } catch (Throwable t) {
            fail("test_QueueBehaviour", t);
        }
    }

    // ================================================================
    //  6. Snapshot Accuracy
    // ================================================================

    private static void test_SnapshotAccuracy() {
        try {
            CoreProcess core = new CoreProcess();

            // Program: MOV A,#0xAA, MOV R3,#0x55, HALT
            // MOV A,#0xAA  → 0x74 0xAA
            // MOV R3,#0x55 → 0x7B 0x55 (0x78 + 3 = 0x7B)
            // HALT → 0xFF
            int[] program = { 0x74, 0xAA, 0x7B, 0x55, 0xFF };
            core.submitAndWait(new CommandMessage(Command.LOAD, program));

            core.submitAndWait(Command.STEP); // MOV A,#0xAA
            core.submitAndWait(Command.STEP); // MOV R3,#0x55

            CommandResponse resp = core.submitAndWait(Command.GET_STATE);
            CoreStateSnapshot snap = resp.getSnapshot();

            // Verify register values in snapshot
            assert snap.getAccumulator() == 0xAA :
                    "Snapshot A should be 0xAA";
            assert snap.getRegister(3) == 0x55 :
                    "Snapshot R3 should be 0x55";
            assert snap.getPC() == 4 :
                    "Snapshot PC should be 4";
            assert snap.getSP() == 0x07 :
                    "Snapshot SP should be 0x07 (no pushes)";
            assert snap.getBRegister() == 0 :
                    "Snapshot B should be 0 (untouched)";
            assert !snap.isHalted() :
                    "Snapshot should not be halted yet";
            assert !snap.isExecuting() :
                    "Snapshot should not show executing (STEP is sync)";
            assert snap.getCycleCount() == 2 :
                    "Cycle count should be 2, got " + snap.getCycleCount();

            // Verify data memory reflects initial state (all zeros
            // except where stack/queue might write)
            int[] dm = snap.getDataMemory();
            assert dm.length == 256 :
                    "Data memory should be 256 bytes";

            // Verify program memory
            int[] pm = snap.getProgramMemory();
            assert pm.length == 5 :
                    "Program memory snapshot should be 5 bytes";
            assert pm[0] == 0x74 && pm[1] == 0xAA :
                    "Program memory should match loaded bytes";

            pass("test_SnapshotAccuracy");
        } catch (Throwable t) {
            fail("test_SnapshotAccuracy", t);
        }
    }

    // ================================================================
    //  7. Invalid Command Resilience
    // ================================================================

    private static void test_InvalidCommandResilience() {
        try {
            CoreProcess core = new CoreProcess();

            // a) Null CommandMessage via handler directly
            CommandResponse r1 = core.getHandler().handleCommand(null);
            assert !r1.isSuccess() :
                    "Null command should return error response";
            assert r1.getErrorMessage() != null :
                    "Error response must have a message";

            // b) LOAD with null payload
            CommandResponse r2 = core.submitAndWait(
                    new CommandMessage(Command.LOAD));
            assert !r2.isSuccess() :
                    "LOAD with null payload should return error";

            // c) LOAD with empty payload
            CommandResponse r3 = core.submitAndWait(
                    new CommandMessage(Command.LOAD, new int[0]));
            assert !r3.isSuccess() :
                    "LOAD with empty payload should return error";

            // d) STEP with no program loaded (NOP at address 0 = 0x00)
            // should not crash — 0x00 is NOP
            CommandResponse r4 = core.submitAndWait(Command.STEP);
            // This may succeed (NOP increments PC) or error depending
            // on opcode 0x00 handling — it should NOT crash
            // The existing CPU handles 0x00 as NOP, so this should succeed
            assert r4.isSuccess() || r4.getErrorMessage() != null :
                    "STEP with no program should return a valid response";

            // e) STEP on halted CPU
            // Load a program that immediately halts
            int[] haltProg = { 0xFF };
            core.submitAndWait(new CommandMessage(Command.LOAD, haltProg));
            core.submitAndWait(Command.STEP); // Execute HALT
            CommandResponse r5 = core.submitAndWait(Command.STEP);
            assert !r5.isSuccess() :
                    "STEP on halted CPU should return error";

            // f) RUN on halted CPU
            CommandResponse r6 = core.submitAndWait(Command.RUN);
            assert !r6.isSuccess() :
                    "RUN on halted CPU should return error";

            // g) Double RUN
            core.submitAndWait(Command.RESET);
            int[] longProg = new int[100];
            for (int i = 0; i < 99; i++) longProg[i] = 0x04; // INC A
            longProg[99] = 0xFF; // HALT
            core.submitAndWait(new CommandMessage(Command.LOAD, longProg));
            core.submitAndWait(Command.RUN);
            Thread.sleep(50);
            CommandResponse r7 = core.submitAndWait(Command.RUN);
            // Either already halted or double-run error
            // Should NOT crash
            Thread.sleep(200);
            core.submitAndWait(Command.PAUSE);

            pass("test_InvalidCommandResilience");
        } catch (Throwable t) {
            fail("test_InvalidCommandResilience", t);
        }
    }

    // ================================================================
    //  8. Clean Shutdown
    // ================================================================

    private static void test_CleanShutdown() {
        try {
            CoreProcess core = new CoreProcess();
            Thread loopThread = core.startAsync();

            // Give the loop time to start
            Thread.sleep(100);
            assert core.isRunning() : "Core should be running";

            // Submit SHUTDOWN
            core.submitCommand(Command.SHUTDOWN);

            // Wait for the loop thread to terminate
            loopThread.join(3000);
            assert !loopThread.isAlive() :
                    "Command loop thread should have terminated";
            assert !core.isRunning() :
                    "Core should report not running after SHUTDOWN";

            pass("test_CleanShutdown");
        } catch (Throwable t) {
            fail("test_CleanShutdown", t);
        }
    }

    // ================================================================
    //  9. Async Command Loop
    // ================================================================

    private static void test_AsyncCommandLoop() {
        try {
            CoreProcess core = new CoreProcess();

            // Track responses
            final CommandResponse[] captured = new CommandResponse[3];
            final int[] idx = {0};

            core.setResponseListener(resp -> {
                if (idx[0] < captured.length) {
                    captured[idx[0]++] = resp;
                }
            });

            Thread loopThread = core.startAsync();
            Thread.sleep(100);

            // Submit commands asynchronously
            int[] program = { 0x74, 0x33, 0xFF }; // MOV A,#0x33; HALT
            core.submitCommand(new CommandMessage(Command.LOAD, program));
            Thread.sleep(50);
            core.submitCommand(Command.STEP);
            Thread.sleep(50);
            core.submitCommand(Command.SHUTDOWN);

            loopThread.join(3000);

            // Verify at least the LOAD response was captured
            assert captured[0] != null :
                    "First response (LOAD) should have been captured";
            assert captured[0].isSuccess() :
                    "LOAD response should be successful";

            pass("test_AsyncCommandLoop");
        } catch (Throwable t) {
            fail("test_AsyncCommandLoop", t);
        }
    }

    // ================================================================
    //  10. RESET Clears All State
    // ================================================================

    private static void test_ResetClearsState() {
        try {
            CoreProcess core = new CoreProcess();

            // Load and execute some instructions
            int[] program = { 0x74, 0xCC, 0xC0, 0xFF }; // MOV A,#0xCC; PUSH A; HALT
            core.submitAndWait(new CommandMessage(Command.LOAD, program));
            core.submitAndWait(Command.STEP); // MOV A,#0xCC
            core.submitAndWait(Command.STEP); // PUSH A

            // Verify state changed
            CoreStateSnapshot before = core.submitAndWait(Command.GET_STATE)
                                            .getSnapshot();
            assert before.getAccumulator() == 0xCC :
                    "A should be 0xCC before reset";
            assert before.getSP() == 0x08 :
                    "SP should be 0x08 before reset";

            // RESET
            CommandResponse resetResp = core.submitAndWait(Command.RESET);
            assert resetResp.isSuccess() : "RESET should succeed";

            CoreStateSnapshot after = resetResp.getSnapshot();
            assert after.getAccumulator() == 0 :
                    "A should be 0 after RESET";
            assert after.getSP() == 0x07 :
                    "SP should be 0x07 after RESET";
            assert after.getPC() == 0 :
                    "PC should be 0 after RESET";
            assert after.getBRegister() == 0 :
                    "B should be 0 after RESET";
            assert after.getPSW() == 0 :
                    "PSW should be 0 after RESET";
            assert !after.isHalted() :
                    "CPU should not be halted after RESET";
            assert after.getCycleCount() == 0 :
                    "Cycle count should be 0 after RESET";

            // Verify all R0-R7 are zero
            for (int i = 0; i < 8; i++) {
                assert after.getRegister(i) == 0 :
                        "R" + i + " should be 0 after RESET";
            }

            pass("test_ResetClearsState");
        } catch (Throwable t) {
            fail("test_ResetClearsState", t);
        }
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private static void pass(String name) {
        passed++;
        System.out.println("[PASS] " + name);
    }

    private static void fail(String name, Throwable t) {
        failed++;
        System.out.println("[FAIL] " + name + " — " + t.getMessage());
        t.printStackTrace(System.out);
    }
}
