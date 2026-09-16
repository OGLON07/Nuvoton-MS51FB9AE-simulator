-------------------------------------------------------Week-3 Data Memory test results-----------------------------------------------------

| Test | Instruction     | Expected Result                              | Actual Result              | Status |
| ---- | --------------- | -------------------------------------------- | -------------------------- | ------ |
| TC01 | `MOV 30h, A`    | RAM[0x30] = 0x42 after write, A = 0x42 after | A = 0x42                   | PASS   |
|      | `MOV A, 30h`    | read back                                    |                            |        |
| TC02 | `MOV 00h, A`    | RAM[0x00] = 0xAB (boundary addr 0)           | A = 0xAB                   | PASS   |
|      | `MOV A, 00h`    |                                              |                            |        |
| TC03 | `MOV FFh, A`    | RAM[0xFF] = 0xCD (boundary addr 255)         | A = 0xCD                   | PASS   |
|      | `MOV A, FFh`    |                                              |                            |        |
| TC04 | `readData(256)` | IllegalArgumentException                     | IllegalArgumentException   | PASS   |
| TC05 | `writeData(-1)` | IllegalArgumentException                     | IllegalArgumentException   | PASS   |
| TC06 | CPU `reset()`   | RAM[0x50] = 0x00 after reset                 | RAM[0x50] = 0x00           | PASS   |
