package Main.instruction;

public class InstructionSet {

    // MOV A,#data
    public static Instruction movAImmediate(int value) {

        checkByte(value);

        return new Instruction(
                Opcode.MOV_A_IMM,
                value
        );
    }

    // MOV A,addr — load accumulator from RAM
    public static Instruction movADirect(int address) {

        checkByte(address);

        return new Instruction(
                Opcode.MOV_A_ADDR,
                address
        );
    }

    // MOV addr,A — store accumulator into RAM
    public static Instruction movDirectA(int address) {

        checkByte(address);

        return new Instruction(
                Opcode.MOV_ADDR_A,
                address
        );
    }

    // MOV Rn,#data
    public static Instruction movRImmediate(int registerIndex, int value) {

        checkRegister(registerIndex);
        checkByte(value);

        return new Instruction(
                Opcode.MOV_RN_IMM,
                registerIndex,
                value
        );
    }

    // MOV B,#data
    public static Instruction movBImmediate(int value) {

        checkByte(value);

        return new Instruction(
                Opcode.MOV_B_IMM,
                value
        );
    }

    // ADD A,#data
    public static Instruction addAImmediate(int value) {

        checkByte(value);

        return new Instruction(
                Opcode.ADD_A_IMM,
                value
        );
    }

    // SUBB A,#data
    public static Instruction subbAImmediate(int value) {

        checkByte(value);

        return new Instruction(
                Opcode.SUBB_A_IMM,
                value
        );
    }

    // INC A
    public static Instruction incA() {

        return new Instruction(
                Opcode.INC_A,
                0
        );
    }

    // DEC A
    public static Instruction decA() {

        return new Instruction(
                Opcode.DEC_A,
                0
        );
    }

    // MUL AB
    public static Instruction mulAB() {

        return new Instruction(
                Opcode.MUL_AB,
                0
        );
    }

    // ANL A,#data
    public static Instruction anlAImmediate(int value) {

        checkByte(value);

        return new Instruction(
                Opcode.ANL_A_IMM,
                value
        );
    }

    // ORL A,#data
    public static Instruction orlAImmediate(int value) {
        checkByte(value);
        return new Instruction(Opcode.ORL_A_IMM, value);
    }

    // XRL A,#data (bitwise XOR; used by Hamming parity calculations)
    public static Instruction xrlAImmediate(int value) {
        checkByte(value);
        return new Instruction(Opcode.XRL_A_IMM, value);
    }

    // Conditional jumps use signed 8-bit relative offsets.
    public static Instruction jz(int offset) {
        checkRelative(offset);
        return new Instruction(Opcode.JZ, offset);
    }

    public static Instruction jnz(int offset) {
        checkRelative(offset);
        return new Instruction(Opcode.JNZ, offset);
    }

    // Bit-addressable internal RAM operations (bit address 00h-7Fh).
    public static Instruction jb(int bitAddress, int offset) {
        checkBitAddress(bitAddress); checkRelative(offset);
        return new Instruction(Opcode.JB, bitAddress, offset);
    }

    public static Instruction jnb(int bitAddress, int offset) {
        checkBitAddress(bitAddress); checkRelative(offset);
        return new Instruction(Opcode.JNB, bitAddress, offset);
    }

    public static Instruction setb(int bitAddress) {
        checkBitAddress(bitAddress);
        return new Instruction(Opcode.SETB, bitAddress);
    }

    public static Instruction clrBit(int bitAddress) {
        checkBitAddress(bitAddress);
        return new Instruction(Opcode.CLR_BIT, bitAddress);
    }

    public static Instruction cplBit(int bitAddress) {
        checkBitAddress(bitAddress);
        return new Instruction(Opcode.CPL_BIT, bitAddress);
    }

    private static void checkRelative(int offset) {
        if (offset < -128 || offset > 127) throw new IllegalArgumentException("Relative offset must be -128..127");
    }

    private static void checkBitAddress(int address) {
        if (address < 0 || address > 0x7F) throw new IllegalArgumentException("Bit address must be 0x00..0x7F");
    }

    // SJMP rel
    public static Instruction sjmp(int offset) {

        if (offset < -128 || offset > 127) {
            throw new IllegalArgumentException(
                    "SJMP offset must be between -128 and 127"
            );
        }

        return new Instruction(
                Opcode.SJMP,
                offset
        );
    }

    // HALT
    public static Instruction halt() {

        return new Instruction(
                Opcode.HALT,
                0
        );
    }

    // ---------- Validation helpers ----------

    private static void checkByte(int value) {

        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(
                    "Value must be between 0 and 255"
            );
        }
    }

    private static void checkRegister(int registerIndex) {

        if (registerIndex < 0 || registerIndex > 7) {
            throw new IllegalArgumentException(
                    "Register must be between R0 and R7"
            );
        }
    }
}