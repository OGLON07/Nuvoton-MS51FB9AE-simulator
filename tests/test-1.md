-------------------------------------------------------Week-2 Instructions test results-----------------------------------------------------

| Test | Instruction   | Expected Result                                             | Actual Result             | Status |
| ---- | ------------- | ----------------------------------------------------------- | ------------------------- | ------ |
| TC01 | `MOV A, #0A`  | A = 0A                                                      | A = 0A                    | PASS   |
| TC02 | `MOV R0, #05` | R0 = 05                                                     | R0 = 05                   | PASS   |
| TC03 | `ADD A, #05`  | A contains sum of A + 05                                    | A = 0F                    | PASS   |
| TC04 | `SUBB A, #01` | A contains A − 01 − CY                                      | A = 0E                    | PASS   |
| TC05 | `MUL AB`      | A and B contain the 16-bit multiplication result            | A = 00, B = 00            | PASS   |
| TC06 | `ANL A, #0F`  | A = A AND 0F                                                | A = 0E                    | PASS   |
| TC07 | `INC A`       | A increases by 1                                            | A = 0F                    | PASS   |
| TC08 | `DEC A`       | A decreases by 1                                            | A = 0E                    | PASS   |
| TC09 | `SJMP +1`     | PC jumps forward by 1 byte relative to the next instruction | PC jumps from 0F to 10    | PASS   |
| TC10 | `HALT`        | Processor execution stops                                   | Simulator status = HALTED | PASS   |
