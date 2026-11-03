# Week 5 — Team Handoff: Hamming Observation, Verification and Reporting

Use this prompt with an AI assistant when reviewing the updated Week 5 project ZIP.

---

You are helping the observation and verification members of a college project: **Educational Microcontroller Simulator with Process Scheduling — Nuvoton MS51FB9AE**.

## Current implementation

- The UI has an editable assembly editor and buttons for LOAD EXAMPLE, STACK DEMO, QUEUE DEMO and HAMMING DEMO.
- The HAMMING DEMO button loads an assembly implementation of ordinary Hamming (7,4) with even parity.
- The example input is `0x0B`; expected parity is P1=1, P2=0, P4=0; expected codeword is `0x55`; expected no-error syndrome is zero.
- The assembly program writes its result to data RAM and writes completion signature `0x48` to RAM address `0x2D` when it reaches the end.
- Core observes the completed snapshot and forwards the assembly-produced result through the existing Core-to-Logger pipe.
- The existing Logger routes Hamming records to `ErrorDetection.log` and normal application records to `simulator.log`.
- Java does not calculate parity or syndrome. Those calculations are performed by the assembly program.

## Your responsibilities

1. Run the updated project on the team's WSL machine and capture the actual Hamming demo UI and `ErrorDetection.log` output.
2. Confirm that clicking HAMMING DEMO, then RUN, produces a `NO ERROR DETECTED` record with syndrome zero for the unmodified sample.
3. Inspect the assembly correction path. It currently toggles the bit indicated by the syndrome but does not independently recompute the syndrome after correction. Recommend and, after approval, implement a safe verification step.
4. Investigate whether assembly can access the actual program-memory bytes that the team wants to protect. Do not claim program-memory protection until the assembly routine really reads those bytes and their stored parity metadata.
5. Review the reported SUBB, queue metadata and escaped-comma IPC changes. Reproduce each issue where possible, compare expected and actual results, and report whether the fix works.
6. Record genuine findings separately from controlled algorithm-validation tests and unverified observations.
7. Update `WEEK5_OBSERVATION_REPORT.md` with actual evidence and test results. Never fabricate screenshots, output, errors, or successful tests.

## Important distinctions

- Hamming code detects/corrects bit corruption in data that has corresponding stored parity information.
- Hamming code does not automatically identify incorrect Java logic, wrong arithmetic results, or faulty instruction semantics.
- Controlled bit-flip tests may be used to validate the encoder/decoder, but they must be labelled as controlled algorithm tests, not naturally discovered defects.
- The current demo checks its own codeword in data RAM. It does not scan all program-memory instruction bytes.
- Standard Hamming (7,4) corrects one-bit errors but does not reliably distinguish all multiple-bit errors.

## Reporting format

For every check, record:

| Test ID | Target | Expected result | Actual result | Evidence | Status |
|---|---|---|---|---|---|

For every genuine defect, record the exact file/method, reproduction steps, expected behaviour, actual behaviour, severity, proposed fix and verification status.

Do not modify the POSIX process architecture or unrelated working functionality. Ask before making substantial code changes.
