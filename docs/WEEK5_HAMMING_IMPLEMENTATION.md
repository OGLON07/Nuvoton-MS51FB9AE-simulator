# Week 5 — Assembly Editor and Hamming (7,4) Demonstration

## Changes included

- The PROGRAM panel is now an editable assembly source editor.
- `LOAD PROGRAM` assembles the text in the editor and sends the resulting bytes to the Core process.
- `LOAD EXAMPLE`, `STACK DEMO`, `QUEUE DEMO`, and `HAMMING DEMO` place their source code into the same editor before assembling it. The demo source can be edited before loading.
- Added assembly instructions needed by the Hamming routine: `XRL A,#data`, `ORL A,#data`, `JZ`, `JNZ`, `JB`, `JNB`, `SETB`, `CLR`, and `CPL`.
- Added a two-pass assembler with labels and relative branch resolution.

## Hamming demonstration

The demo implements ordinary Hamming (7,4) with even parity. It uses input nibble `0x0B` as an example value, calculates P1/P2/P4, constructs a seven-bit codeword, calculates the three-bit syndrome, and contains a single-bit correction path for syndrome values 1 through 7.

Expected demonstration values:

| Item | Expected value |
|---|---:|
| Input nibble | `0x0B` |
| P1 | `1` |
| P2 | `0` |
| P4 | `0` |
| Hamming codeword | `0x55` |
| Syndrome with no bit error | `0` |

## Hamming demo RAM layout

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
| `0x2C` | Correction marker (set to 1 if correction path runs) |

The simulator implements bit-addressable internal RAM bit addresses `0x00`–`0x7F`, mapped to data RAM bytes `0x20`–`0x2F`.

## Assembly syntax supported by the editor

- `MOV A,#data`, `MOV Rn,#data`, `MOV B,#data`
- `MOV A,direct`, `MOV direct,A`
- `ADD A,#data`, `SUBB A,#data`, `ANL A,#data`, `ORL A,#data`, `XRL A,#data`
- `INC A`, `DEC A`, `MUL AB`
- `SJMP label`, `JZ label`, `JNZ label`
- `JB bit,label`, `JNB bit,label`
- `SETB bit`, `CLR bit`, `CPL bit`
- `PUSH A`, `POP A`, `NOP`, `HALT`

Numbers may be decimal, `0x`-prefixed hexadecimal, or suffixed with `H`. Semicolon and `//` comments are supported. Labels use `NAME:` syntax. Relative branches must fit the signed 8-bit range.

## Important technical limitation

Hamming code detects/corrects bit errors in encoded data for which parity information exists. It does not identify ordinary source-code logic errors such as a wrong branch condition or incorrect arithmetic implementation.

The current Hamming demo validates its own encoded sample nibble. Existing program-memory instruction bytes are not yet exposed to assembly code as a readable data region, and the existing program does not carry stored Hamming parity metadata. Therefore, this implementation must not be described as having scanned and verified the entire existing simulator program or as having found naturally occurring simulator defects. Further work is needed if the professor expects Hamming protection over the actual loaded instruction stream.
