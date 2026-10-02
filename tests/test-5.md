-------------------------------------------------------Week-4 IPC & Multi-Process Test Results-----------------------------------------------------

| Test | Operation / Description | Expected Result | Actual Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| TC01 | Command Protocol Serialization & Deserialization | All commands (STEP, RUN, PAUSE, RESET, LOAD, GET_STATE, SHUTDOWN) serialize/deserialize | Commands accurately serialized with payload handling | PASS |
| TC02 | Response & Snapshot IPC Serialization | Complete CPU, Register (A, B, PC, SP, PSW, R0-R7), Flags (CY, AC, OV), RAM, Stack, Queue snapshot serialized | Snapshot parsed and verified across all registers and memory structures | PASS |
| TC03 | LogMessage IPC Protocol & Timestamping | EXECUTION, ERROR, and SYSTEM logs with timestamp & details formatted properly | All log types serialized/deserialized with exact timestamp retention | PASS |
| TC04 | CoreProcess Subprocess IPC Execution | Child OS Process running `CoreProcess --ipc` processes stdin commands and streams stdout snapshots | GET_STATE, LOAD, STEP (A=0x55), and SHUTDOWN executed with exit code 0 | PASS |
| TC05 | LoggerProcess Subprocess IPC Execution | Child OS Process running `LoggerProcess --ipc` reads stdin and writes to `simulator.log` | Log file generated with formatted timestamps and all log categories | PASS |
| TC06 | IPC Malformed Message Resilience | System recovers from corrupted input without crash or unhandled exceptions | Malformed commands, responses, and log lines safely handled | PASS |

================================================================================================================================================
Summary: 6 / 6 Test Cases Passed (100% Pass Rate). Multi-Process IPC Architecture Fully Verified.
