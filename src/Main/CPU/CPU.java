package Main.CPU;

public class CPU {

    private final Registers registers;

    private boolean halted;

    public CPU() {
        registers = new Registers();
        halted = false;
    }

    // Get the CPU registers
    public Registers getRegisters() {
        return registers;
    }

    // FETCH stage
    public void fetch() {
        System.out.println("FETCH");
    }

    // DECODE stage
    public void decode() {
        System.out.println("DECODE");
    }

    // EXECUTE stage
    public void execute() {
        System.out.println("EXECUTE");
    }

    // Reset the CPU
    public void reset() {
        registers.reset();
        halted = false;
    }

    // Check whether CPU has stopped execution
    public boolean isHalted() {
        return halted;
    }
}