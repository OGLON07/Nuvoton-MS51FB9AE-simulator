package Main.instruction;

public enum Opcode {

    MOV_A_IMM(0x74),
    MOV_RN_IMM(0x78),

    ADD_A_IMM(0x24),
    SUBB_A_IMM(0x94),

    INC_A(0x04),
    DEC_A(0x14),

    MUL_AB(0xA4),

    ANL_A_IMM(0x54),

    SJMP(0x80),

    HALT(0xFF);

    private final int code;

    Opcode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}