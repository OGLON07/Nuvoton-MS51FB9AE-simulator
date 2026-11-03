package Main.instruction;

/** Editable assembly source used by the UI demo buttons. */
public final class DemoPrograms {
    private DemoPrograms() { }

    public static String example() {
        return "; General CPU instruction example\n" +
               "MOV A,#0x0F\nMOV R0,#0x01\nMOV R1,#0x02\nMOV R2,#0x03\nMOV R3,#0x05\n" +
               "MOV R4,#0x08\nMOV R5,#0x0D\nMOV R6,#0x15\nMOV R7,#0x22\n" +
               "ADD A,#0x01\nADD A,#0xF0\nMOV A,#0x50\nADD A,#0x50\n" +
               "SUBB A,#0xB0\nMOV A,#0x20\nMOV B,#0x10\nMUL AB\n" +
               "MOV A,#0x3C\nANL A,#0x0F\nINC A\nDEC A\nMOV 0x30,A\n" +
               "MOV A,#0x00\nMOV A,0x30\nNOP\nSJMP DONE\nMOV A,#0xFF\nDONE: HALT\n";
    }

    public static String stack() {
        return "; Stack demonstration\nMOV A,#0x42\nPUSH A\nMOV A,#0xFF\nPUSH A\n" +
               "ADD A,#0x01\nMOV R0,#0x09\nPOP A\nPOP A\nHALT\n";
    }

    public static String queue() {
        return "; FIFO queue metadata and three sample values\nMOV A,#0\nMOV 0x30,A\nMOV 0x31,A\nMOV 0x32,A\n" +
               "MOV A,#0xAA\nMOV 0x40,A\nMOV A,#1\nMOV 0x31,A\nMOV 0x32,A\n" +
               "MOV A,#0xBB\nMOV 0x41,A\nMOV A,#2\nMOV 0x31,A\nMOV 0x32,A\n" +
               "MOV A,#0xCC\nMOV 0x42,A\nMOV A,#3\nMOV 0x31,A\nMOV 0x32,A\n" +
               "MOV R0,#3\nMOV R1,#0x40\nHALT\n";
    }

