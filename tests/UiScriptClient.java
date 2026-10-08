import Main.IPC.IPCProtocol;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Week-4 system test client.
 *
 * Runs in place of the Swing UI as the "UI process" of the real three-process
 * system started by native/posix_launcher.c. It talks to the Core ONLY through
 * its stdin/stdout pipes (fd 0 / fd 1), exactly like UIProcessMain does, and
 * checks the replies. Results go to stderr; exit code 0 = all passed.
 *
 * TC08 / TC09 / TC10 additionally need the Logger's file and the process table;
 * those are verified by tests/run_ipc_tests.sh after this client exits.
 */
public class UiScriptClient {

    private static final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
    private static PrintWriter toCore;
    private static int passed = 0, failed = 0;

    // ---------------------------------------------------------------- helpers

    private static void send(String line) {
        toCore.println(line);
    }

    /** Waits up to ms for a line satisfying p; earlier non-matching lines are discarded. */
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

    private static IPCProtocol.SnapshotData snap(String line) {
        IPCProtocol.ParsedResponse r = IPCProtocol.parseResponse(line);
        return (r != null && r.success) ? r.snapshot : null;
    }

    private static boolean isState(String l) { return l.startsWith("RSP|OK|"); }
    private static boolean isError(String l) { return l.startsWith("RSP|ERROR|"); }

    private static void check(String id, String what, boolean ok, String detail) {
        if (ok) { passed++; System.err.println("  PASS  " + id + "  " + what); }
        else    { failed++; System.err.println("  FAIL  " + id + "  " + what + "  -> " + detail); }
    }

    private static void drain() throws InterruptedException {
        while (inbox.poll(150, TimeUnit.MILLISECONDS) != null) { /* discard stale pushes */ }
    }

    private static String link(String path) {
        try { return Files.readSymbolicLink(Paths.get(path)).toString(); }
        catch (IOException e) { return "<" + e.getClass().getSimpleName() + ">"; }
    }

    private static String cmdline(long pid) {
        try { return new String(Files.readAllBytes(Paths.get("/proc/" + pid + "/cmdline"))).replace('\0', ' '); }
        catch (IOException e) { return ""; }
    }

    // ------------------------------------------------------------------- main

