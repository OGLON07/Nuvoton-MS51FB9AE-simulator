-------------------------------------------------------Week-3 Queue test results-----------------------------------------------------

| Test | Operation / Description | Expected Result | Actual Result | Status |
| ---- | ----------------------- | --------------- | ------------- | ------ |
| TC01 | Single Enqueue & Dequeue (0x42) | Dequeued value = 0x42, count = 0, isEmpty = true | val = 0x42, count = 0 | PASS |
| TC02 | Multiple Enqueues (0x11, 0x22, 0x33) | Strict FIFO dequeue order: 0x11, 0x22, 0x33 | order = [11, 22, 33] | PASS |
| TC03 | Empty Condition Check | isEmpty = true initially, false after enqueue, true after dequeue | init = true, mid = false, final = true | PASS |
| TC04 | Full Condition Check (8 items) | isFull = true when count = 8 (capacity) | isFull = true, count = 8 | PASS |
| TC05 | Circular Buffer Wrap-Around | Enqueue across boundary (slots 6, 7, 0) preserves FIFO order | order = [A1, B2, C3] | PASS |
| TC06 | Assembly Program End-to-End Execution | Interleaved enqueues/dequeues: results at 0x50–0x53 = [AA, BB, CC, DD], final count = 0 | [AA, BB, CC, DD], count = 0 | PASS |
