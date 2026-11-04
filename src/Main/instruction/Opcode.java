package Main.instruction;

public enum Opcode {

    MOV_A_IMM(0x74),
    MOV_A_ADDR(0xE5),
    MOV_ADDR_A(0xF5),
    MOV_RN_IMM(0x78),
    MOV_B_IMM(0x75),      // MOV B,#data  (8051 encoding: 75 F0 data)

    ADD_A_IMM(0x24),
    SUBB_A_IMM(0x94),

    INC_A(0x04),
    DEC_A(0x14),

    MUL_AB(0xA4),

    ANL_A_IMM(0x54),
    ORL_A_IMM(0x44),
    XRL_A_IMM(0x64),

    JZ(0x60),
    JNZ(0x70),
    JB(0x20),
    JNB(0x30),
    SETB(0xD2),
    CLR_BIT(0xC2),
    CPL_BIT(0xB2),

    SJMP(0x80),

    PUSH_A(0xC0),
    POP_A(0xD0),

    NOP(0x00),

    HALT(0xFF);

    private final int code;

    Opcode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}