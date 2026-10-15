import Main.CPU.CPU;
import Main.CPU.Registers;
import java.io.*;

/**
 * Direct CPU test of the PSW flags (CY/AC/OV), MOV B,#data and the registers.
 * No assert statements: every check prints PASS/FAIL and the exit code reflects the result.
 */
public class CpuFlagsTest {
    static int pass = 0, fail = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.err.println("  PASS  " + name); }
        else    { fail++; System.err.println("  FAIL  " + name + "  -> " + detail); }
    }

    /** Runs the program to HALT on a fresh CPU and returns its registers. */
    static CPU run(int... prog) {
        CPU cpu = new CPU();
        cpu.getMemory().loadProgram(prog);
        cpu.run();
        return cpu;
    }

    static String state(Registers r) {
        return String.format("A=%02X B=%02X PSW=%02X CY=%d AC=%d OV=%d", r.getAccumulator(), r.getB(), r.getPSW(),
                r.isCarry() ? 1 : 0, r.isAuxiliaryCarry() ? 1 : 0, r.isOverflow() ? 1 : 0);
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));   // silence FETCH/DECODE trace
        Registers r;

        r = run(0x74, 0xFF, 0x24, 0x01, 0xFF).getRegisters();              // 0xFF + 0x01
        check("ADD 0xFF+0x01 -> A=00, CY=1, AC=1, OV=0, PSW=C0",
              r.getAccumulator() == 0 && r.isCarry() && r.isAuxiliaryCarry() && !r.isOverflow() && r.getPSW() == 0xC0, state(r));

        r = run(0x74, 0x7F, 0x24, 0x01, 0xFF).getRegisters();              // 127 + 1 = signed overflow
        check("ADD 0x7F+0x01 -> A=80, CY=0, AC=1, OV=1 (signed overflow)",
              r.getAccumulator() == 0x80 && !r.isCarry() && r.isAuxiliaryCarry() && r.isOverflow(), state(r));

        r = run(0x74, 0x50, 0x24, 0x50, 0xFF).getRegisters();              // 0x50 + 0x50
        check("ADD 0x50+0x50 -> A=A0, CY=0, AC=0, OV=1, PSW=04",
              r.getAccumulator() == 0xA0 && !r.isCarry() && !r.isAuxiliaryCarry() && r.isOverflow() && r.getPSW() == 0x04, state(r));

        r = run(0x74, 0xFF, 0x24, 0x01, 0x74, 0x10, 0x24, 0x20, 0xFF).getRegisters();   // set flags, then a clean ADD
        check("a later ADD without carry clears CY/AC/OV again (PSW=00)", r.getPSW() == 0 && r.getAccumulator() == 0x30, state(r));

        r = run(0x74, 0x05, 0x94, 0x0A, 0xFF).getRegisters();              // 5 - 10
        check("SUBB 0x05-0x0A -> A=FB, CY=1 (borrow)", r.getAccumulator() == 0xFB && r.isCarry(), state(r));

        r = run(0x74, 0x0A, 0x94, 0x05, 0xFF).getRegisters();              // 10 - 5
        check("SUBB 0x0A-0x05 -> A=05, CY=0", r.getAccumulator() == 0x05 && !r.isCarry() && r.getPSW() == 0, state(r));

        r = run(0x74, 0x10, 0x75, 0xF0, 0x10, 0xA4, 0xFF).getRegisters(); // 0x10 * 0x10 = 0x100
        check("MOV B,#10 ; MUL AB (0x10*0x10) -> A=00, B=01, OV=1, CY=0",
              r.getAccumulator() == 0 && r.getB() == 1 && r.isOverflow() && !r.isCarry(), state(r));

        r = run(0x74, 0x03, 0x75, 0xF0, 0x04, 0xA4, 0xFF).getRegisters(); // 3 * 4 = 12
        check("MUL AB (3*4) -> A=0C, B=00, OV=0", r.getAccumulator() == 0x0C && r.getB() == 0 && !r.isOverflow(), state(r));

        r = run(0x75, 0xF0, 0x33, 0xFF).getRegisters();
        check("MOV B,#0x33 loads B", r.getB() == 0x33, state(r));

        boolean threw = false;
        try { run(0x75, 0x30, 0x01, 0xFF); } catch (UnsupportedOperationException e) { threw = true; }
        check("MOV direct,#data other than B is rejected (not silently wrong)", threw, "no exception");

        r = run(0x74, 0xFF, 0x24, 0x01, 0x04, 0x14, 0x54, 0x0F, 0xFF).getRegisters();   // INC / DEC / ANL after CY=1
        check("INC / DEC / ANL do not touch the flags (CY/AC stay set)", r.getPSW() == 0xC0, state(r));

        r = run(0x78, 0x01, 0x79, 0x02, 0x7A, 0x03, 0x7B, 0x04, 0x7C, 0x05, 0x7D, 0x06, 0x7E, 0x07, 0x7F, 0x08, 0xFF).getRegisters();
        boolean all = true;
        for (int i = 0; i < 8; i++) all &= r.getR(i) == i + 1;
        check("MOV R0..R7,#imm writes each register independently", all, "R-values wrong");

        CPU cpu = run(0x74, 0xFF, 0x24, 0x01, 0x75, 0xF0, 0x09, 0x78, 0x07, 0xFF);
        cpu.reset();
        r = cpu.getRegisters();
        check("RESET clears A, B, R0, PSW and flags", r.getAccumulator() == 0 && r.getB() == 0 && r.getR(0) == 0 && r.getPSW() == 0, state(r));

        System.err.println("  CpuFlagsTest: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
