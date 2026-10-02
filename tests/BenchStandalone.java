import Main.CPU.CPU;
import java.io.*;
import java.nio.file.*;

/**
 * BASELINE: the original single-process simulator. The Week-3 CPU is called
 * directly (no IPC, no extra processes). Same workload as BenchClient.
 * Output: one CSV line appended to the file given as args[0].
 */
public class BenchStandalone {
    public static void main(String[] args) throws Exception {
        // the Week-3 CPU prints FETCH/DECODE lines; discard them exactly as the Core does
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        long self = ProcessHandle.current().pid();
        int[] prog = BenchCommon.workload();
        CPU cpu = new CPU();

        double cpu0 = BenchCommon.cpuMs(self);

        // --- STEP: individual step() calls (small 5-byte loop, same as the multi-process side)
        cpu.getMemory().loadProgram(BenchCommon.smallLoop());
        double[] stepNs = new double[BenchCommon.STEP_SAMPLES];
        for (int i = 0; i < stepNs.length; i++) {
            long t = System.nanoTime();
            cpu.step();
            stepNs[i] = System.nanoTime() - t;
        }

        // --- RUN: whole program to HALT, repeated
        double[] runMs = new double[BenchCommon.RUN_REPEATS];
        for (int r = 0; r < runMs.length; r++) {
            cpu.reset();
            cpu.getMemory().loadProgram(prog);
            long t = System.nanoTime();
            cpu.run();
            runMs[r] = (System.nanoTime() - t) / 1e6;
        }

        double cpuUsed = BenchCommon.cpuMs(self) - cpu0;
        String line = String.join(",",
            f(BenchCommon.median(stepNs) / 1000.0),      // step_us_median
            f(BenchCommon.mean(stepNs) / 1000.0),        // step_us_mean
            f(BenchCommon.median(runMs)),                // run_ms_median
            f(BenchCommon.mean(runMs)),                  // run_ms_mean
            f(cpuUsed),                                  // cpu_ms during measured phase
            f(BenchCommon.statusMb(self, "VmRSS")),      // rss_mb
            f(BenchCommon.statusMb(self, "VmHWM")));     // peak rss_mb
        Files.write(Paths.get(args[0]), (line + "\n").getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    private static String f(double v) { return String.format(java.util.Locale.ROOT, "%.4f", v); }
}
