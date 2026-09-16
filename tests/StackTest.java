import Main.CPU.CPU;

public class StackTest {

    public static void main(String[] args) {
        System.out.println("--- Running Stack Unit Tests ---");

        testSinglePushPop();
        testMultiplePushPopLIFO();
        testSPOverflow();

        System.out.println("ALL STACK TESTS PASSED SUCCESSFULLY!");
    }

    public static void testSinglePushPop() {
        CPU cpu = new CPU();

        // Load Program Memory with: PUSH_A (0xC0), POP_A (0xD0), HALT (0xFF)
        cpu.getMemory().writeProgram(0, 0xC0);
        cpu.getMemory().writeProgram(1, 0xD0);
        cpu.getMemory().writeProgram(2, 0xFF);

        // Pre-set Accumulator and Stack Pointer
        cpu.getRegisters().setAccumulator(0x42);
        
        // Execute PUSH_A
        cpu.step();
        assert cpu.getRegisters().getSP() == 0x08 : "Error: SP should be 0x08 after PUSH";
        assert cpu.getMemory().readData(0x08) == 0x42 : "Error: Memory at SP 0x08 should hold 0x42";

        // Corrupt Accumulator to verify POP restores it
        cpu.getRegisters().setAccumulator(0x00);

        // Execute POP_A
        cpu.step();
        assert cpu.getRegisters().getSP() == 0x07 : "Error: SP should be 0x07 after POP";
        assert cpu.getRegisters().getAccumulator() == 0x42 : "Error: ACC should be restored to 0x42";

        System.out.println("[PASS] testSinglePushPop");
    }

    public static void testMultiplePushPopLIFO() {
        CPU cpu = new CPU();

        // Load Program: PUSH_A, INC_A, PUSH_A, POP_A, POP_A, HALT
        int[] program = {0xC0, 0x04, 0xC0, 0xD0, 0xD0, 0xFF};
        for (int i = 0; i < program.length; i++) {
            cpu.getMemory().writeProgram(i, program[i]);
        }

        cpu.getRegisters().setAccumulator(0x10);

        // PUSH 0x10 (SP becomes 0x08)
        cpu.step(); 
        // INC A (ACC becomes 0x11)
        cpu.step(); 
        // PUSH 0x11 (SP becomes 0x09)
        cpu.step(); 

        assert cpu.getRegisters().getSP() == 0x09 : "Error: SP should be 0x09 after two PUSHes";

        // POP (Should retrieve 0x11 first, SP becomes 0x08)
        cpu.step(); 
        assert cpu.getRegisters().getAccumulator() == 0x11 : "Error: First POP should yield 0x11";

        // POP (Should retrieve 0x10 next, SP becomes 0x07)
        cpu.step(); 
        assert cpu.getRegisters().getAccumulator() == 0x10 : "Error: Second POP should yield 0x10";
        assert cpu.getRegisters().getSP() == 0x07 : "Error: SP should return to 0x07";

        System.out.println("[PASS] testMultiplePushPopLIFO");
    }

    public static void testSPOverflow() {
    CPU cpu = new CPU();

    // Set SP near the top of 256-byte RAM
    cpu.getRegisters().setSP(0xFF);
    
    // Program: PUSH_A (0xC0)
    cpu.getMemory().writeProgram(0, 0xC0);
    cpu.getRegisters().setAccumulator(0xAA);

    // Execute PUSH_A at boundary
    cpu.step();

    // Verify 8-bit wrap-around from 0xFF -> 0x00
    assert cpu.getRegisters().getSP() == 0x00 : "Error: SP should wrap around to 0x00 on overflow";
    assert cpu.getMemory().readData(0x00) == 0xAA : "Error: Data should be written to RAM[0x00]";

    System.out.println("[PASS] testSPOverflow");
}
}