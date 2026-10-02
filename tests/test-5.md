# Week-4 IPC & Multi-Process Test Results

Produced by `tests/run_ipc_tests.sh` on the real three-process system (C launcher: pipe/fork/dup2/exec).
Result of the last run: **37 / 37 checks passed**. Full table with expected/actual results: `docs/WEEK4_IPC.md` section 6.

| Test | Operation | Status |
| :--- | :--- | :--- |
| TC01 | UI to Core STEP | PASS |
| TC02 | RESET | PASS |
| TC03 | LOAD | PASS |
| TC04 | RUN (Core pushes HALTED state) | PASS |
| TC05 | PAUSE (RUNNING to PAUSED, 6 ms) | PASS |
| TC06 | GET_STATE | PASS |
| TC07 | Invalid commands, Core stays alive | PASS |
| TC08 | Execution log appears in log file | PASS |
| TC09 | Error log appears in log file | PASS |
| TC10 / TC10b | Clean shutdown / UI crash, no orphans | PASS |
| TC11 | Processes separate, joined only by pipes | PASS |
| TC12 | UI/Core/Logger source separation rules | PASS |
| TC13 | NOP regression | PASS |

Note: the older `tests/IPCTest.java` uses Java `assert` and only checks anything when run with `java -ea`.
