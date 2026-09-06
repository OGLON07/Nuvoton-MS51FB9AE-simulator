package Main.instruction;

public class Instruction {

    private final Opcode opcode;
    private final int operand;
    private final int registerIndex;

    // Constructor for instructions without a register operand
    public Instruction(Opcode opcode, int operand) {
        this.opcode = opcode;
        this.operand = operand;
        this.registerIndex = -1;
    }

    // Constructor for instructions such as MOV Rn,#data
    public Instruction(Opcode opcode, int registerIndex, int operand) {
        this.opcode = opcode;
        this.registerIndex = registerIndex;
        this.operand = operand;
    }

    public Opcode getOpcode() {
        return opcode;
    }

    public int getOperand() {
        return operand;
    }

    public int getRegisterIndex() {
        return registerIndex;
    }
}