    /** Hamming (7,4), even parity. Encodes nibble 0xB, then checks the codeword syndrome. */
    public static String hamming() {
        return "; HAMMING (7,4) DEMO - EVEN PARITY\n" +
               "; Input nibble is stored in RAM[20h]. Codeword is built in RAM[24h].\n" +
               "; Bit positions: 1=P1, 2=P2, 3=D1, 4=P4, 5=D2, 6=D3, 7=D4.\n" +
               "MOV A,#0x0B\nMOV 0x20,A\n" +
               "; Calculate P1 = D1 XOR D2 XOR D4 (input bits 0,1,3)\n" +
               "MOV A,#0\nMOV 0x21,A\nJNB 0x00,P1B\nMOV A,0x21\nXRL A,#1\nMOV 0x21,A\nP1B: JNB 0x01,P1C\nMOV A,0x21\nXRL A,#1\nMOV 0x21,A\nP1C: JNB 0x03,P1D\nMOV A,0x21\nXRL A,#1\nMOV 0x21,A\nP1D: NOP\n" +
               "; Calculate P2 = D1 XOR D3 XOR D4 (input bits 0,2,3)\n" +
               "MOV A,#0\nMOV 0x22,A\nJNB 0x00,P2B\nMOV A,0x22\nXRL A,#1\nMOV 0x22,A\nP2B: JNB 0x02,P2C\nMOV A,0x22\nXRL A,#1\nMOV 0x22,A\nP2C: JNB 0x03,P2D\nMOV A,0x22\nXRL A,#1\nMOV 0x22,A\nP2D: NOP\n" +
               "; Calculate P4 = D2 XOR D3 XOR D4 (input bits 1,2,3)\n" +
               "MOV A,#0\nMOV 0x23,A\nJNB 0x01,P4B\nMOV A,0x23\nXRL A,#1\nMOV 0x23,A\nP4B: JNB 0x02,P4C\nMOV A,0x23\nXRL A,#1\nMOV 0x23,A\nP4C: JNB 0x03,P4D\nMOV A,0x23\nXRL A,#1\nMOV 0x23,A\nP4D: NOP\n" +
               "; Assemble codeword bits in RAM[24h]: P1,P2,D1,P4,D2,D3,D4\n" +
               "MOV A,#0\nMOV 0x24,A\nJNB 0x08,OUT2\nSETB 0x20\nOUT2: JNB 0x10,OUT3\nSETB 0x21\nOUT3: JNB 0x00,OUT4\nSETB 0x22\nOUT4: JNB 0x18,OUT5\nSETB 0x23\nOUT5: JNB 0x01,OUT6\nSETB 0x24\nOUT6: JNB 0x02,OUT7\nSETB 0x25\nOUT7: JNB 0x03,ENCODED\nSETB 0x26\nENCODED: NOP\n" +
               "; Syndrome checks: S1 checks positions 1,3,5,7; S2 checks 2,3,6,7; S4 checks 4,5,6,7.\n" +
               "MOV A,#0\nMOV 0x28,A\nJNB 0x20,S1B\nMOV A,0x28\nXRL A,#1\nMOV 0x28,A\nS1B: JNB 0x22,S1C\nMOV A,0x28\nXRL A,#1\nMOV 0x28,A\nS1C: JNB 0x24,S1D\nMOV A,0x28\nXRL A,#1\nMOV 0x28,A\nS1D: JNB 0x26,S1E\nMOV A,0x28\nXRL A,#1\nMOV 0x28,A\nS1E: NOP\n" +
               "MOV A,#0\nMOV 0x29,A\nJNB 0x21,S2B\nMOV A,0x29\nXRL A,#1\nMOV 0x29,A\nS2B: JNB 0x22,S2C\nMOV A,0x29\nXRL A,#1\nMOV 0x29,A\nS2C: JNB 0x25,S2D\nMOV A,0x29\nXRL A,#1\nMOV 0x29,A\nS2D: JNB 0x26,S2E\nMOV A,0x29\nXRL A,#1\nMOV 0x29,A\nS2E: NOP\n" +
               "MOV A,#0\nMOV 0x2A,A\nJNB 0x23,S4B\nMOV A,0x2A\nXRL A,#1\nMOV 0x2A,A\nS4B: JNB 0x24,S4C\nMOV A,0x2A\nXRL A,#1\nMOV 0x2A,A\nS4C: JNB 0x25,S4D\nMOV A,0x2A\nXRL A,#1\nMOV 0x2A,A\nS4D: JNB 0x26,S4E\nMOV A,0x2A\nXRL A,#1\nMOV 0x2A,A\nS4E: NOP\n" +
               "; Combine syndrome: bit0=S1, bit1=S2, bit2=S4. Zero means no error detected.\n" +
               "MOV A,#0\nMOV 0x2B,A\nMOV A,0x28\nJZ SY2\nMOV A,0x2B\nORL A,#1\nMOV 0x2B,A\nSY2: MOV A,0x29\nJZ SY4\nMOV A,0x2B\nORL A,#2\nMOV 0x2B,A\nSY4: MOV A,0x2A\nJZ FINISH\nMOV A,0x2B\nORL A,#4\nMOV 0x2B,A\n" +
               "; Correct a single-bit error when syndrome is 1..7. Syndrome 0 skips correction.\n" +
               "FINISH: MOV A,0x2B\nJZ DONE\nXRL A,#1\nJZ FIX1\nMOV A,0x2B\nXRL A,#2\nJZ FIX2\n" +
               "MOV A,0x2B\nXRL A,#3\nJZ FIX3\nMOV A,0x2B\nXRL A,#4\nJZ FIX4\n" +
               "MOV A,0x2B\nXRL A,#5\nJZ FIX5\nMOV A,0x2B\nXRL A,#6\nJZ FIX6\n" +
               "CPL 0x26\nSJMP CORRECTED\nFIX1: CPL 0x20\nSJMP CORRECTED\nFIX2: CPL 0x21\nSJMP CORRECTED\n" +
               "FIX3: CPL 0x22\nSJMP CORRECTED\nFIX4: CPL 0x23\nSJMP CORRECTED\n" +
               "FIX5: CPL 0x24\nSJMP CORRECTED\nFIX6: CPL 0x25\nSJMP CORRECTED\n" +
               "CORRECTED: MOV A,#1\nMOV 0x2C,A\nDONE: HALT\n";
    }
}
