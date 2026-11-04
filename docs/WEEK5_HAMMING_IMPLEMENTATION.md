# Week 5 — Assembly Editor, Hamming (7,4), and ErrorDetection.log

## 1. Existing UI workflow

The existing **HAMMING DEMO** button is retained. It places the Hamming assembly source in the editable PROGRAM text area and loads the assembled bytes into the Core process. The user can inspect or edit the source before loading it. No additional Hamming button or separate logger process is introduced.

To run the demonstration:

1. Start the simulator using the normal project launcher.
2. Click **HAMMING DEMO**.
3. Review the assembly source in the PROGRAM editor.
4. Press **RUN** (or use STEP repeatedly until HALT).
5. After the Hamming program reaches HALT, the Core sends the assembly-produced result to the existing Logger process.
6. The Logger writes Hamming records to `ErrorDetection.log`; ordinary application events continue to be written to `simulator.log`.

## 2. Hamming algorithm

The demonstration implements ordinary Hamming (7,4) with even parity. It uses input nibble `0x0B` as a sample value, calculates P1/P2/P4, constructs a seven-bit codeword, and calculates the syndrome.

Codeword positions:

| Position | Meaning |
|---:|---|
| 1 | P1 |
| 2 | P2 |
| 3 | D1 |
| 4 | P4 |
| 5 | D2 |
| 6 | D3 |
| 7 | D4 |

Even-parity equations:

- `P1 = D1 XOR D2 XOR D4`
- `P2 = D1 XOR D3 XOR D4`
- `P4 = D2 XOR D3 XOR D4`

For input `0x0B` (data bits D1..D4 = 1,1,0,1), the expected parity bits are P1=1, P2=0, P4=0. The codeword in the order P1,P2,D1,P4,D2,D3,D4 is `1010101`, or `0x55`.

The syndrome is assembled as `S1 + 2*S2 + 4*S4`. A zero syndrome means no error is detected in the checked codeword. A non-zero syndrome identifies a bit position under the single-bit-error assumption. Ordinary Hamming (7,4) corrects one-bit errors but cannot reliably distinguish all multiple-bit errors.

## 3. RAM layout used by the demo

| Address | Purpose |
|---|---|
| `0x20` | Input nibble (`0x0B`) |
| `0x21` | P1 temporary parity |
| `0x22` | P2 temporary parity |
| `0x23` | P4 temporary parity |
| `0x24` | Seven-bit Hamming codeword |
| `0x28` | S1 check result |
| `0x29` | S2 check result |
| `0x2A` | S4 check result |
| `0x2B` | Combined syndrome (bit 0=S1, bit 1=S2, bit 2=S4) |
| `0x2C` | Correction-path marker (1 when the single-bit correction branch is taken) |
| `0x2D` | Completion signature `0x48`; used by Core to recognise a completed Hamming demo |

The simulator implements bit-addressable internal RAM bit addresses `0x00`–`0x7F`, mapped to data RAM bytes `0x20`–`0x2F`.

## 4. ErrorDetection.log integration

The Hamming algorithm itself runs in assembly. It calculates parity and syndrome and writes the resulting values into RAM. It does not call Java to perform parity calculations.

When the assembly program reaches HALT after writing completion signature `0x48` to RAM address `0x2D`, the Core reads the already-produced result fields from the state snapshot and sends a `HAMMING` log message through the existing Core-to-Logger POSIX pipe. The existing Logger process routes these messages to `ErrorDetection.log`. It continues to route normal system/execution/error messages to `simulator.log`.

A typical no-error record has this general form:

```text
[date time] [HAMMING] NO ERROR DETECTED | input=0xB codeword=0x55 syndrome=0 (0b000) correctionMarker=0; scope=Hamming demo codeword in data RAM (not the entire program memory)
```

The log is appended to on each completed Hamming demo. It is not created with a fabricated error record. If a non-zero syndrome is produced, the log reports `ERROR DETECTED`, the syndrome and whether the correction branch was taken.

## 5. Instruction-by-instruction Hamming report

After the demo reaches HALT and writes its completion signature, Core produces a report for the assembled Hamming demo program:

1. Core reads the loaded program-byte snapshot and groups bytes into the supported instruction boundaries.
2. The high and low nibbles of each byte are checked using the existing Hamming assembly routine.
3. The routine is executed once for each possible nibble value (0 through 15); the assembly-produced completion marker and syndrome are collected.
4. Each decoded instruction is sent to the Logger with its address, encoded bytes, and PASS / CHECK FAILED status.
5. The Logger writes these records and the final summary to `ErrorDetection.log`.

No new CPU register or instruction was added. The Hamming parity and syndrome calculations remain in assembly; Core only coordinates the runs and formats the instruction report.

The current run produced 176 instruction records across 372 program bytes, with 176 passing and zero failing.

## 6. Important scope and limitations

- The per-instruction report covers the loaded Hamming demo program, not every arbitrary program the user may load into the simulator.
- Each nibble is encoded into a fresh Hamming codeword and that generated codeword's syndrome is checked. This validates the assembly encoder/checker path for every nibble value and applies that validation to every program byte.
- This is **not** a comparison against parity stored before a possible corruption. It cannot detect a byte that was already corrupted before the fresh codeword was generated. A true corruption-detection stage requires trusted parity to be stored separately before corruption and checked later.
- The assembly subset does not gain `DPTR`, `MOVC`, or any other new microcontroller instruction. Core supplies the bytes from the program-memory snapshot to the verifier.
- The correction path still needs a post-correction syndrome recomputation before a correction can be reported as independently verified.
- The correction branch maps a non-zero syndrome to a codeword bit and toggles that bit. The current demo does not recompute all syndrome checks after correction, so the log must not claim that a correction was independently verified.
- Hamming code detects bit corruption in data protected by stored parity. It does not detect ordinary software logic mistakes such as an incorrect arithmetic implementation or wrong branch condition.
- Standard Hamming (7,4) is a single-error-correcting code. Multiple simultaneous errors are outside the guarantee of this implementation.

## 7. Assembly syntax used

The editor supports the instruction subset documented in the project source, including `MOV`, `ADD`, `SUBB`, `ANL`, `ORL`, `XRL`, `INC`, `DEC`, `MUL`, `SJMP`, `JZ`, `JNZ`, `JB`, `JNB`, `SETB`, `CLR`, `CPL`, `PUSH`, `POP`, `NOP`, and `HALT`. The Hamming program uses assembly instructions for parity and syndrome calculations.

## 8. Verification evidence

The sample was assembled and executed against the simulator CPU implementation. The observed values were:

| Item | Observed result |
|---|---:|
| Input nibble | `0x0B` |
| Codeword | `0x55` |
| Syndrome | `0` |
| Correction marker | `0` |
| Completion signature | `0x48` |
| CPU halted | Yes |

The per-instruction report helper was executed against the assembled demo and produced 176 instruction records plus one summary record. The project compiles successfully with `bash build.sh`. The full legacy IPC test script was also attempted, but its run was not fully green in this environment: one PAUSE timing test failed, the script's second phase could not run because `bc` is unavailable, and the UI process returned non-zero during shutdown. Those failures are unrelated to the new Hamming report helper and should be rechecked on the team's WSL machine. These checks do not demonstrate naturally occurring corruption of program memory.
