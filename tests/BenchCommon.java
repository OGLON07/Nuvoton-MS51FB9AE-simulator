import java.nio.file.*;
import java.util.*;

/** Shared helpers for the standalone and multi-process benchmarks (Linux /proc based). */
public class BenchCommon {
    public static final int RUN_INSTRUCTIONS = 8000;     // INC A x 8000 (+ HALT)
    public static final int STEP_SAMPLES     = 2000;
    public static final int RUN_REPEATS      = 20;

    /** Workload: 8000 x INC A (0x04) followed by HALT (0xFF) = 8001 instructions. */
    public static int[] workload() {
        int[] p = new int[RUN_INSTRUCTIONS + 1];
        Arrays.fill(p, 0x04);
        p[RUN_INSTRUCTIONS] = 0xFF;
        return p;
    }

    /** Small endless loop used for STEP / GET_STATE: MOV A,#1 ; INC A ; SJMP -3  (5 bytes). */
    public static int[] smallLoop() { return new int[] { 0x74, 0x01, 0x04, 0x80, 0xFD }; }

    /** CPU time (user+system) of a process in milliseconds, from /proc/pid/stat. */
    public static double cpuMs(long pid) {
        try {
            String s = new String(Files.readAllBytes(Paths.get("/proc/" + pid + "/stat")));
            String[] f = s.substring(s.lastIndexOf(')') + 2).split(" ");
            long ticks = Long.parseLong(f[11]) + Long.parseLong(f[12]);   // utime, stime
            return ticks * 1000.0 / 100.0;                                 // CLK_TCK = 100 on Linux
        } catch (Exception e) { return -1; }
    }

    /** Reads a kB field (VmRSS, VmHWM) from /proc/pid/status, returns MB. */
    public static double statusMb(long pid, String key) {
        try {
            for (String line : Files.readAllLines(Paths.get("/proc/" + pid + "/status"))) {
                if (line.startsWith(key + ":")) {
                    return Long.parseLong(line.replaceAll("[^0-9]", "")) / 1024.0;
                }
            }
        } catch (Exception ignored) { }
        return -1;
    }

    public static double median(double[] a) {
        double[] c = a.clone(); Arrays.sort(c);
        return c.length % 2 == 1 ? c[c.length / 2] : (c[c.length / 2 - 1] + c[c.length / 2]) / 2;
    }
    public static double mean(double[] a) { double s = 0; for (double v : a) s += v; return s / a.length; }
    public static double pct(double[] a, double p) {
        double[] c = a.clone(); Arrays.sort(c);
        return c[Math.min(c.length - 1, (int) Math.ceil(p / 100.0 * c.length) - 1)];
    }
}
