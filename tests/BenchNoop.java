import java.io.*;
/** Startup/teardown probe. mode=standalone: build a CPU and exit. (Multi-process side uses UiIdleClient-like quick client.) */
public class BenchNoop {
    public static void main(String[] a) throws Exception {
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        Main.CPU.CPU cpu = new Main.CPU.CPU();
        cpu.getMemory().loadProgram(new int[]{0x74, 0x42, 0xFF});
        cpu.run();
    }
}
