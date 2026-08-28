# Initial System Architecture Design

Our team structured the MS51FB9AE microcontroller simulator into a 3-tier modular architecture. This design cleanly separates the visual user interface, the operating system management, and the underlying virtual hardware simulation logic.

---

## 1. User Interface & Control Layer
This layer handles all direct user interactions and visual displays:
* **Interactive Controls:** Provides options for program execution, including **Run**, **Single-Step**, **Reset**, and **Load**.
* **UI View & State Visualization:** Displays live updates of register values, memory contents, process queues, and current execution states.

## 2. OS & Process Management Subsystem
This layer manages process lifecycles and handles task scheduling before passing execution down to the hardware core:
* **Process Manager:** Coordinates process creation, state updates, and termination.
* **Process Control Block (PCB) Data Structures:** Stores process metadata, including Process ID (PID), saved registers, program counter state, and process status.
* **Ready Queue / Circular Queue:** Holds and organizes processes waiting for CPU execution time.
* **CPU Scheduler:** Executes task ordering using **FCFS**, **Round Robin**, and **Priority** scheduling algorithms.
* **Context Switcher:** Saves active register states back into the PCB and restores the next scheduled process state.

## 3. Virtual Hardware Simulation Layer
This layer directly models the physical components of the Nuvoton MS51FB9AE chip as software classes:
* **CPU Core Class:**
  * **Registers:** Holds data for `A`, `B`, `PSW`, `PC`, `SP`, and general-purpose registers `R0`–`R7`.
  * **Fetch-Decode-Execute Engine:** Runs the primary instruction loop, parsing binary opcodes and executing hardware updates.
* **Memory System Class:**
  * **16 KB ROM Array (Flash):** Stores non-volatile program code and instructions.
  * **1 KB RAM Array (SRAM & Stack):** Manages volatile data storage and the upward-growing system stack.
* **Peripherals Simulation Class:**
  * **GPIO Port State Registers:** Simulates digital I/O states across ports `P0`, `P1`, `P2`, and `P3`.
  * **Timer Counters & Delays:** Models hardware timing counts and delay operations.
  * **Interrupt Controller Logic:** Manages interrupt requests and vector branching routines.
