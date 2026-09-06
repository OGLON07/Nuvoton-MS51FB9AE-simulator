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