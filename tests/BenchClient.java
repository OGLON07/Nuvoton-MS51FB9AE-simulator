import Main.IPC.IPCProtocol;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/**
 * MULTI-PROCESS benchmark client. Runs as the "UI process" inside the real
 * three-process system and measures the same workload as BenchStandalone, but
 * every operation goes through the pipes UI -> Core (-> Logger).
 * Output: one CSV line appended to the file named by env BENCH_OUT.
 */
public class BenchClient {
    private static final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
    private static PrintWriter toCore;

    private static String await(Predicate<String> p, long ms) throws InterruptedException {
        long end = System.nanoTime() + ms * 1_000_000L;
        while (true) {
            long left = (end - System.nanoTime()) / 1_000_000L;
            if (left <= 0) return null;
            String l = inbox.poll(left, TimeUnit.MILLISECONDS);
            if (l == null) return null;
            if (p.test(l)) return l;
        }
    }
    private static String rt(String cmd) throws InterruptedException {      // round trip
        toCore.println(cmd);
        String r = await(x -> x.startsWith("RSP|"), 10000);
        if (r == null) throw new IllegalStateException("no reply to " + cmd);
        return r;
    }
    private static String f(double v) { return String.format(java.util.Locale.ROOT, "%.4f", v); }

    public static void main(String[] args) throws Exception {
        InputStream pipeIn = new FileInputStream(FileDescriptor.in);
        toCore = new PrintWriter(new OutputStreamWriter(new FileOutputStream(FileDescriptor.out)), true);
        System.setOut(System.err);
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(pipeIn))) {
                String l; while ((l = br.readLine()) != null) inbox.add(l);
            } catch (IOException ignored) { }
        });
        reader.setDaemon(true); reader.start();

        long ui = ProcessHandle.current().pid();
        long core = Long.parseLong(System.getenv("NUVOTON_CORE_PID"));
        long log  = Long.parseLong(System.getenv("NUVOTON_LOGGER_PID"));
        int[] prog = BenchCommon.workload();
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < prog.length; i++) csv.append(i == 0 ? "" : ",").append(prog[i]);

        rt("CMD|GET_STATE");                                   // wait until Core is up

        double uiC0 = BenchCommon.cpuMs(ui), coC0 = BenchCommon.cpuMs(core), loC0 = BenchCommon.cpuMs(log);

        StringBuilder small = new StringBuilder();
        for (int i = 0; i < BenchCommon.smallLoop().length; i++) small.append(i == 0 ? "" : ",").append(BenchCommon.smallLoop()[i]);
        rt("CMD|LOAD|" + small);

        // --- pure IPC round trip: GET_STATE (no CPU work, only snapshot + pipes)
        double[] getNs = new double[BenchCommon.STEP_SAMPLES];
        for (int i = 0; i < getNs.length; i++) { long t = System.nanoTime(); rt("CMD|GET_STATE"); getNs[i] = System.nanoTime() - t; }

        // --- STEP round trip (small program, comparable with standalone step())
        double[] stepNs = new double[BenchCommon.STEP_SAMPLES];
        for (int i = 0; i < stepNs.length; i++) { long t = System.nanoTime(); rt("CMD|STEP"); stepNs[i] = System.nanoTime() - t; }

        // --- STEP round trip with the 8 KB program image loaded (snapshot carries all 8001 bytes)
        rt("CMD|LOAD|" + csv);
        double[] bigNs = new double[300];
        for (int i = 0; i < bigNs.length; i++) { long t = System.nanoTime(); rt("CMD|STEP"); bigNs[i] = System.nanoTime() - t; }

        // --- RUN to HALT, repeated (Core pushes the final HALTED state)
        double[] runMs = new double[BenchCommon.RUN_REPEATS];
        for (int r = 0; r < runMs.length; r++) {
            rt("CMD|LOAD|" + csv);
            while (inbox.poll(20, TimeUnit.MILLISECONDS) != null) { }
            long t = System.nanoTime();
            toCore.println("CMD|RUN");
            String done = await(x -> x.startsWith("RSP|OK|") && x.contains("HALTED=1"), 20000);
            runMs[r] = (System.nanoTime() - t) / 1e6;
            if (done == null) throw new IllegalStateException("RUN did not finish");
        }

        double uiC = BenchCommon.cpuMs(ui) - uiC0, coC = BenchCommon.cpuMs(core) - coC0, loC = BenchCommon.cpuMs(log) - loC0;
        String line = String.join(",",
            f(BenchCommon.median(getNs) / 1000.0), f(BenchCommon.mean(getNs) / 1000.0),     // ipc_rtt_us median/mean
            f(BenchCommon.median(stepNs) / 1000.0), f(BenchCommon.mean(stepNs) / 1000.0),   // step_us median/mean
            f(BenchCommon.pct(stepNs, 95) / 1000.0),                                        // step_us p95
            f(BenchCommon.median(runMs)), f(BenchCommon.mean(runMs)),                       // run_ms median/mean
            f(uiC), f(coC), f(loC),                                                         // cpu_ms ui/core/logger
            f(BenchCommon.statusMb(ui, "VmRSS")), f(BenchCommon.statusMb(core, "VmRSS")), f(BenchCommon.statusMb(log, "VmRSS")),
            f(BenchCommon.statusMb(ui, "VmHWM")), f(BenchCommon.statusMb(core, "VmHWM")), f(BenchCommon.statusMb(log, "VmHWM")),
            f(BenchCommon.median(bigNs) / 1000.0));                                         // step_us median, 8 KB image
        Files.write(Paths.get(System.getenv("BENCH_OUT")), (line + "\n").getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        rt("CMD|SHUTDOWN");
        System.exit(0);
    }
}
