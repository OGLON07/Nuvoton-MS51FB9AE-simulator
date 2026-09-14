package Main.Queue;

import Main.Memory.Memory;

/**
 * FIFO Queue backed by a fixed circular buffer in Data Memory (RAM).
 *
 * This class is a read-only observer: it reads queue state
 * from predetermined RAM addresses so the UI can display
 * the queue contents, head/tail pointers, and empty/full status.
 *
 * RAM layout (agreed partitioning):
 *   0x30       Head pointer  (index of next element to dequeue)
 *   0x31       Tail pointer  (index of next free slot for enqueue)
 *   0x32       Count         (number of elements currently in queue)
 *   0x40-0x47  Buffer        (8-entry circular buffer)
 *
 * No new opcodes are needed. Queue operations are performed
 * by assembly programs using existing MOV/INC/DEC instructions.
 */
public class Queue {

    /** RAM address storing the head pointer. */
    public static final int HEAD_ADDR = 0x30;

    /** RAM address storing the tail pointer. */
    public static final int TAIL_ADDR = 0x31;

    /** RAM address storing the element count. */
    public static final int COUNT_ADDR = 0x32;

    /** First RAM address of the circular buffer. */
    public static final int BUFFER_START = 0x40;

    /** Maximum number of elements the queue can hold. */
    public static final int CAPACITY = 8;

    private final Memory memory;

    // ---------- Constructor ----------

    public Queue(Memory memory) {

        if (memory == null) {
            throw new IllegalArgumentException(
                    "Memory cannot be null"
            );
        }

        this.memory = memory;
    }

    // ---------- Pointer accessors ----------

    /** Returns the current head index (read from RAM[0x30]). */
    public int getHead() {
        return memory.readData(HEAD_ADDR);
    }

    /** Returns the current tail index (read from RAM[0x31]). */
    public int getTail() {
        return memory.readData(TAIL_ADDR);
    }

    /** Returns the number of elements currently in the queue. */
    public int getCount() {
        return memory.readData(COUNT_ADDR);
    }

    /** Returns the maximum queue capacity. */
    public int getCapacity() {
        return CAPACITY;
    }

    // ---------- Status ----------

    /** Returns true if the queue contains no elements. */
    public boolean isEmpty() {
        return getCount() == 0;
    }

    /** Returns true if the queue is at full capacity. */
    public boolean isFull() {
        return getCount() >= CAPACITY;
    }

    // ---------- Contents ----------

    /**
     * Returns the queue contents in FIFO order (head to tail).
     *
     * Reads buffer elements starting from the head index
     * and wrapping around circularly for 'count' elements.
     *
     * @return array of values in dequeue order
     */
    public int[] getContentsInOrder() {

        int count = getCount();
        int head = getHead();
        int[] contents = new int[count];

        for (int i = 0; i < count; i++) {

            int index = (head + i) % CAPACITY;

            contents[i] = memory.readData(
                    BUFFER_START + index
            );
        }

        return contents;
    }

    /**
     * Reads the raw buffer value at the given buffer index (0-7).
     *
     * @param index buffer index (0 to CAPACITY-1)
     * @return 8-bit value stored at BUFFER_START + index
     */
    public int getBufferElement(int index) {

        if (index < 0 || index >= CAPACITY) {
            throw new IllegalArgumentException(
                    "Buffer index must be between 0 and "
                            + (CAPACITY - 1)
            );
        }

        return memory.readData(BUFFER_START + index);
    }
}
