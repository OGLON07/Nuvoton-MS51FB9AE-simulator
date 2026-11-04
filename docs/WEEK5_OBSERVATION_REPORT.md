# Week 5 Observation and Error Analysis Report

## 1. Scope of Review

This Week 5 review covers:

- The editable assembly editor and two-pass assembler.
- The Hamming (7,4) assembly demonstration.
- The existing Core-to-Logger POSIX pipe.
- Dedicated Hamming result logging in `ErrorDetection.log`.
- Selected CPU, queue and IPC implementation observations.

The Hamming parity and syndrome calculations are implemented in assembly. Core coordinates the verification runs, reads the assembly-produced syndrome values, identifies instruction boundaries in the assembled byte stream, and sends the results to the existing Logger process. Java does not calculate Hamming parity or syndrome.

## 2. Hamming (7,4) Algorithm Verification

The demonstration uses input nibble `0x0B` and even parity.

| Data bit | Value |
|---|---:|
| D1 | 1 |
| D2 | 1 |
| D3 | 0 |
| D4 | 1 |

Parity calculations:

- `P1 = D1 XOR D2 XOR D4 = 1`
- `P2 = D1 XOR D3 XOR D4 = 0`
- `P4 = D2 XOR D3 XOR D4 = 0`

The codeword layout `P1, P2, D1, P4, D2, D3, D4` produces `1010101` (`0x55`). The checked sample produces syndrome `000`, meaning no error was detected in this codeword.

The assembly routine includes a correction branch mapping syndrome values 1 through 7 to codeword positions 1 through 7. The current routine marks when that branch is taken, but does not recompute the full syndrome after correction; therefore correction is not yet independently verified.

## 3. ErrorDetection.log Integration

The existing Logger process is reused. The Hamming assembly program writes completion signature `0x48` to RAM address `0x2D` after calculating its result. When the program halts, Core reads the result fields from the CPU snapshot and sends a dedicated `HAMMING` log message over the existing Core-to-Logger pipe.

The Logger routes these messages to `ErrorDetection.log`. Other application messages continue to be written to `simulator.log`.

The Hamming log records:

- The original sample result (`0x0B`, codeword `0x55`, syndrome `000`).
- Each decoded instruction in the assembled Hamming demo, its program address, and its encoded byte values.
- A PASS / CHECK FAILED result for each instruction, based on assembly-executed Hamming nibble checks for every byte.
- A final instruction count and pass/fail summary.

The verifier runs the existing Hamming assembly routine for each possible 4-bit nibble value (0 through 15), then applies those assembly results to the high and low nibbles of every byte in the demo's assembled program. This avoids adding new CPU instructions or registers. No artificial corruption is introduced.

## 4. Executed Verification Checks

| Check | Expected result | Observed result | Status |
|---|---|---|---|
| Compile project using `bash build.sh` | Java and native launcher build successfully | Build completed with `Build OK` | PASS |
| Assemble and execute Hamming demo | Program reaches HALT | Program reached HALT; completion marker `0x48` was written | PASS |
| Instruction-by-instruction report | Every assembled instruction is listed with its address and bytes | 176 instructions listed across 372 program bytes | PASS |
| Assembly Hamming nibble validation | All 16 possible nibble values produce a completed zero-syndrome codeword | All 16 assembly checks passed | PASS |
| Per-instruction summary | Each instruction's bytes receive Hamming nibble validation | 176 passed, 0 failed | PASS |
| Hamming sample encoding | Input `0x0B` produces codeword `0x55` | Codeword `0x55` | PASS |
| Hamming syndrome | Unmodified sample produces syndrome 0 | Syndrome `0` | PASS |
| Completion marker | Demo writes marker `0x48` to RAM `0x2D` | Marker `0x48` | PASS |
| No-error correction marker | No correction branch is taken | Marker `0` | PASS |
| Logger routing | HAMMING records go to `ErrorDetection.log` | Existing Logger routes `HAMMING` messages to the dedicated file; full UI-to-Logger Hamming run still needs team-machine confirmation | PENDING |

The project compiled successfully. The report helper was executed against the assembled demo and produced 176 instruction records plus one summary record. The legacy IPC suite was attempted but did not complete cleanly in this environment (PAUSE timing, missing `bc`, and a UI shutdown exit status); rerun it on the team's WSL machine. These checks validate the Hamming assembly routine across all possible nibble inputs and produce a per-instruction report for the loaded Hamming demo. They do not demonstrate naturally occurring bit corruption or a correction event.

## 5. Simulator Code Review Findings from the Team Review

The following changes were reported in the teammate's review. They are retained in the supplied project, but they have not been independently runtime-tested as part of this packaging pass.

### 5.1 SUBB carry-in handling

- **File / method:** `src/Main/instruction/InstructionExecutor.java`, `SUBB_A_IMM` case.
- **Expected:** 8051-style `SUBB` computes `A = A - data - CY`.
- **Reported issue:** Previous implementation ignored incoming carry/borrow.
- **Reported fix:** Include CY in the subtrahend and calculate borrow-related flags from the complete subtraction.
- **Verification status:** Code change present; runtime test still required.

### 5.2 Queue count validation

- **File / method:** `src/Main/Queue/Queue.java`, `getContentsInOrder`.
- **Expected:** Queue contents are materialized only for counts within the capacity range 0–8.
- **Reported issue:** Invalid metadata could cause invalid array sizing or iteration outside the queue contract.
- **Reported fix:** Reject count values outside the supported range with an explicit state exception.
- **Verification status:** Code change present; runtime test still required.

### 5.3 IPC escaped-comma parsing

- **File / method:** `src/Main/IPC/IPCProtocol.java`, `parseSnapshotData`.
- **Expected:** Escaped commas remain part of the serialized field value.
- **Reported issue:** Unconditional comma splitting could corrupt escaped values.
- **Reported fix:** Split only on commas not preceded by an escape backslash.
- **Verification status:** Code change present; runtime test still required.

These are general software correctness findings from code review, not errors detected by the Hamming algorithm.

## 6. Limitations and Remaining Work

1. The report now covers every decoded instruction byte in the loaded Hamming demo program. It does not scan arbitrary programs loaded by the user.
2. Program memory and data memory remain separate. Core supplies the program-byte values to the verifier; no new assembly instruction, `DPTR`, or `MOVC` support was added.
3. The assembly routine generates a fresh Hamming codeword from each nibble and checks its syndrome. This is an encoding/self-consistency validation, not a comparison against parity saved before possible corruption. Therefore it cannot prove that a byte was not already corrupted before the check.
4. The correction path should be extended to recompute the syndrome after correction before reporting a correction as verified.
5. Ordinary instruction logic errors are not Hamming-code bit-corruption errors and must not be reported as Hamming findings.
6. A full GUI/WSL end-to-end run should be performed on the team's demo machine to confirm that the actual `ErrorDetection.log` appears in the project working directory.
7. The three general code-review changes listed above need the team's runtime verification and evidence.


## 7. Conclusion

The Week 5 project now has an assembly-based Hamming (7,4) demonstration and a dedicated logging route through the existing Logger process. The report lists all 176 decoded instructions across the 372-byte Hamming demo program, with 176 passing and zero failing in the assembly-based nibble self-check.

This is a per-instruction Hamming encoding/self-consistency report for the Hamming demo program. It is not a stored-parity comparison and must not be described as proof that pre-existing program-memory corruption was detected or ruled out. A later stored-parity verification stage would be needed for that stronger claim.
