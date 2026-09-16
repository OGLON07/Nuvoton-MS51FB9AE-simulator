import Main.CPU.CPU;
import Main.Memory.Memory;
import Main.Queue.Queue;

/**
 * Week-03 FIFO Queue Tests
 *
 * Tests the FIFO circular buffer implementation in RAM:
 * - Single enqueue and dequeue
 * - Multiple enqueues and FIFO ordering
 * - Empty condition check
 * - Full condition check (capacity = 8)
 * - Circular buffer wrap-around behavior
 * - Full assembly demo program execution
 *
 * No external test framework required — plain assertions with console reporting.
 */
public class QueueTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        System.out.println(
                "========================================\n" +
                " Week-03 Queue Test Suite\n" +
                "========================================"
        );

        testSingleEnqueueDequeue();
        testMultipleEnqueueFifoOrder();
        testEmptyCondition();
        testFullCondition();
        testCircularBufferWrapAround();
        testQueueDemoProgramExecution();

        System.out.println(
                "\n========================================\n" +
                " Results: " + passed + " PASSED, " +
                failed + " FAILED\n" +
                "========================================"
        );

        if (failed > 0) {
            System.exit(1);
        }
    }

    // -------------------------------------------------------
    // TC01: Enqueue 1 value (0x42), dequeue returns same value
    // -------------------------------------------------------
    private static void testSingleEnqueueDequeue() {
        CPU cpu = new CPU();
        Queue queue = new Queue(cpu.getMemory());

        // Assembly:
        // Init: head=0, tail=0, count=0
        // Enqueue 0x42: buffer[0]=0x42, tail=1, count=1
        // Dequeue: A=buffer[0], head=1, count=0
        // Save dequeued A to RAM[0x50]
        cpu.getMemory().loadProgram(new int[]{
                // Init
                0x74, 0x00, 0xF5, 0x30, 0xF5, 0x31, 0xF5, 0x32,
                // Enqueue 0x42
                0x74, 0x42, 0xF5, 0x40,
                0x74, 0x01, 0xF5, 0x31, 0xF5, 0x32,
                // Dequeue into A
                0xE5, 0x40, 0xF5, 0x50,
                0x74, 0x01, 0xF5, 0x30,
                0x74, 0x00, 0xF5, 0x32,
                0xFF
        });

        cpu.run();

        int dequeued = cpu.getMemory().readData(0x50);
        boolean ok = (dequeued == 0x42) && (queue.getCount() == 0) && queue.isEmpty();
        check("TC01 Single Enqueue & Dequeue",
                ok,
                "val=0x" + hex(dequeued) + ", count=" + queue.getCount(),
                "val=0x42, count=0");
    }

    // -------------------------------------------------------
    // TC02: Multiple enqueues (0x11, 0x22, 0x33) -> FIFO dequeue order
    // -------------------------------------------------------
    private static void testMultipleEnqueueFifoOrder() {
        CPU cpu = new CPU();

        cpu.getMemory().loadProgram(new int[]{
                // Init
                0x74, 0x00, 0xF5, 0x30, 0xF5, 0x31, 0xF5, 0x32,
                // Enqueue 0x11
                0x74, 0x11, 0xF5, 0x40,
                0x74, 0x01, 0xF5, 0x31, 0xF5, 0x32,
                // Enqueue 0x22
                0x74, 0x22, 0xF5, 0x41,
                0x74, 0x02, 0xF5, 0x31, 0xF5, 0x32,
                // Enqueue 0x33
                0x74, 0x33, 0xF5, 0x42,
                0x74, 0x03, 0xF5, 0x31, 0xF5, 0x32,
                // Dequeue 1 -> save to 0x50
                0xE5, 0x40, 0xF5, 0x50,
                0x74, 0x01, 0xF5, 0x30,
                0x74, 0x02, 0xF5, 0x32,
                // Dequeue 2 -> save to 0x51
                0xE5, 0x41, 0xF5, 0x51,
                0x74, 0x02, 0xF5, 0x30,
                0x74, 0x01, 0xF5, 0x32,
                // Dequeue 3 -> save to 0x52
                0xE5, 0x42, 0xF5, 0x52,
                0x74, 0x03, 0xF5, 0x30,
                0x74, 0x00, 0xF5, 0x32,
                0xFF
        });

        cpu.run();

        int v1 = cpu.getMemory().readData(0x50);
        int v2 = cpu.getMemory().readData(0x51);
        int v3 = cpu.getMemory().readData(0x52);

        boolean ok = (v1 == 0x11) && (v2 == 0x22) && (v3 == 0x33);
        check("TC02 Multiple Enqueues FIFO Order",
                ok,
                "order=[" + hex(v1) + ", " + hex(v2) + ", " + hex(v3) + "]",
                "order=[11, 22, 33]");
    }

    // -------------------------------------------------------
    // TC03: Empty condition (count = 0, isEmpty = true)
    // -------------------------------------------------------
    private static void testEmptyCondition() {
        Memory mem = new Memory();
        Queue queue = new Queue(mem);

        // Initial state after clear
        mem.writeData(Queue.HEAD_ADDR, 0);
        mem.writeData(Queue.TAIL_ADDR, 0);
        mem.writeData(Queue.COUNT_ADDR, 0);

        boolean emptyInitially = queue.isEmpty() && (queue.getCount() == 0) && !queue.isFull();

        // Enqueue an item
        mem.writeData(Queue.BUFFER_START, 0x99);
        mem.writeData(Queue.TAIL_ADDR, 1);
        mem.writeData(Queue.COUNT_ADDR, 1);
        boolean notEmptyNow = !queue.isEmpty();

        // Dequeue it
        mem.writeData(Queue.HEAD_ADDR, 1);
        mem.writeData(Queue.COUNT_ADDR, 0);
        boolean emptyAgain = queue.isEmpty() && (queue.getCount() == 0);

        boolean ok = emptyInitially && notEmptyNow && emptyAgain;
        check("TC03 Empty Condition Check",
                ok,
                "init=" + emptyInitially + ", mid=" + !notEmptyNow + ", final=" + emptyAgain,
                "init=true, mid=false, final=true");
    }

    // -------------------------------------------------------
    // TC04: Full condition (count = 8, isFull = true)
    // -------------------------------------------------------
    private static void testFullCondition() {
        Memory mem = new Memory();
        Queue queue = new Queue(mem);

        mem.writeData(Queue.HEAD_ADDR, 0);
        mem.writeData(Queue.TAIL_ADDR, 0);
        mem.writeData(Queue.COUNT_ADDR, 0);

        // Fill all 8 slots
        for (int i = 0; i < Queue.CAPACITY; i++) {
            mem.writeData(Queue.BUFFER_START + i, i + 1);
        }
        mem.writeData(Queue.TAIL_ADDR, 0); // wrapped around
        mem.writeData(Queue.COUNT_ADDR, Queue.CAPACITY);

        boolean full = queue.isFull() && !queue.isEmpty() && (queue.getCount() == Queue.CAPACITY);
        int[] contents = queue.getContentsInOrder();
        boolean contentsMatch = (contents.length == 8) && (contents[0] == 1) && (contents[7] == 8);

        check("TC04 Full Condition Check",
                full && contentsMatch,
                "isFull=" + queue.isFull() + ", count=" + queue.getCount(),
                "isFull=true, count=8");
    }

    // -------------------------------------------------------
    // TC05: Circular buffer wrap-around
    // -------------------------------------------------------
    private static void testCircularBufferWrapAround() {
        Memory mem = new Memory();
        Queue queue = new Queue(mem);

        // Put head at index 6, tail at index 6, count = 0
        mem.writeData(Queue.HEAD_ADDR, 6);
        mem.writeData(Queue.TAIL_ADDR, 6);
        mem.writeData(Queue.COUNT_ADDR, 0);

        // Enqueue 3 elements: slots 6, 7, and 0 (wrap around!)
        mem.writeData(Queue.BUFFER_START + 6, 0xA1);
        mem.writeData(Queue.BUFFER_START + 7, 0xB2);
        mem.writeData(Queue.BUFFER_START + 0, 0xC3); // wrapped slot 0

        mem.writeData(Queue.TAIL_ADDR, 1); // tail is now at 1
        mem.writeData(Queue.COUNT_ADDR, 3); // 3 items

        int[] contents = queue.getContentsInOrder();
        boolean orderOk = (contents.length == 3)
                && (contents[0] == 0xA1)
                && (contents[1] == 0xB2)
                && (contents[2] == 0xC3);

        check("TC05 Circular Buffer Wrap-Around",
                orderOk,
                "order=[" + hex(contents[0]) + ", " + hex(contents[1]) + ", " + hex(contents[2]) + "]",
                "order=[A1, B2, C3]");
    }

    // -------------------------------------------------------
    // TC06: Full Queue Demo Assembly Program Execution
    // -------------------------------------------------------
    private static void testQueueDemoProgramExecution() {
        CPU cpu = new CPU();
        Queue queue = new Queue(cpu.getMemory());

        int[] queueDemoProgram = {
                // Initialize queue metadata
                0x74, 0x00, 0xF5, 0x30, 0xF5, 0x31, 0xF5, 0x32,

                // Enqueue 0xAA
                0x74, 0xAA, 0xF5, 0x40,
                0x74, 0x01, 0xF5, 0x31, 0xF5, 0x32,

                // Enqueue 0xBB
                0x74, 0xBB, 0xF5, 0x41,
                0x74, 0x02, 0xF5, 0x31, 0xF5, 0x32,

                // Enqueue 0xCC
                0x74, 0xCC, 0xF5, 0x42,
                0x74, 0x03, 0xF5, 0x31, 0xF5, 0x32,

                // Dequeue #1 (expect 0xAA) -> save at 0x50
                0xE5, 0x40, 0xF5, 0x50,
                0x74, 0x01, 0xF5, 0x30,
                0x74, 0x02, 0xF5, 0x32,

                // Dequeue #2 (expect 0xBB) -> save at 0x51
                0xE5, 0x41, 0xF5, 0x51,
                0x74, 0x02, 0xF5, 0x30,
                0x74, 0x01, 0xF5, 0x32,

                // Enqueue 0xDD -> slot 3
                0x74, 0xDD, 0xF5, 0x43,
                0x74, 0x04, 0xF5, 0x31,
                0x74, 0x02, 0xF5, 0x32,

                // Dequeue #3 (expect 0xCC) -> save at 0x52
                0xE5, 0x42, 0xF5, 0x52,
                0x74, 0x03, 0xF5, 0x30,
                0x74, 0x01, 0xF5, 0x32,

                // Dequeue #4 (expect 0xDD) -> save at 0x53
                0xE5, 0x43, 0xF5, 0x53,
                0x74, 0x04, 0xF5, 0x30,
                0x74, 0x00, 0xF5, 0x32,

                0xFF // HALT
        };

        cpu.getMemory().loadProgram(queueDemoProgram);
        cpu.run();

        int d1 = cpu.getMemory().readData(0x50);
        int d2 = cpu.getMemory().readData(0x51);
        int d3 = cpu.getMemory().readData(0x52);
        int d4 = cpu.getMemory().readData(0x53);

        boolean fifoCorrect = (d1 == 0xAA) && (d2 == 0xBB) && (d3 == 0xCC) && (d4 == 0xDD);
        boolean finalStateEmpty = queue.isEmpty() && (queue.getCount() == 0);

        check("TC06 Assembly Program End-to-End Execution",
                fifoCorrect && finalStateEmpty,
                "[" + hex(d1) + ", " + hex(d2) + ", " + hex(d3) + ", " + hex(d4) + "], count=" + queue.getCount(),
                "[AA, BB, CC, DD], count=0");
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private static void check(String name,
                              boolean condition,
                              String actual,
                              String expected) {
        if (condition) {
            System.out.println(
                    "  PASS  " + name +
                    "  (actual=" + actual + ")"
            );
            passed++;
        } else {
            System.out.println(
                    "  FAIL  " + name +
                    "  expected=" + expected +
                    "  actual=" + actual
            );
            failed++;
        }
    }

    private static String hex(int value) {
        return String.format("%02X", value & 0xFF);
    }
}
