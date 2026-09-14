import Main.CPU.CPU;
import Main.Memory.Memory;
import Main.CPU.Registers;

/**
 * Week-03 Data Memory Tests
 *
 * Tests the MOV A,addr and MOV addr,A instructions
 * along with RAM boundary cases and reset behaviour.
 *
 * No JUnit dependency — uses plain assertions + console output.
 */
public class DataMemoryTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        System.out.println(
                "========================================\n" +
                " Week-03 Data Memory Test Suite\n" +
                "========================================"
        );

        testWriteAndReadBack();
        testBoundaryAddress0x00();
        testBoundaryAddress0xFF();
        testOutOfRangeReadThrows();
        testOutOfRangeWriteThrows();
        testResetClearsRAM();

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
    // TC01: Write A=0x42 to RAM[0x30], read it back into A
    // Program: MOV A,#42  ->  MOV 30h,A  ->  MOV A,#00  ->  MOV A,30h  ->  HALT
    // -------------------------------------------------------
    private static void testWriteAndReadBack() {

        CPU cpu = new CPU();

        cpu.getMemory().loadProgram(new int[]{
                0x74, 0x42,       // MOV A, #42h
                0xF5, 0x30,       // MOV 30h, A    (write A into RAM[0x30])
                0x74, 0x00,       // MOV A, #00h   (clear A to prove read works)
                0xE5, 0x30,       // MOV A, 30h    (read RAM[0x30] into A)
                0xFF              // HALT
        });

        cpu.run();

        int actual = cpu.getRegisters().getAccumulator();
        check("TC01 Write & Read Back",
                actual == 0x42,
                "A=0x" + hex(actual),
                "A=0x42");
    }

    // -------------------------------------------------------
    // TC02: Write to boundary address RAM[0x00]
    // -------------------------------------------------------
    private static void testBoundaryAddress0x00() {

        CPU cpu = new CPU();

        cpu.getMemory().loadProgram(new int[]{
                0x74, 0xAB,       // MOV A, #ABh
                0xF5, 0x00,       // MOV 00h, A    (write to address 0)
                0x74, 0x00,       // MOV A, #00h
                0xE5, 0x00,       // MOV A, 00h    (read from address 0)
                0xFF              // HALT
        });

        cpu.run();

        int actual = cpu.getRegisters().getAccumulator();
        check("TC02 Boundary addr 0x00",
                actual == 0xAB,
                "A=0x" + hex(actual),
                "A=0xAB");
    }

    // -------------------------------------------------------
    // TC03: Write to boundary address RAM[0xFF]
    // -------------------------------------------------------
    private static void testBoundaryAddress0xFF() {

        CPU cpu = new CPU();

        cpu.getMemory().loadProgram(new int[]{
                0x74, 0xCD,       // MOV A, #CDh
                0xF5, 0xFF,       // MOV FFh, A    (write to address 255)
                0x74, 0x00,       // MOV A, #00h
                0xE5, 0xFF,       // MOV A, FFh    (read from address 255)
                0xFF              // HALT
        });

        cpu.run();

        int actual = cpu.getRegisters().getAccumulator();
        check("TC03 Boundary addr 0xFF",
                actual == 0xCD,
                "A=0x" + hex(actual),
                "A=0xCD");
    }

    // -------------------------------------------------------
    // TC04: Read from out-of-range address (256)
    // -------------------------------------------------------
    private static void testOutOfRangeReadThrows() {

        Memory mem = new Memory();
        boolean threw = false;

        try {
            mem.readData(256);
        } catch (IllegalArgumentException e) {
            threw = true;
        }

        check("TC04 Out-of-range read (256)",
                threw,
                threw ? "IllegalArgumentException" : "No exception",
                "IllegalArgumentException");
    }

    // -------------------------------------------------------
    // TC05: Write to out-of-range address (-1)
    // -------------------------------------------------------
    private static void testOutOfRangeWriteThrows() {

        Memory mem = new Memory();
        boolean threw = false;

        try {
            mem.writeData(-1, 0x55);
        } catch (IllegalArgumentException e) {
            threw = true;
        }

        check("TC05 Out-of-range write (-1)",
                threw,
                threw ? "IllegalArgumentException" : "No exception",
                "IllegalArgumentException");
    }

    // -------------------------------------------------------
    // TC06: CPU reset() clears RAM
    // -------------------------------------------------------
    private static void testResetClearsRAM() {

        CPU cpu = new CPU();

        // Write 0x99 to RAM[0x50]
        cpu.getMemory().loadProgram(new int[]{
                0x74, 0x99,       // MOV A, #99h
                0xF5, 0x50,       // MOV 50h, A
                0xFF              // HALT
        });

        cpu.run();

        // Reset the CPU (should clear RAM but keep program)
        cpu.reset();

        // Read back — should be 0 after reset
        int actual = cpu.getMemory().readData(0x50);
        check("TC06 RESET clears RAM",
                actual == 0x00,
                "RAM[0x50]=0x" + hex(actual),
                "RAM[0x50]=0x00");
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
