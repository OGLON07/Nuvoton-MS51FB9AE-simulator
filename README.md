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
| Team Member | Primary Role | Secondary Role | Week 1 Responsibilities |
| :--- | :--- | :--- | :--- |
| Gilon Prince Serrao | CPU Core & Instruction Decoder | Architecture & Repository Lead | GitHub repo setup, branch management, base structure |
| asad moidhin | Memory & Stack Management | System Documentation |  memory map layout, documentation | CPU support |
| Melbin K Vinod  | Data Structures & Process Control | Unit Testing & QA | PCB design, Ready Queue and Circular Queue logic |
| Preemal Simona Pinto | OS Scheduler & Context Switching | User Interface & Analytics | README and documentation | Scheduling algorithms (FCFS, RR, Priority) & UI |

## 7. Selected Programming Language
* **Language:** Java
* **Reason for Selection:** We all learned Java in our previous classes, so we are comfortable with it. Object-Oriented Programming makes it really easy to treat the CPU, Memory, and Registers as separate objects. Also, Java has built-in queues and lists, which will save us a lot of time when building the OS scheduling part.

## 8. Initial System Architecture
![System Architecture](images/system%20architecture.jpeg)

## 9. Initial Development Plan

* **Week 1: Project Setup & Basic Design**
  * Set up the GitHub repository, team branches, and workflow rules.
  * Draw the main system architecture diagram and write the README file.

* **Week 2: Hardware Layer Implementation**
  * Code the main CPU registers (A, B, PSW, PC, SP) and the fetch-decode-execute loop.
  * Build the memory system (16 KB Flash and 1 KB RAM for SRAM and Stack).
  * Add basic simulation logic for GPIO ports, timers, and interrupts.

* **Week 3: OS & Process Management**
  * Design Process Control Blocks (PCBs) to save process states and register snapshots.
  * Set up Ready Queues and Circular Queues to manage running programs.
  * Code the FCFS, Round Robin, and Priority scheduling algorithms along with context switching.

* **Week 4: User Interface & Controls**
  * Build control buttons for Run, Step, Reset, and Load Program.
  * Create screen views to display real-time register values, RAM usage, and running tasks.
  * Connect the UI screens to the backend OS and hardware code.

* **Week 5: Testing & Final Details**
  * Test real assembly code programs on the simulator to check for errors.
  * Measure performance stats like waiting time, turnaround time, and CPU usage.
  * Fix bugs, clean up code, and prepare the project presentation.
