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

            case MOV_B_IMM:
                registers.setB(instruction.getOperand() & 0xFF);
                break;

            case ADD_A_IMM: {
                int a   = registers.getAccumulator();
                int imm = instruction.getOperand() & 0xFF;
                int sum = a + imm;
                boolean carry     = sum > 0xFF;                          // carry out of bit 7
                boolean auxCarry  = ((a & 0x0F) + (imm & 0x0F)) > 0x0F;  // carry out of bit 3
                boolean carryInto7 = ((a & 0x7F) + (imm & 0x7F)) > 0x7F; // carry out of bit 6
                registers.setAccumulator(sum & 0xFF);
                registers.setCarry(carry);
                registers.setAuxiliaryCarry(auxCarry);
                registers.setOverflow(carry != carryInto7);              // signed overflow
                break;
            }

            case SUBB_A_IMM: {
                int a   = registers.getAccumulator();
                int imm = instruction.getOperand() & 0xFF;
                int carryIn = registers.isCarry() ? 1 : 0;
                int subtrahend = imm + carryIn;
                boolean borrow      = a < subtrahend;
                boolean auxBorrow   = (a & 0x0F) < ((imm & 0x0F) + carryIn);
                boolean borrowFrom6 = (a & 0x7F) < (subtrahend & 0x7F);
                registers.setAccumulator((a - subtrahend) & 0xFF);
                registers.setCarry(borrow);
                registers.setAuxiliaryCarry(auxBorrow);
                registers.setOverflow(borrow != borrowFrom6);
                break;
            }

            case MUL_AB: {
                int result = registers.getAccumulator() * registers.getB();
                registers.setAccumulator(result & 0xFF);        // Low byte
                registers.setB((result >> 8) & 0xFF);           // High byte
                registers.setCarry(false);                      // MUL always clears CY
                registers.setOverflow(result > 0xFF);           // OV = result does not fit in A
                break;
            }

            case ANL_A_IMM:
                registers.setAccumulator(registers.getAccumulator() & instruction.getOperand());
                break;
            case ORL_A_IMM:
                registers.setAccumulator(registers.getAccumulator() | (instruction.getOperand() & 0xFF));
                break;

            case XRL_A_IMM:
                registers.setAccumulator(registers.getAccumulator() ^ (instruction.getOperand() & 0xFF));
                break;

            case JZ:
                if (registers.getAccumulator() == 0) {
                    registers.setPC((registers.getPC() + instruction.getOperand()) & 0xFFFF);
                }
                break;

            case JNZ:
                if (registers.getAccumulator() != 0) {
                    registers.setPC((registers.getPC() + instruction.getOperand()) & 0xFFFF);
                }
                break;

            case JB:
                if (readBit(instruction.getRegisterIndex())) {
                    registers.setPC((registers.getPC() + instruction.getOperand()) & 0xFFFF);
                }
                break;

            case JNB:
                if (!readBit(instruction.getRegisterIndex())) {
                    registers.setPC((registers.getPC() + instruction.getOperand()) & 0xFFFF);
                }
                break;

            case SETB:
                writeBit(instruction.getOperand(), true);
                break;

            case CLR_BIT:
                writeBit(instruction.getOperand(), false);
                break;

            case CPL_BIT:
                writeBit(instruction.getOperand(), !readBit(instruction.getOperand()));
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

            case NOP:
            case HALT:
                break;

            default:
                throw new UnsupportedOperationException("Unhandled opcode: " + instruction.getOpcode());
        }
    }

    /** 8051-style bit-addressable internal RAM: bit addresses 00h-7Fh map to RAM 20h-2Fh. */
    private boolean readBit(int bitAddress) {
        if (bitAddress < 0 || bitAddress > 0x7F) {
            throw new IllegalArgumentException("Only bit-addressable RAM bits 0x00-0x7F are supported");
        }
        int byteAddress = 0x20 + (bitAddress / 8);
        int mask = 1 << (bitAddress % 8);
        return (memory.readData(byteAddress) & mask) != 0;
    }

    private void writeBit(int bitAddress, boolean value) {
        if (bitAddress < 0 || bitAddress > 0x7F) {
            throw new IllegalArgumentException("Only bit-addressable RAM bits 0x00-0x7F are supported");
        }
        int byteAddress = 0x20 + (bitAddress / 8);
        int mask = 1 << (bitAddress % 8);
        int current = memory.readData(byteAddress);
        memory.writeData(byteAddress, value ? (current | mask) : (current & ~mask));
    }

}