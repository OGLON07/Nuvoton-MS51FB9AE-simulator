# Nuvoton MS51FB9AE Microcontroller & OS Simulator

## 1. Problem Objective
We want to build a software simulator for the Nuvoton MS51FB9AE microcontroller. It will show how the CPU runs basic assembly instructions and how a simple operating system manages multiple programs at the same time using different scheduling algorithms.

## 2. Problem Statement
Design and implement a simulator for the Nuvoton MS51FB9AE processor. The software must emulate fundamental hardware components (registers, memory, stack, and peripherals) while acting as a lightweight OS that manages multiple processes using Process Control Blocks (PCBs), ready queues, context switching, and FCFS, Round Robin, and Priority scheduling algorithms.

## 3. Project Scope
* **Hardware:** Simulating the 8051 core registers (like ACC, B, PC, SP), 256 bytes of RAM, and 16 KB of Flash memory.
* **Instructions:** Running basic assembly commands (math, logic, moving data).
* **Peripherals:** Adding simple features like a Timer, Interrupts, and GPIO pins.
* **OS Management:** Making PCBs to track if a process is Ready, Running, or Blocked.
* **Scheduling:** Writing the logic for FCFS, Round Robin, and Priority scheduling, and making sure context switching works.
* **UI & Stats:** A simple interface to load code, run it step-by-step, and see stats like waiting time and CPU usage.

## 4. Microcontroller Being Simulated
* **Device:** Nuvoton MS51FB9AE
* **Architecture:** 1T 8051-based 8-bit microcontroller
* **Clock Frequency:** Up to 24 MHz
* **Memory:** 16 KB APROM Flash memory, 256 Bytes Internal Direct/Indirect RAM
* **Key Hardware Registers:** Accumulator (`ACC`), `B` Register, Data Pointer (`DPTR`), Program Counter (`PC`), Stack Pointer (`SP`), Program Status Word (`PSW`)

## 5. Team Members
* **Student 1 (Team Leader):** Gilon Prince Serrao
* **Student 2:** Asad Moidhin
* **Student 3:** Melbin K Vinod
* **Student 4:** Preemal Simona Pinto

## 6. Team Responsibilities
| Team Member | Primary Role | Secondary Role | Week 2 Responsibilities |
| :--- | :--- | :--- | :--- |
| Gilon Prince Serrao | CPU Core & Instruction Decoder | Architecture & Repository Lead | Integrating the CPU core, Implementing registers and CPU state, Implementing FETCH → DECODE → EXECUTE flow |
| asad moidhin | Memory & Stack Management | System Documentation | Implementing memory functionality, instruction representation and related components |
| Melbin K Vinod | Data Structures & Process Control | Unit Testing & QA | team discussions, documenting agenda and decisions, and creating the meeting report. |
| Preemal Simona Pinto | OS Scheduler & Context Switching | User Interface & Analytics | Developing the simulator UI, Implementing CPU state and execution trace display, UI controls and execution visualization. |

## 7. Multi-Process Architecture (Week 4 IPC)

The simulator is built entirely in **100% Pure Java** and runs as **three separate OS processes** connected through standard I/O pipes:

```text
               ┌──────────────────────────────────────────────┐
               │              1. UI PROCESS                   │
               │   Swing User Interface & IPCCoreCommunicator │
               └──────────────┬────────────────────────▲──────┘
          stdin (Commands)    │                        │ stdout (Snapshots)
          pipe_ui_to_core     │                        │ pipe_core_to_ui
                              ▼                        │
               ┌───────────────────────────────────────┴──────┐
               │             2. CORE PROCESS                  │
               │   8051 CPU, Memory, Stack, Queue Engine      │
               └──────────────────────────────┬───────────────┘
                                 stderr (Logs)│
                           pipe_core_to_logger│
                                              ▼
               ┌──────────────────────────────────────────────┐
               │            3. LOGGER PROCESS                 │
               │   Independent Logger & File Writer           │
               └──────────────────────────────────────────────┘
```

### IPC Mechanism
* **Pipes:** Process stream pipes (`stdin`, `stdout`, `stderr`).
* **Process Creation:** `ProcessBuilder` spawning independent JVM processes with distinct OS Process IDs (PIDs).
* **Cross-Platform:** Runs natively on Windows, Linux, WSL, and macOS without requiring any C compiler or native code.

---

## 8. Build & Run Instructions (Java Only)

### Prerequisites
* Java JDK (version 17 or higher)

### 1. Compile All Sources
```bash
javac -d out -sourcepath src src/Main/Main.java src/Main/Launcher.java src/Main/CPU/*.java src/Main/instruction/*.java src/Main/Memory/*.java src/Main/Queue/*.java src/Main/UI/*.java src/Main/core/*.java src/Main/core/command/*.java src/Main/core/state/*.java src/Main/Logger/*.java src/Main/IPC/*.java
```

### 2. Run Test Suites
```bash
# Compile tests
javac -d out -cp out -sourcepath src tests/*.java

# Run unit & integration tests
java -ea -cp out DataMemoryTest
java -ea -cp out StackTest
java -ea -cp out QueueTest
java -ea -cp out Main.core.test.CoreProcessTest
java -ea -cp out IPCTest
```

### 3. Launch Full 3-Process Simulator
```bash
java -cp out Main.Main
```

---

## 9. Test Results Summary
* **Data Memory:** 6 / 6 Passed
* **Stack Operations:** 3 / 3 Passed
* **FIFO Queue:** 6 / 6 Passed
* **Core Engine:** 11 / 11 Passed
* **IPC Protocol & Multi-Process:** 6 / 6 Passed
