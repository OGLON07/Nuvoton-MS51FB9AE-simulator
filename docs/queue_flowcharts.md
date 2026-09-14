# FIFO Queue — Flowcharts

## Queue Design Overview

The FIFO queue is implemented as a **fixed circular buffer** in Data Memory (RAM), using three metadata pointers stored at dedicated RAM addresses:

| Metadata | RAM Address | Description |
|----------|-------------|-------------|
| HEAD     | `0x30`      | Index of the next element to dequeue |
| TAIL     | `0x31`      | Index of the next free slot for enqueue |
| COUNT    | `0x32`      | Number of elements currently in the queue |

**Buffer:** RAM `0x40`–`0x47` (8 entries, circular)

All operations are built using existing `MOV`, `INC`, and `DEC` instructions — **no new opcodes required**.

---

## 1. Enqueue Operation

```mermaid
flowchart TD
    A([Start Enqueue])
    A --> B{Is queue full?\nCOUNT >= CAPACITY}
    B -- Yes --> C[/"Queue OVERFLOW\nCannot enqueue"/]
    C --> H([End])
    B -- No --> D["Load value into A\nMOV A, #value"]
    D --> E["Store A at buffer position TAIL\nMOV (0x40 + TAIL), A"]
    E --> F["Increment TAIL\nTAIL = (TAIL + 1) mod CAPACITY\nMOV 31h, A"]
    F --> G["Increment COUNT\nCOUNT = COUNT + 1\nMOV 32h, A"]
    G --> H
```

---

## 2. Dequeue Operation

```mermaid
flowchart TD
    A([Start Dequeue])
    A --> B{Is queue empty?\nCOUNT == 0}
    B -- Yes --> C[/"Queue UNDERFLOW\nCannot dequeue"/]
    C --> H([End])
    B -- No --> D["Read value from buffer position HEAD\nMOV A, (0x40 + HEAD)"]
    D --> E["Save dequeued value\nMOV result_addr, A"]
    E --> F["Increment HEAD\nHEAD = (HEAD + 1) mod CAPACITY\nMOV 30h, A"]
    F --> G["Decrement COUNT\nCOUNT = COUNT - 1\nMOV 32h, A"]
    G --> H
```

---

## 3. Empty Condition Check

```mermaid
flowchart TD
    A([Check Empty])
    A --> B["Read COUNT from RAM\nMOV A, 32h"]
    B --> C{COUNT == 0 ?}
    C -- Yes --> D["Queue is EMPTY\nNo elements to dequeue"]
    C -- No --> E["Queue is NOT empty\nCOUNT elements present"]
    D --> F([End])
    E --> F
```

---

## 4. Full Condition Check

```mermaid
flowchart TD
    A([Check Full])
    A --> B["Read COUNT from RAM\nMOV A, 32h"]
    B --> C{COUNT >= CAPACITY ?}
    C -- Yes --> D["Queue is FULL\nCannot enqueue more"]
    C -- No --> E["Queue is NOT full\nCAPACITY - COUNT slots free"]
    D --> F([End])
    E --> F
```

---

## 5. Status Update

```mermaid
flowchart TD
    A([Update Status])
    A --> B["Read COUNT from RAM\nMOV A, 32h"]
    B --> C{COUNT == 0 ?}
    C -- Yes --> D["Status: EMPTY"]
    C -- No --> E{COUNT >= CAPACITY ?}
    E -- Yes --> F["Status: FULL"]
    E -- No --> G["Status: ACTIVE\nCOUNT / CAPACITY items"]
    D --> H["Display in QUEUE panel:\nHEAD, TAIL, COUNT,\nstatus, contents in FIFO order"]
    F --> H
    G --> H
    H --> I([End])
```

---

## Circular Buffer Diagram

The buffer wraps around: when TAIL or HEAD reaches index 7, the next position is index 0.

```
Buffer indices:   [0] [1] [2] [3] [4] [5] [6] [7]
RAM addresses:    40  41  42  43  44  45  46  47

Example: HEAD=6, TAIL=1, COUNT=3
                   ↑T                      ↑H
Contents:         [CC] [__] [__] [__] [__] [__] [AA] [BB]

FIFO order: AA → BB → CC
```
