package Main.instruction;

import Main.CPU.Registers;
import Main.Memory.Memory;

public class InstructionExecutor {

    private final Registers registers;
    private final Memory memory;

    public InstructionExecutor(Registers registers, Memory memory) {
        this.registers = registers;
        this.memory = memory;
    }

    public void execute(Instruction instruction) {
        switch (instruction.getOpcode()) {

            case PUSH_A: {
                // 1. Pre-increment SP (0x07 -> 0x08)
                int newSP = (registers.getSP() + 1) & 0xFF;
                
                // 2. MUST save new SP back into registers
                registers.setSP(newSP);
                
                // 3. Write ACC value into RAM at location [newSP]
                memory.writeData(newSP, registers.getAccumulator());
                break;
            }

            case POP_A: {
                int currentSP = registers.getSP();
                
                // 1. Read byte at RAM[currentSP] into ACC
                registers.setAccumulator(memory.readData(currentSP));
                
                // 2. Post-decrement SP (0x08 -> 0x07) and save back
                registers.setSP((currentSP - 1) & 0xFF);
                break;
            }

            case MOV_A_IMM:
                registers.setAccumulator(instruction.getOperand() & 0xFF);
                break;

            case MOV_RN_IMM:
                registers.setR(instruction.getRegisterIndex(), instruction.getOperand() & 0xFF);
                break;

            case ADD_A_IMM:
                registers.setAccumulator((registers.getAccumulator() + instruction.getOperand()) & 0xFF);
                break;

            case SUBB_A_IMM:
                registers.setAccumulator((registers.getAccumulator() - instruction.getOperand()) & 0xFF);
                break;

            case MUL_AB: {
                int result = registers.getAccumulator() * registers.getB();
                registers.setAccumulator(result & 0xFF);        // Low byte
                registers.setB((result >> 8) & 0xFF);           // High byte
                break;
            }

            case ANL_A_IMM:
                registers.setAccumulator(registers.getAccumulator() & instruction.getOperand());
                break;

            case INC_A:
                registers.setAccumulator((registers.getAccumulator() + 1) & 0xFF);
                break;

            case DEC_A:
                registers.setAccumulator((registers.getAccumulator() - 1) & 0xFF);
                break;

            case SJMP:
                registers.setPC((registers.getPC() + instruction.getOperand()) & 0xFFFF);
                break;

            case MOV_A_ADDR:
                registers.setAccumulator(memory.readData(instruction.getOperand()));
                break;

            case MOV_ADDR_A:
                memory.writeData(instruction.getOperand(), registers.getAccumulator());
                break;

            case HALT:
                break;

            default:
                throw new UnsupportedOperationException("Unhandled opcode: " + instruction.getOpcode());
        }
    }
}