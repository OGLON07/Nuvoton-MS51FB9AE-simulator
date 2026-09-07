package Main.Memory;

/**
 * Represents the memory of the MS51FB9AE simulator.
 *
 * The simulator uses two separate memory areas:
 *
 * 1. Program Memory (ROM)
 *    - Stores the program/instruction bytes.
 *    - Size: 16 KB
 *
 * 2. Data Memory (RAM)
 *    - Stores runtime data.
 *    - Size: 256 bytes
 *
 * int[] is used instead of byte[] so that memory values
 * can easily be represented as unsigned 8-bit values (0-255).
 */
public class Memory {

    // Program memory size = 16 KB
    public static final int ROM_SIZE = 16384;

    // Data memory size = 256 bytes
    public static final int RAM_SIZE = 256;

    // Stores program/instruction bytes
    private final int[] programMemory;

    // Stores runtime data
    private final int[] dataMemory;

    // Number of bytes currently loaded into program memory
    private int programSize;

    /**
     * Creates empty program memory and data memory.
     */
    public Memory() {
        programMemory = new int[ROM_SIZE];
        dataMemory = new int[RAM_SIZE];
        programSize = 0;
    }

    /**
     * Loads a program into program memory starting at address 0.
     *
     * @param code array containing program/instruction bytes
     */
    public void loadProgram(int[] code) {

        if (code == null) {
            throw new IllegalArgumentException(
                    "Program cannot be null"
            );
        }

        if (code.length > ROM_SIZE) {
            throw new IllegalArgumentException(
                    "Program is too large for program memory"
            );
        }

        // Clear previous program before loading the new one
        for (int i = 0; i < ROM_SIZE; i++) {
            programMemory[i] = 0;
        }

        // Store the new program
        for (int i = 0; i < code.length; i++) {
            programMemory[i] = code[i] & 0xFF;
        }

        programSize = code.length;
    }

    /**
     * Reads one byte from program memory.
     *
     * @param address program memory address
     * @return 8-bit value stored at the address
     */
    public int readProgram(int address) {

        checkProgramAddress(address);

        return programMemory[address];
    }

    /**
     * Reads one byte from data memory.
     *
     * @param address data memory address
     * @return 8-bit value stored at the address
     */
    public int readData(int address) {

        checkDataAddress(address);

        return dataMemory[address];
    }

    /**
     * Writes an 8-bit value into data memory.
     *
     * @param address data memory address
     * @param value value to store
     */
    public void writeData(int address, int value) {

        checkDataAddress(address);

        dataMemory[address] = value & 0xFF;
    }

    /**
     * Returns the number of bytes in the currently loaded program.
     *
     * This will be useful for the CPU when checking
     * whether the program has finished executing.
     *
     * @return number of loaded program bytes
     */
    public int getProgramSize() {
        return programSize;
    }

    /**
     * Clears only data memory (RAM).
     *
     * This is what the CPU should normally use during RESET,
     * because the loaded program should remain in program memory.
     */
    public void clearDataMemory() {

        for (int i = 0; i < RAM_SIZE; i++) {
            dataMemory[i] = 0;
        }
    }

    /**
     * Clears program memory and removes the loaded program.
     */
    public void clearProgramMemory() {

        for (int i = 0; i < ROM_SIZE; i++) {
            programMemory[i] = 0;
        }

        programSize = 0;
    }

    /**
     * Completely resets both program memory and data memory.
     *
     * This should only be used when we intentionally want
     * to remove the loaded program as well.
     */
    public void reset() {

        clearProgramMemory();
        clearDataMemory();
    }

    /**
     * Checks whether a program memory address is valid.
     */
    private void checkProgramAddress(int address) {

        if (address < 0 || address >= ROM_SIZE) {
            throw new IllegalArgumentException(
                    "Program memory address must be between 0 and "
                            + (ROM_SIZE - 1)
            );
        }
    }

    /**
     * Checks whether a data memory address is valid.
     */
    private void checkDataAddress(int address) {

        if (address < 0 || address >= RAM_SIZE) {
            throw new IllegalArgumentException(
                    "Data memory address must be between 0 and "
                            + (RAM_SIZE - 1)
            );
        }
    }
}
