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
