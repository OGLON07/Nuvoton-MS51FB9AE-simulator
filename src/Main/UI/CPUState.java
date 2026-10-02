package Main.UI;

public class CPUState {
    public int pc;                          // Program Counter
    public int sp;                          // Stack Pointer
    public int acc;                         // Accumulator Register
    public byte[] ram = new byte[256];      // Data RAM
    public String[] stackData = new String[8]; // Formatted stack values for display
    public String lastInstruction = "";     // Trace/Status message
}
