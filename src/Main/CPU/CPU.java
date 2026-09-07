package Main.CPU;

import Main.Memory.Memory;
import Main.instruction.Instruction;
import Main.instruction.InstructionExecutor;
import Main.instruction.Opcode;

public class CPU {

    private final Registers registers;
    private final Memory memory;
    private final InstructionExecutor instructionExecutor;

    private int fetchedOpcode;
    private Instruction decodedInstruction;
    private boolean halted;

    // ---------- Constructor ----------

    public CPU() {
        registers = new Registers();
        memory = new Memory();
        instructionExecutor = new InstructionExecutor(registers);
        halted = false;
    }

    // ---------- Getters ----------

    public Registers getRegisters() {
        return registers;
    }

    public Memory getMemory() {
        return memory;
    }
    public int getFetchedOpcode() {
        return fetchedOpcode;
    }

    public Instruction getDecodedInstruction() {
        return decodedInstruction;
    }

    // ---------- FETCH ----------

    public int fetch() {

        int pc = registers.getPC();

        // Read instruction byte from program memory
        fetchedOpcode = memory.readProgram(pc);

        // Move PC to the next byte
        registers.setPC(pc + 1);

        System.out.println(
                "FETCH: PC=" + pc +
                " OPCODE=0x" +
                Integer.toHexString(fetchedOpcode).toUpperCase()
        );

        return fetchedOpcode;
    }

    // ---------- DECODE ----------

    public Instruction decode() {

        switch (fetchedOpcode) {

            // MOV A,#data
            case 0x74: {

                int operand =
                        memory.readProgram(registers.getPC());

                registers.setPC(registers.getPC() + 1);

                decodedInstruction =
                        new Instruction(
                                Opcode.MOV_A_IMM,
                                operand
                        );

                break;
            }

            // MOV Rn,#data
            case 0x78:
            case 0x79:
            case 0x7A:
            case 0x7B:
            case 0x7C:
            case 0x7D:
            case 0x7E:
            case 0x7F: {

                int registerIndex =
                        fetchedOpcode - 0x78;

                int operand =
                        memory.readProgram(registers.getPC());

                registers.setPC(registers.getPC() + 1);

                decodedInstruction =
                        new Instruction(
                                Opcode.MOV_RN_IMM,
                                registerIndex,
                                operand
                        );

                break;
            }

            // ADD A,#data
            case 0x24: {

                int operand =
                        memory.readProgram(registers.getPC());

                registers.setPC(registers.getPC() + 1);

                decodedInstruction =
                        new Instruction(
                                Opcode.ADD_A_IMM,
                                operand
                        );

                break;
            }

            // SUBB A,#data
            case 0x94: {

                int operand =
                        memory.readProgram(registers.getPC());

                registers.setPC(registers.getPC() + 1);

                decodedInstruction =
                        new Instruction(
                                Opcode.SUBB_A_IMM,
                                operand
                        );

                break;
            }

            // MUL AB
            case 0xA4:

                decodedInstruction =
                        new Instruction(
                                Opcode.MUL_AB,
                                0
                        );

                break;

            // ANL A,#data
            case 0x54: {

                int operand =
                        memory.readProgram(registers.getPC());

                registers.setPC(registers.getPC() + 1);

                decodedInstruction =
                        new Instruction(
                                Opcode.ANL_A_IMM,
                                operand
                        );

                break;
            }

            // INC A
            case 0x04:

                decodedInstruction =
                        new Instruction(
                                Opcode.INC_A,
                                0
                        );

                break;

            // DEC A
            case 0x14:

                decodedInstruction =
                        new Instruction(
                                Opcode.DEC_A,
                                0
                        );

                break;

            // SJMP rel
            case 0x80: {

                int relativeOffset =
                        memory.readProgram(registers.getPC());

                registers.setPC(registers.getPC() + 1);

                // Convert unsigned 8-bit offset
                // into signed range -128 to +127
                if (relativeOffset >= 128) {
                    relativeOffset -= 256;
                }

                decodedInstruction =
                        new Instruction(
                                Opcode.SJMP,
                                relativeOffset
                        );

                break;
            }

            // HALT
            // Simulator-defined termination instruction
            case 0xFF:

                decodedInstruction =
                        new Instruction(
                                Opcode.HALT,
                                0
                        );

                break;

            default:

                throw new UnsupportedOperationException(
                        "Unknown opcode: 0x"
                                + Integer.toHexString(fetchedOpcode)
                                .toUpperCase()
                );
        }

        System.out.println(
                "DECODE: " +
                decodedInstruction.getOpcode()
        );

        if (decodedInstruction.getRegisterIndex() != -1) {

            System.out.println(
                    "       Register = R" +
                    decodedInstruction.getRegisterIndex()
            );
        }

        System.out.println(
                "       Operand = " +
                decodedInstruction.getOperand()
        );

        return decodedInstruction;
    }

    // ---------- EXECUTE ----------

    public void execute(Instruction instruction) {

        if (instruction == null) {
            throw new IllegalArgumentException(
                    "Instruction cannot be null"
            );
        }

        instructionExecutor.execute(instruction);

        if (instruction.getOpcode() == Opcode.HALT) {
            halted = true;
        }
    }

    // ---------- STEP ----------

    public void step() {

        if (halted) {
            return;
        }

        fetch();

        Instruction instruction = decode();

        execute(instruction);
    }

    // ---------- RUN ----------

    public void run() {

        while (!halted) {
            step();
        }
    }

    // ---------- RESET ----------

    public void reset() {

        registers.reset();

        // Reset CPU runtime state,
        // but keep the loaded program in program memory.
        memory.clearDataMemory();

        fetchedOpcode = 0;
        decodedInstruction = null;
        halted = false;
    }

    // ---------- HALT STATUS ----------

    public boolean isHalted() {
        return halted;
    }
}
