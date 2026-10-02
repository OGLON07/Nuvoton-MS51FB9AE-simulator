# Week 4 — Three-Process Simulator with POSIX Pipe IPC

## 1. Architecture

```
                     native/posix_launcher.c   (C: pipe() + fork() + dup2() + exec)
                      creates the pipes, starts the 3 processes, supervises them
                                         |
        +--------------------------------+--------------------------------+
        v                                v                                v
 +--------------+   pipe ui_to_core   +----------------+   pipe core_to_log   +----------------+
 |  UI PROCESS  | ------------------> |  CORE PROCESS  | -------------------> | LOGGER PROCESS |
 | Swing GUI    |   fd1  ->  fd0      | command loop   |   fd3  ->  fd0       | log queue +    |
 | buttons,     | <------------------ | CPU, Memory,   |                      | log file       |
 | panels       |   pipe core_to_ui   | Stack, Queue   |                      | simulator.log  |
 +--------------+   fd0  <-  fd1      +----------------+                      +----------------+
```

| Process | Java entry point | Owns | Must NOT |
|---|---|---|---|
| UI | `Main.UI.UIProcessMain --ipc` | Swing, buttons, display panels | execute CPU, touch Memory/Stack/Queue |
| Core | `Main.core.CoreProcess --ipc` | CPU, registers, memory, stack, queue, command handling, snapshot generation | contain Swing/UI code |
| Logger | `Main.Logger.LoggerProcess --ipc` | log queue, log file | execute CPU, depend on Swing/Core |

Core internals: `CoreProcess` (read loop, stdin→stdout, logging) → `CoreCommandHandler` (STEP/RUN/PAUSE/RESET/LOAD/GET_STATE/SHUTDOWN) → `CPU` / `Memory` / `Registers` / `Queue` (the unchanged Week 1–3 classes). `CoreStateSnapshot` is the single state object sent to the UI.