    public static void main(String[] args) throws Exception {
        // fd 0 / fd 1 are the pipes. Keep println() from ever touching fd 1.
        InputStream pipeIn = new FileInputStream(FileDescriptor.in);
        toCore = new PrintWriter(new OutputStreamWriter(new FileOutputStream(FileDescriptor.out)), true);
        System.setOut(System.err);

        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(pipeIn))) {
                String l;
                while ((l = br.readLine()) != null) inbox.add(l);
            } catch (IOException ignored) { }
        }, "test-pipe-reader");
        reader.setDaemon(true);
        reader.start();

        System.err.println("========================================");
        System.err.println(" Week-4 system test (UI stand-in, real pipes)");
        System.err.println("========================================");

        int[] prog = { 0x74, 0x42, 0x24, 0x05, 0xFF };     // MOV A,#0x42 ; ADD A,#5 ; HALT

        // ---- TC06: GET_STATE returns the complete state
        send("CMD|GET_STATE");
        String l = await(UiScriptClient::isState, 15000);
        IPCProtocol.SnapshotData s = l == null ? null : snap(l);
        check("TC06", "GET_STATE returns complete state",
              s != null && s.r.length == 8 && s.dataMemory.length == 256 && s.sp == 7
                && s.pc == 0 && "READY".equals(s.status) && s.stackContents != null && s.queueCapacity > 0,
              String.valueOf(l));

        // ---- TC03: LOAD
        send("CMD|LOAD|116,66,36,5,255");
        l = await(UiScriptClient::isState, 3000);
        s = l == null ? null : snap(l);
        check("TC03", "LOAD confirms program (5 bytes, status LOADED, bytes intact)",
              s != null && s.programSize == 5 && "LOADED".equals(s.status)
                && Arrays.equals(s.programMemory, prog), String.valueOf(l));

        // ---- TC01: STEP
        send("CMD|STEP");
        l = await(UiScriptClient::isState, 3000);
        s = l == null ? null : snap(l);
        check("TC01a", "STEP #1 executes MOV A,#0x42 (A=0x42, PC=2, cycles=1)",
              s != null && s.acc == 0x42 && s.pc == 2 && s.cycleCount == 1
                && s.lastInstruction != null && s.lastInstruction.contains("MOV_A_IMM"), String.valueOf(l));
        send("CMD|STEP");
        l = await(UiScriptClient::isState, 3000);
        s = l == null ? null : snap(l);
        check("TC01b", "STEP #2 executes ADD A,#5 (A=0x47, PC=4)",
              s != null && s.acc == 0x47 && s.pc == 4 && s.lastInstruction.contains("ADD_A_IMM"), String.valueOf(l));

        // ---- TC02: RESET
        send("CMD|RESET");
        l = await(UiScriptClient::isState, 3000);
        s = l == null ? null : snap(l);
        check("TC02", "RESET clears CPU (PC=0, A=0, SP=7, cycles=0, status RESET)",
              s != null && s.pc == 0 && s.acc == 0 && s.sp == 7 && s.cycleCount == 0
                && "RESET".equals(s.status), String.valueOf(l));

        // ---- TC04: RUN to HALT (Core pushes the final state on its own)
        drain();
        send("CMD|RUN");
        l = await(x -> isState(x) && x.contains("HALTED=1"), 5000);
        s = l == null ? null : snap(l);
        check("TC04", "RUN executes whole program; Core pushes HALTED state (A=0x47)",
              s != null && s.acc == 0x47 && "HALTED".equals(s.status) && s.cycleCount == 3, String.valueOf(l));

        // STEP on a halted CPU -> ERROR (also feeds TC09: Core must log it)
        drain();
        send("CMD|STEP");
        l = await(UiScriptClient::isError, 3000);
        check("TC09-pre", "STEP on halted CPU returns ERROR response", l != null, "no ERROR reply");

        // ---- TC05: PAUSE (RUNNING -> PAUSED) on an endless loop, and it must be prompt
        send("CMD|LOAD|116,1,4,128,253");               // MOV A,#1 ; INC A ; SJMP -3
        await(UiScriptClient::isState, 3000);
        send("CMD|RUN");
        l = await(x -> isState(x) && x.contains("ST=RUNNING"), 3000);
        check("TC05a", "RUN on endless loop reports RUNNING", l != null, "no RUNNING state");
        Thread.sleep(150);                               // let it execute a bit
        long t0 = System.nanoTime();
        send("CMD|PAUSE");
        l = await(x -> isState(x) && x.contains("EXEC=0"), 5000);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        s = l == null ? null : snap(l);
        check("TC05b", "PAUSE: RUNNING -> PAUSED in " + ms + " ms (limit 1000)",
              s != null && "PAUSED".equals(s.status) && ms < 1000, String.valueOf(l) + " ms=" + ms);
        drain();
        send("CMD|GET_STATE");
        IPCProtocol.SnapshotData a = snap(await(UiScriptClient::isState, 3000));
        Thread.sleep(200);
        drain();
        send("CMD|GET_STATE");
        IPCProtocol.SnapshotData b = snap(await(UiScriptClient::isState, 3000));
        check("TC05c", "after PAUSE the CPU really stopped (cycles frozen, > 0)",
              a != null && b != null && a.cycleCount == b.cycleCount && a.cycleCount > 0,
              a == null || b == null ? "null" : a.cycleCount + " vs " + b.cycleCount);

        // ---- TC13: NOP regression (used to crash with NullPointerException and skip a byte)
        send("CMD|LOAD|0,116,7,255");                    // NOP ; MOV A,#7 ; HALT
        await(UiScriptClient::isState, 3000);
        send("CMD|STEP");
        l = await(x -> isState(x) || isError(x), 3000);
        s = l == null ? null : snap(l);
        boolean nopOk = s != null && s.pc == 1 && s.lastInstruction != null && s.lastInstruction.contains("NOP");
        send("CMD|STEP");
        l = await(x -> isState(x) || isError(x), 3000);
        s = l == null ? null : snap(l);
        check("TC13", "NOP executes (PC 0->1), next MOV A,#7 runs correctly (A=7, PC=3)",
              nopOk && s != null && s.acc == 7 && s.pc == 3, String.valueOf(l));

        // ---- TC07: invalid commands -> ERROR, Core stays alive
        String[] bad = { "CMD|BOGUS", "this is not a command", "CMD|LOAD|zz,1", "CMD|LOAD|" };
        boolean allErr = true;
        for (String bcmd : bad) {
            drain();
            send(bcmd);
            if (await(UiScriptClient::isError, 3000) == null) { allErr = false; System.err.println("      no ERROR for: " + bcmd); }
        }
        drain();
        send("CMD|GET_STATE");
        l = await(UiScriptClient::isState, 3000);
        long corePid = Long.parseLong(System.getenv("NUVOTON_CORE_PID"));
        long loggerPid = Long.parseLong(System.getenv("NUVOTON_LOGGER_PID"));
        long uiPid = ProcessHandle.current().pid();
        check("TC07", "4 invalid commands -> 4 ERROR replies; Core still answers & process alive",
              allErr && l != null && Files.exists(Paths.get("/proc/" + corePid)), String.valueOf(l));

        // ---- TC11: processes are separate and joined only by pipes
        boolean distinct = uiPid != corePid && corePid != loggerPid && uiPid != loggerPid;
        boolean names = cmdline(corePid).contains("Main.core.CoreProcess")
                     && cmdline(loggerPid).contains("Main.Logger.LoggerProcess");
        String uiOut  = link("/proc/" + uiPid   + "/fd/1");
        String coreIn = link("/proc/" + corePid + "/fd/0");
        String coreOut= link("/proc/" + corePid + "/fd/1");
        String uiIn   = link("/proc/" + uiPid   + "/fd/0");
        String coreLog= link("/proc/" + corePid + "/fd/3");
        String logIn  = link("/proc/" + loggerPid + "/fd/0");
        boolean pipes = uiOut.startsWith("pipe:") && uiOut.equals(coreIn)
                     && coreOut.startsWith("pipe:") && coreOut.equals(uiIn)
                     && coreLog.startsWith("pipe:") && coreLog.equals(logIn);
        System.err.println("      pids: UI=" + uiPid + " CORE=" + corePid + " LOGGER=" + loggerPid);
        System.err.println("      UI.fd1 " + uiOut + "  == CORE.fd0 " + coreIn);
        System.err.println("      CORE.fd1 " + coreOut + "  == UI.fd0 " + uiIn);
        System.err.println("      CORE.fd3 " + coreLog + "  == LOGGER.fd0 " + logIn);
        check("TC11", "3 distinct OS processes; UI->Core, Core->UI, Core->Logger each share one pipe",
              distinct && names && pipes, "distinct=" + distinct + " names=" + names + " pipes=" + pipes);

        // ---- TC10 (part 1): SHUTDOWN acknowledged. Shell script verifies teardown.
        send("CMD|SHUTDOWN");
        l = await(x -> x.equals("RSP|SHUTDOWN_ACK"), 5000);
        check("TC10a", "SHUTDOWN answered with SHUTDOWN_ACK", l != null, "no ack");

        System.err.printf("  client results: %d passed, %d failed%n", passed, failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
