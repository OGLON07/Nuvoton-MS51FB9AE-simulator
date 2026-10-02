package Main.core.state;

import Main.CPU.CPU;
import Main.CPU.Registers;
import Main.Memory.Memory;
import Main.Queue.Queue;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Immutable, serializable snapshot of the entire simulator state.
 *
 * Designed as a Data Transfer Object (DTO) so that a decoupled UI
 * process can render the simulator without holding any live
 * references to the Core's mutable hardware objects.
 *
 * All arrays are defensively copied on construction to guarantee
 * immutability even if the caller retains a reference to the
 * original arrays.
 */
public final class CoreStateSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== Registers ====================

    /** General-purpose registers R0–R7. */
    private final int[] registers;

    /** Accumulator (A). */
    private final int accumulator;

    /** B register. */
    private final int bRegister;

    /** Program Counter. */
    private final int pc;

    /** Stack Pointer. */
    private final int sp;

    /** Program Status Word (full byte). */
    private final int psw;

    // ---- Individual PSW flag bits ----
    private final boolean carryFlag;       // CY  — PSW.7
    private final boolean auxCarryFlag;    // AC  — PSW.6
    private final boolean f0Flag;          // F0  — PSW.5
    private final boolean rs1Flag;         // RS1 — PSW.4
    private final boolean rs0Flag;         // RS0 — PSW.3
    private final boolean overflowFlag;    // OV  — PSW.2
    private final boolean parityFlag;      // P   — PSW.0

    // ==================== Memory ====================

    /** Complete program memory image (ROM — 16 KB). */
    private final int[] programMemory;

    /** Number of bytes actually loaded into program memory. */
    private final int programSize;

    /** Complete data memory image (RAM — 256 bytes). */
    private final int[] dataMemory;

    // ==================== Stack ====================

    /**
     * Stack contents read from data memory between the
     * reset vector (0x08) and the current SP, inclusive.
     * Empty array if SP == 0x07 (nothing pushed).
     */
    private final int[] stackContents;

    // ==================== Queue ====================

    /** Queue contents in FIFO order (head → tail). */
    private final int[] queueContents;

    /** Queue head pointer. */
    private final int queueHead;

    /** Queue tail pointer. */
    private final int queueTail;

    /** Number of elements in the queue. */
    private final int queueCount;

    /** Maximum queue capacity. */
    private final int queueCapacity;

    // ==================== Execution Status ====================

    /** True if the CPU has encountered a HALT instruction. */
    private final boolean halted;

    /** True if the execution worker is currently running. */
    private final boolean executing;

    /** Human-readable error message from the last failed operation, or null. */
    private final String lastError;

    /** Total number of instruction cycles executed since last reset. */
    private final long cycleCount;

    // ==================== Constructor ====================

    private CoreStateSnapshot(Builder b) {
        this.registers      = b.registers.clone();
        this.accumulator    = b.accumulator;
        this.bRegister      = b.bRegister;
        this.pc             = b.pc;
        this.sp             = b.sp;
        this.psw            = b.psw;
        this.carryFlag      = b.carryFlag;
        this.auxCarryFlag   = b.auxCarryFlag;
        this.f0Flag         = b.f0Flag;
        this.rs1Flag        = b.rs1Flag;
        this.rs0Flag        = b.rs0Flag;
        this.overflowFlag   = b.overflowFlag;
        this.parityFlag     = b.parityFlag;
        this.programMemory  = b.programMemory.clone();
        this.programSize    = b.programSize;
        this.dataMemory     = b.dataMemory.clone();
        this.stackContents  = b.stackContents.clone();
        this.queueContents  = b.queueContents.clone();
        this.queueHead      = b.queueHead;
        this.queueTail      = b.queueTail;
        this.queueCount     = b.queueCount;
        this.queueCapacity  = b.queueCapacity;
        this.halted         = b.halted;
        this.executing      = b.executing;
        this.lastError      = b.lastError;
        this.cycleCount     = b.cycleCount;
    }

    // ==================== Public Accessors (all return copies) ====================

    public int[] getRegisters()      { return registers.clone(); }
    public int   getRegister(int i)  { return registers[i]; }
    public int   getAccumulator()    { return accumulator; }
    public int   getBRegister()      { return bRegister; }
    public int   getPC()             { return pc; }
    public int   getSP()             { return sp; }
    public int   getPSW()            { return psw; }

    public boolean isCarryFlag()     { return carryFlag; }
    public boolean isAuxCarryFlag()  { return auxCarryFlag; }
    public boolean isF0Flag()        { return f0Flag; }
    public boolean isRS1Flag()       { return rs1Flag; }
    public boolean isRS0Flag()       { return rs0Flag; }
    public boolean isOverflowFlag()  { return overflowFlag; }
    public boolean isParityFlag()    { return parityFlag; }

    public int[] getProgramMemory()  { return programMemory.clone(); }
    public int   getProgramSize()    { return programSize; }
    public int[] getDataMemory()     { return dataMemory.clone(); }

    public int[] getStackContents()  { return stackContents.clone(); }

    public int[] getQueueContents()  { return queueContents.clone(); }
    public int   getQueueHead()      { return queueHead; }
    public int   getQueueTail()      { return queueTail; }
    public int   getQueueCount()     { return queueCount; }
    public int   getQueueCapacity()  { return queueCapacity; }

    public boolean isHalted()        { return halted; }
    public boolean isExecuting()     { return executing; }
    public String  getLastError()    { return lastError; }
    public long    getCycleCount()   { return cycleCount; }

    // ==================== Factory — capture from live hardware ====================

    /**
     * Captures an immutable snapshot from the live CPU, Memory, and
     * Queue objects. This is the primary way snapshots are created
     * inside the Core engine.
     *
     * @param cpu           the live CPU instance
     * @param queue         the live Queue observer
     * @param isExecuting   whether the execution worker is currently running
     * @param lastError     last error string, or null
     * @param cycleCount    total instruction cycles since last reset
     * @return an immutable snapshot of the current simulator state
     */
    public static CoreStateSnapshot capture(CPU cpu,
                                            Queue queue,
                                            boolean isExecuting,
                                            String lastError,
                                            long cycleCount) {
        Registers regs = cpu.getRegisters();
        Memory    mem  = cpu.getMemory();

        Builder b = new Builder();

        // Registers
        int[] r = new int[8];
        for (int i = 0; i < 8; i++) {
            r[i] = regs.getR(i);
        }
        b.registers    = r;
        b.accumulator  = regs.getAccumulator();
        b.bRegister    = regs.getB();
        b.pc           = regs.getPC();
        b.sp           = regs.getSP();
        b.psw          = regs.getPSW();

        // PSW flags
        b.carryFlag    = regs.isCarry();
        b.auxCarryFlag = regs.isAuxiliaryCarry();
        b.overflowFlag = regs.isOverflow();
        // F0 — PSW bit 5
        b.f0Flag       = (regs.getPSW() & 0x20) != 0;
        // RS1 — PSW bit 4
        b.rs1Flag      = (regs.getPSW() & 0x10) != 0;
        // RS0 — PSW bit 3
        b.rs0Flag      = (regs.getPSW() & 0x08) != 0;
        // P — PSW bit 0
        b.parityFlag   = (regs.getPSW() & 0x01) != 0;

        // Program Memory (only the loaded portion for efficiency)
        int progSize = mem.getProgramSize();
        int[] prog = new int[progSize];
        for (int i = 0; i < progSize; i++) {
            prog[i] = mem.readProgram(i);
        }
        b.programMemory = prog;
        b.programSize   = progSize;

        // Data Memory (full 256 bytes)
        int[] data = new int[Memory.RAM_SIZE];
        for (int i = 0; i < Memory.RAM_SIZE; i++) {
            data[i] = mem.readData(i);
        }
        b.dataMemory = data;

        // Stack contents (from 0x08 up to current SP, inclusive)
        int sp = regs.getSP();
        if (sp > 0x07) {
            int depth = sp - 0x07;
            int[] stack = new int[depth];
            for (int i = 0; i < depth; i++) {
                stack[i] = mem.readData(0x08 + i);
            }
            b.stackContents = stack;
        } else {
            b.stackContents = new int[0];
        }

        // Queue
        b.queueContents = queue.getContentsInOrder();
        b.queueHead     = queue.getHead();
        b.queueTail     = queue.getTail();
        b.queueCount    = queue.getCount();
        b.queueCapacity = queue.getCapacity();

        // Execution status
        b.halted     = cpu.isHalted();
        b.executing  = isExecuting;
        b.lastError  = lastError;
        b.cycleCount = cycleCount;

        return new CoreStateSnapshot(b);
    }

    // ==================== Builder (package-private) ====================

    /**
     * Internal builder used only by the {@link #capture} factory.
     * Not exposed publicly because snapshots should only be created
     * from live hardware state.
     */
    static final class Builder {
        int[]   registers      = new int[8];
        int     accumulator;
        int     bRegister;
        int     pc;
        int     sp;
        int     psw;
        boolean carryFlag;
        boolean auxCarryFlag;
        boolean f0Flag;
        boolean rs1Flag;
        boolean rs0Flag;
        boolean overflowFlag;
        boolean parityFlag;
        int[]   programMemory  = new int[0];
        int     programSize;
        int[]   dataMemory     = new int[0];
        int[]   stackContents  = new int[0];
        int[]   queueContents  = new int[0];
        int     queueHead;
        int     queueTail;
        int     queueCount;
        int     queueCapacity;
        boolean halted;
        boolean executing;
        String  lastError;
        long    cycleCount;
    }

    @Override
    public String toString() {
        return "CoreStateSnapshot{" +
               "PC=0x" + Integer.toHexString(pc).toUpperCase() +
               ", SP=0x" + Integer.toHexString(sp).toUpperCase() +
               ", A=0x" + Integer.toHexString(accumulator).toUpperCase() +
               ", B=0x" + Integer.toHexString(bRegister).toUpperCase() +
               ", PSW=0x" + Integer.toHexString(psw).toUpperCase() +
               ", halted=" + halted +
               ", executing=" + executing +
               ", cycles=" + cycleCount +
               (lastError != null ? ", error=" + lastError : "") +
               '}';
    }
}
