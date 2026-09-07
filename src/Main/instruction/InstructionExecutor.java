package Main.instruction;

import Main.CPU.Registers;

public class InstructionExecutor {

    private final Registers registers;

    // ---------- Constructor ----------

    public InstructionExecutor(Registers registers) {

        if (registers == null) {
            throw new IllegalArgumentException(
                    "Registers cannot be null"
            );
        }

        this.registers = registers;
    }

    // ---------- EXECUTE ----------

    public void execute(Instruction instruction) {

        if (instruction == null) {
            throw new IllegalArgumentException(
                    "Instruction cannot be null"
            );
        }

        switch (instruction.getOpcode()) {

            // -------------------------
            // DATA TRANSFER
            // -------------------------

            // MOV A,#data
            case MOV_A_IMM:

                registers.setAccumulator(
                        instruction.getOperand()
                );

                break;

            // MOV Rn,#data
            case MOV_RN_IMM:

                registers.setR(
                        instruction.getRegisterIndex(),
                        instruction.getOperand()
                );

                break;


            // -------------------------
            // ARITHMETIC
            // -------------------------

            // ADD A,#data
            case ADD_A_IMM: {

                int a =
                        registers.getAccumulator();

                int operand =
                        instruction.getOperand();

                int result =
                        a + operand;

                int result8 =
                        result & 0xFF;

                // Store 8-bit result
                registers.setAccumulator(result8);

                // Carry
                registers.setCarry(
                        result > 0xFF
                );

                // Auxiliary Carry
                registers.setAuxiliaryCarry(
                        ((a & 0x0F) +
                         (operand & 0x0F)) > 0x0F
                );

                // Signed overflow
                boolean overflow =
                        ((~(a ^ operand))
                        & (a ^ result8)
                        & 0x80) != 0;

                registers.setOverflow(overflow);

                break;
            }


            // SUBB A,#data
            case SUBB_A_IMM: {

                int a =
                        registers.getAccumulator();

                int operand =
                        instruction.getOperand();

                // SUBB includes the current carry
                int carryIn =
                        registers.isCarry() ? 1 : 0;

                int result =
                        a - operand - carryIn;

                int result8 =
                        result & 0xFF;

                // Store result
                registers.setAccumulator(result8);

                // Borrow sets Carry
                registers.setCarry(
                        result < 0
                );

                // Auxiliary borrow
                registers.setAuxiliaryCarry(
                        ((a & 0x0F)
                        - (operand & 0x0F)
                        - carryIn) < 0
                );

                // Signed overflow
                boolean overflow =
                        ((a ^ operand)
                        & (a ^ result8)
                        & 0x80) != 0;

                registers.setOverflow(overflow);

                break;
            }


            // MUL AB
            case MUL_AB: {

                int a =
                        registers.getAccumulator();

                int b =
                        registers.getB();

                int result =
                        a * b;

                // Lower 8 bits -> A
                registers.setAccumulator(
                        result & 0xFF
                );

                // Upper 8 bits -> B
                registers.setB(
                        (result >> 8) & 0xFF
                );

                // MUL clears Carry
                registers.setCarry(false);

                // Overflow if result needs more than 8 bits
                registers.setOverflow(
                        result > 0xFF
                );

                break;
            }


            // -------------------------
            // LOGICAL
            // -------------------------

            // ANL A,#data
            case ANL_A_IMM: {

                int a =
                        registers.getAccumulator();

                int operand =
                        instruction.getOperand();

                registers.setAccumulator(
                        a & operand
                );

                break;
            }


            // -------------------------
            // INCREMENT / DECREMENT
            // -------------------------

            // INC A
            case INC_A: {

                int a =
                        registers.getAccumulator();

                registers.setAccumulator(
                        a + 1
                );

                break;
            }


            // DEC A
            case DEC_A: {

                int a =
                        registers.getAccumulator();

                registers.setAccumulator(
                        a - 1
                );

                break;
            }


            // -------------------------
            // CONTROL FLOW
            // -------------------------

            // SJMP rel
            case SJMP:

                /*
                 * At this point PC already points
                 * to the next instruction.
                 *
                 * Therefore:
                 *
                 * new PC = current PC + offset
                 */
                int pc =
                        registers.getPC();

                int offset =
                        instruction.getOperand();

                registers.setPC(
                        pc + offset
                );

                break;


            // -------------------------
            // TERMINATION
            // -------------------------

            // HALT
            case HALT:

                // CPU handles the halted state.
                // No register operation is needed here.
                break;


            default:

                throw new UnsupportedOperationException(
                        "Instruction not implemented: "
                                + instruction.getOpcode()
                );
        }
    }
}