The three processes are connected **only** by the three anonymous pipes above (verified by test TC11, which compares the pipe inode numbers in `/proc/<pid>/fd`). There are no sockets, no shared memory, no files used for communication (the log file is the Logger's output, not a channel).

## 2. IPC mechanism and justification

**Selected: anonymous POSIX pipes, created with `pipe()`, inherited through `fork()`, wired onto file descriptors with `dup2()`, and the JVMs started with `execvp()`.** This is done in `native/posix_launcher.c` (about 250 lines). The Java programs just read/write their own file descriptors (stdin/stdout, plus fd 3 for the Core's log pipe, opened through `/dev/fd/3`).

Honest note: Java cannot call `pipe()/fork()/dup2()` itself without JNI or the (preview in Java 21) FFM API. The C launcher is what makes this genuine POSIX IPC. The older `Launcher.java` (Java `ProcessBuilder`) is kept only as a portable fallback for machines without Ubuntu/WSL; it is OS pipes too, but it is **not** the POSIX launcher and it relays Core→Logger through the UI JVM.

Why pipes:
- Exactly the three relationships we have are one-directional streams: commands, responses, log lines. A pipe is the simplest POSIX primitive for that.
- Reliable, ordered byte stream; the kernel does the buffering and blocking (back-pressure for free).
- EOF gives a clean, signal-free shutdown cascade: UI exits → Core stdin EOF → Core exits → log pipe EOF → Logger exits (tested, TC10/TC10b).
- No extra libraries; easy to explain and to inspect (`ls -l /proc/<pid>/fd`).

Why not the alternatives: **FIFOs** (named pipes) would also work but add file-system names to create/clean up for no benefit since the launcher is the common parent. **POSIX message queues** keep message boundaries but need `-lrt`/JNI from Java and have size limits. **Shared memory + semaphores** is fastest but needs explicit synchronisation and is overkill for text commands. **Sockets** add networking concepts we do not need.

Limitations: one-directional pipes ×3 (not one duplex channel); text protocol is parsed line by line; a pipe has ~64 KB of kernel buffer, so a process that stops reading eventually blocks the writer (the design avoids this: nobody leaves a pipe unread, CPU debug prints are discarded instead of being written to a pipe).

## 3. Message protocol (one text line per message)

**UI → Core** (`CMD|<NAME>[|<payload>]`)

| Command | Example | Meaning |
|---|---|---|
| STEP | `CMD|STEP` | execute one instruction |
| RUN | `CMD|RUN` | run continuously on a worker thread |
| PAUSE | `CMD|PAUSE` | stop the worker |
| RESET | `CMD|RESET` | reset CPU/memory state, keep program |
| LOAD | `CMD|LOAD|116,66,36,5,255` | load program bytes (decimal, comma separated) |
| GET_STATE | `CMD|GET_STATE` | request a snapshot |
| SHUTDOWN | `CMD|SHUTDOWN` | stop Core |

**Core → UI**: `RSP|OK|<snapshot>` (STATE) · `RSP|ERROR|<message>` · `RSP|SHUTDOWN_ACK`.
The snapshot is `KEY=VALUE` pairs: `PC SP A B PSW R0..R7 CY AC OV HALTED EXEC CYCLES PSIZE ST LAST DM PM STK QH QT QC QCAP QD [ERR]`.
`ST` is the status word `READY | LOADED | RESET | RUNNING | PAUSED | HALTED`; `LAST` is the last executed instruction (e.g. `PC=0x0002 ADD_A_IMM 0x05`). `PM` contains only the *loaded* program bytes (`PSIZE` of them), not the full 16 KB. While RUN is active the Core also **pushes** STATE lines on its own (at most one per 50 ms, plus a final one when it stops).

**Core → Logger**: `LOG|<EXECUTION|ERROR|SYSTEM>|<epoch-ms>|<message>|<details>`, with `| , \ newline` escaped. The Logger never interprets CPU internals; the Core writes the human-readable text. Malformed lines are recorded as an ERROR entry and processing continues.

What gets logged: startup/shutdown (SYSTEM); program loaded, every STEP with its instruction, RUN started/stopped, PAUSE, RESET (EXECUTION); failed commands and invalid input (ERROR). RUN does not log per instruction (that would flood the log); GET_STATE is not logged (it is polling).

## 4. Thread model

| Process | Thread | Why |
|---|---|---|
| UI | Swing Event Dispatch Thread | button handling, drawing |
| UI | `UI-IPC-Listener` | blocking read of Core pipe; hands results to the EDT with `invokeLater` (EDT never blocks on the Core) |
| Core | main thread | read command line → handle → write response |
| Core | `Core-Execution-Worker` (only while RUN is active) | runs instructions so the main thread can still receive PAUSE/GET_STATE |
| Logger | main thread | reads the pipe, puts messages on a `BlockingQueue` |
| Logger | `Logger-Thread` | `take()`s from the queue and writes the file; no polling/sleeping; stopped with a stop marker after draining the queue |

The Core handler is synchronized; PAUSE uses `wait()/notifyAll()` (the lock is released while waiting for the worker).

## 5. How to build and run (Ubuntu / WSL)

```bash
sudo apt install openjdk-21-jdk gcc make      # JDK 11+ is enough
./build.sh                                    # javac -> out/, gcc -> bin/nuvoton_launcher
./run.sh                                      # start the 3-process simulator + GUI
tests/run_ipc_tests.sh                        # IPC / system tests
tests/run_benchmark.sh                        # standalone vs multi-process benchmark
```
Notes: the GUI needs a display (WSLg on Windows 11 works out of the box; Windows 10 needs an X server such as VcXsrv and `export DISPLAY=...`). Keep the project inside the Linux file system (`~/`), not under `/mnt/c`, and make sure `*.sh` files have LF line endings (`.gitattributes` handles this for git checkouts).

## 6. Test results

Run: `tests/run_ipc_tests.sh` (real three-process system, scripted client in place of the Swing UI) — **37 / 37 checks passed**. Unit tests: StackTest, QueueTest (6), DataMemoryTest (6), CoreProcessTest (11), legacy IPCTest (6, run with `-ea`) all pass.

| Test | Operation | Expected | Actual | Status |
|---|---|---|---|---|
| TC01 | UI→Core STEP ×2 | one instruction each; A=0x42 then 0x47; last-instruction reported | as expected | PASS |
| TC02 | RESET | PC=0, A=0, SP=7, cycles=0, status RESET | as expected | PASS |
| TC03 | LOAD 5 bytes | confirmation, status LOADED, bytes intact | as expected | PASS |
| TC04 | RUN to HALT | Core pushes final HALTED state (A=0x47, 3 cycles) | as expected | PASS |
| TC05 | PAUSE on endless loop | RUNNING→PAUSED in <1 s, CPU really stops | 6 ms, cycles frozen | PASS |
| TC06 | GET_STATE | all registers, 256-byte RAM, stack, queue, flags | as expected | PASS |
| TC07 | 4 invalid commands | 4 ERROR replies, Core alive, still answers | as expected | PASS |
| TC08 | execution logging | Program loaded / STEP+instruction / RUN / PAUSE / RESET in file | all present | PASS |
| TC09 | error logging | STEP-on-halted and invalid command logged as ERROR; no FETCH/DECODE noise | as expected | PASS |
| TC10 | SHUTDOWN | ACK, exit code 0 for all 3 children, no pid or JVM left | as expected | PASS |
| TC10b | UI killed with SIGKILL | Core and Logger exit by themselves via pipe EOF | exited, status 0, no SIGTERM needed | PASS |
| TC11 | process separation | 3 distinct pids; UI→Core, Core→UI, Core→Logger share one pipe each | pipe inodes match | PASS |
| TC12 | source rules | UI has no CPU/Memory/Queue use; Core no Swing; Logger no Swing/Core | grep clean | PASS |
| TC13 | NOP regression | NOP advances PC by 1 and next instruction is correct | as expected | PASS |

Also verified by hand on a virtual display (Xvfb + xdotool + openbox): GUI starts, shows initial Core state, LOAD/STEP/RESET/RUN/PAUSE buttons drive the Core and the Logger file, and closing the window via the window-manager close button shuts all three processes down with status 0.

## 7. Benchmark (standalone vs multi-process)

Workload: 8001-instruction program (`INC A` ×8000 + `HALT`) for RUN; a 5-byte endless loop for STEP/GET_STATE; 2000 samples each; 20 RUNs per session; 5 independent sessions per variant (fresh JVMs). Standalone = original single-process simulator, CPU called directly. Raw data: `benchmark/standalone.csv`, `benchmark/multi.csv`; regenerate with `tests/run_benchmark.sh`.

Benchmark: 5 independent sessions per variant (each session = fresh JVM(s)); values are the mean of the per-session medians.

| Metric | Standalone | Multi-process | Difference |
|---|---|---|---|
| RUN: 8001 instructions to HALT, median (ms) | 13.21 | 14.86 | +1.64 ms (+12 %) |
| STEP: one instruction, median (us) | 6.15 (direct call) | 39.6 (round trip) | +33.5 us |
| STEP: 95th percentile (us) | - | 2021.1 | - |
| STEP with an 8 KB program loaded, median (us) | - | 297.5 | snapshot carries the whole program image |
| **IPC overhead** = GET_STATE round trip, median (us) | 0 | 37.1 | +37.1 us per command |
| CPU time used during measured phase, all processes (ms) | 406 | 2186 (UI 370 + Core 1454 + Logger 362) | +1780 ms |
| Memory, resident set (MB) | 65 | 189 (UI 61 + Core 77 + Logger 51) | +124 MB |
| Memory, peak resident (MB) | 65 | 189 (UI 61 + Core 77 + Logger 51) | +125 MB |
| Start-up to clean exit (ms), mean of 5 | 84 | 277 | +193 ms |

Per-session spread (stdev across sessions): standalone RUN 1.30 ms, multi RUN 0.87 ms, multi STEP 3.7 us, IPC RTT 5.3 us.

How to read it: **CPU execution time** is the standalone RUN/STEP row. **IPC overhead** is the GET_STATE round trip (pipes + snapshot encode/decode, no CPU work). **Process-management overhead** is the start-up row (three JVMs instead of one). CPU time is read from `/proc/<pid>/stat`, which has 10 ms resolution, so only the large totals are meaningful. Memory is resident set size (`VmRSS`) per process; the extra 124 MB is mostly the two additional JVMs.

Caveats: these numbers were measured on the Linux sandbox used to develop the change, not on the team's WSL machine — run `tests/run_benchmark.sh` there and use those numbers in the report. The 95th-percentile STEP latency (~2 ms) is much higher than the median (~40 µs); the cause was not investigated.

A finding worth mentioning in the report: the first benchmark run showed STEP at ~2 ms because the snapshot serializer used `String.format` per byte for the program image (≈1.8 ms for 8 KB). Replacing it with a lookup-table encoder reduced the STEP round trip from 2001 µs to 40 µs.

## 8. Week 4 status

**Completed and tested:** three separate OS processes; pipe()/fork()/dup2()/exec launcher; UI→Core and Core→UI commands/responses for all 7 commands; Core→Logger log pipe; threading as described; clean shutdown including UI crash; NOP, PAUSE-stall and pipe-flooding bugs fixed; protocol/IPC/architecture documented; benchmark run.

**Known limitations / not done:**
- The Java `Launcher.java` fallback is not POSIX and relays logs through the UI JVM (tested only lightly).
- `UIProcessMain` without `--ipc` still uses the Mock communicator (kept for UI-only demos).
- At class level the UI JVM's classpath contains Core classes (`IPCProtocol` and `CommandMessage` live in `Main.IPC` / `Main.core.command`), though the UI never instantiates CPU/Memory/Queue (TC12). Moving the message classes to a neutral package would remove this.
- The GUI was exercised through automated clicks on a virtual display, not on real WSLg; Stack/Queue demo panels were loaded but not inspected visually.
- Benchmark figures come from a different machine than the team's WSL.
