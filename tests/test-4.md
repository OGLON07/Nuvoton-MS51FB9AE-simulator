-------------------------------------------------------Week-3 Stack test results-----------------------------------------------------

| Test | Operation / Description                     | Expected Result                                   | Actual Result                        | Status |
| ---- | ------------------------------------------- | ------------------------------------------------- | ------------------------------------ | ------ |
| TC01 | Single PUSH & POP (0x42)                    | POP returns 0x42, stack returns to previous state | val = 0x42, SP restored              | PASS   |
| TC02 | Multiple PUSH Operations (0x11, 0x22, 0x33) | All three values stored successfully in stack     | stack = [11, 22, 33]                 | PASS   |
| TC03 | Multiple POP Operations                     | Values returned in LIFO order: 0x33, 0x22, 0x11   | order = [33, 22, 11]                 | PASS   |
| TC04 | Stack Pointer Behavior                      | SP increments on PUSH and decrements on POP       | SP updated correctly                 | PASS   |
| TC05 | SP Overflow / Wrap-Around                   | SP wraps correctly after reaching 0xFF            | 8-bit SP wrap-around works correctly | PASS   |
