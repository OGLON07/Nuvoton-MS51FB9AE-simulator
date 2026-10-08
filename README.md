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

| Team Member | Primary Role | Secondary Role | Week 4 Responsibilities |
| :--- | :--- | :--- | :--- |
| Gilon Prince Serrao | Team Leader & Core Process Integration | Architecture & Repository Lead | Integrating the three-process architecture, coordinating UI/Core/Logger integration, implementing and integrating POSIX IPC, maintaining CPU execution and simulator core functionality, and managing repository integration |
| asad moidhin | Core Process | Memory & Stack Management | Implementing and maintaining CPU execution, program/data memory, stack, queue, and related core-process functionality |
| Melbin K Vinod | Logger Process | Testing & Documentation | Implementing the Logger Process, handling execution/event logging through IPC, testing logging functionality, and maintaining test documentation |
| Preemal Simona Pinto | UI Process | User Interface & Analytics | Developing the UI Process, implementing UI controls and execution visualization, displaying CPU/memory state received through IPC, and supporting performance/analytics visualization |

## 7. Selected Programming Language
* **Language:** Java
* **Reason for Selection:** We all learned Java in our previous classes, so we are comfortable with it. Object-Oriented Programming makes it really easy to treat the CPU, Memory, and Registers as separate objects. Also, Java has built-in queues and lists, which will save us a lot of time when building the OS scheduling part.

## 8. Initial System Architecture
![System Architecture](images/System%20Architecture%202.png)


## 9. Initial Development Plan
* Design Process Control Blocks (PCBs) to save process states and register snapshots.
* Set up Ready Queues and Circular Queues to manage running programs.
* Implement the FCFS, Round Robin, and Priority scheduling algorithms.
* Implement context switching and connect process management with the CPU simulator.
* Extend the simulator UI to display process and scheduling information.

## 10. Week 4: Three-Process Simulator (UI / Core / Logger over POSIX pipes)
See [docs/WEEK4_IPC.md](docs/WEEK4_IPC.md) for architecture, IPC justification, protocol, thread model, test results and benchmark.

Quick start on Ubuntu/WSL: `sudo apt install openjdk-21-jdk gcc make`, then `./build.sh`, `./run.sh`, `tests/run_ipc_tests.sh`.
