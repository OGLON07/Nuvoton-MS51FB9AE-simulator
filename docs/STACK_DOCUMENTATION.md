# Week-03 Stack Implementation & Test Documentation

## 1. Overview & Objective

The objective of Student 2's task for Week-03 is to implement hardware stack management for the Nuvoton MS51FB9AE 8051 simulator using the dedicated Stack Pointer (`SP`) register and Data Memory (RAM).

The stack is accessed via two new instructions:
* `PUSH A` (Opcode: `0xC0`) — Pushes the contents of the Accumulator onto the stack.
* `POP A` (Opcode: `0xD0`) — Pops the top value of the stack into the Accumulator.

---

## 2. Hardware Design & Architectural Behavior

In the standard 1T 8051 microcontroller architecture:

1. **Stack Pointer (`SP`) Initial Value**:
   - On hardware `RESET`, the Stack Pointer (`SP`) register is initialized to `0x07`.
   - RAM addresses `0x00–0x07` are reserved for Register Bank 0 (`R0–R7`). Initializing `SP` to `0x07` ensures that the stack grows upward starting at RAM address `0x08`, preventing stack pushes from overwriting the working registers.

2. **`PUSH A` (Pre-increment Stack)**:
   - Increment `SP` by 1: `SP = (SP + 1) & 0xFF`
   - Store the value of Accumulator (`A`) into Data Memory at the new `SP` address: `RAM[SP] = ACC`
   - Example: If `SP == 0x07` and `A == 0x42`, after `PUSH A`: `SP == 0x08` and `RAM[0x08] == 0x42`.

3. **`POP A` (Post-decrement Stack)**:
   - Read the byte from Data Memory at current `SP` into the Accumulator: `ACC = RAM[SP]`
   - Decrement `SP` by 1: `SP = (SP - 1) & 0xFF`
   - Example: If `SP == 0x08` and `RAM[0x08] == 0x42`, after `POP A`: `A == 0x42` and `SP == 0x07`.

4. **8-bit Wrap-around (SP Overflow)**:
   - The Stack Pointer is an 8-bit register (`0x00`–`0xFF`).
   - If `SP == 0xFF` and a `PUSH` is executed, `SP` wraps around to `0x00` (`(0xFF + 1) & 0xFF = 0x00`), and the byte is stored at `RAM[0x00]`.

---

## 3. Instruction Encoding

| Mnemonic | Opcode (Hex) | Length (Bytes) | Cycles | Operation |
|---|---|---|---|---|
| `PUSH A` | `0xC0` | 1 | 2 | `SP ← SP + 1`, `RAM[SP] ← ACC` |
| `POP A`  | `0xD0` | 1 | 2 | `ACC ← RAM[SP]`, `SP ← SP - 1` |

---

## 4. UI Integration

A dedicated **STACK VIEW** panel is integrated into the simulator interface (`SimulatorUI.java`):
* Embedded inside the tabbed pane under the **"STACK"** tab (alongside Data Memory and Queue).
* Shows the current `SP` value in hexadecimal (`0x07`, `0x08`, etc.).
* Visualizes the active stack memory area (`RAM[0x17]` down to `RAM[0x07]`) with a visual marker (`<-- SP`) indicating the top of the stack.
* Updates dynamically after every single instruction execution (`STEP` and `RUN`).
* Includes a **"STACK DEMO"** button that loads a self-contained assembly program demonstrating push and pop operations.

---

## 5. Test Suite & Verification Results

The automated test suite in [`tests/StackTest.java`](tests/StackTest.java) verifies all required edge cases:

| Test Case | Method | Description | Expected Result | Actual Result | Status |
|---|---|---|---|---|---|
| **TC01** | `testSinglePushPop` | Push `0x42`, corrupt ACC, pop back | `SP: 0x07 → 0x08 → 0x07`, `ACC = 0x42` | `SP: 0x08 → 0x07`, `ACC = 0x42` | **PASS** |
| **TC02** | `testMultiplePushPopLIFO` | Push `0x10`, increment to `0x11`, push `0x11`, pop twice | Pops `0x11` then `0x10` (strict LIFO order) | Retreives `0x11` then `0x10` | **PASS** |
| **TC03** | `testSPOverflow` | Set `SP = 0xFF`, execute `PUSH A` | `SP` wraps around to `0x00`, writes to `RAM[0x00]` | `SP = 0x00`, `RAM[0x00] = 0xAA` | **PASS** |

### Execution Command:
```powershell
javac -d out -cp out src/Main/**/*.java tests/StackTest.java
java -ea -cp out StackTest
```

### Console Output:
```
--- Running Stack Unit Tests ---
FETCH: PC=0 OPCODE=0xC0
DECODE: PUSH_A
       Operand = 0
FETCH: PC=1 OPCODE=0xD0
DECODE: POP_A
       Operand = 0
[PASS] testSinglePushPop
FETCH: PC=0 OPCODE=0xC0
DECODE: PUSH_A
       Operand = 0
FETCH: PC=1 OPCODE=0x4
DECODE: INC_A
       Operand = 0
FETCH: PC=2 OPCODE=0xC0
DECODE: PUSH_A
       Operand = 0
FETCH: PC=3 OPCODE=0xD0
DECODE: POP_A
       Operand = 0
FETCH: PC=4 OPCODE=0xD0
DECODE: POP_A
       Operand = 0
[PASS] testMultiplePushPopLIFO
FETCH: PC=0 OPCODE=0xC0
DECODE: PUSH_A
       Operand = 0
[PASS] testSPOverflow
ALL STACK TESTS PASSED SUCCESSFULLY!
```
