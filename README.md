# Nuvoton MS51FB9AE Microcontroller & OS Simulator

## 1. Problem Objective
We want to build a software simulator for the Nuvoton MS51FB9AE microcontroller. It will show how the CPU runs basic assembly instructions and how a simple operating system manages multiple programs at the same time using different scheduling algorithms.

## 2. Problem Statement
Design and implement a simulator for the Nuvoton MS51FB9AE processor. The software must emulate fundamental hardware components (registers, memory, stack, and peripherals) while acting as a lightweight OS that manages multiple processes using Process Control Blocks (PCBs), ready queues, context switching, and FCFS, Round Robin, and Priority scheduling algorithms.

## 3. Project Scope
* **Hardware:** Simulating key CPU registers (like A, B, PC, SP), 256 bytes of RAM, and 16 KB of Flash memory.
* **Instructions:** Running basic assembly commands for simple math, logic, and moving data around.
* **Peripherals:** Adding basic components like input/output pins, timers, and simple interrupts.
* **OS Management:** Tracking program states to see if a task is Ready, Running, or Waiting.
* **Scheduling:** Writing logic for FCFS, Round Robin, and Priority scheduling to switch between tasks smoothly.
* **UI & Stats:** Building a clean screen to load code, step through execution line-by-line, and check CPU stats.

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

| Team Member | Primary Role | Secondary Role | Week 1 Tasks |
| :--- | :--- | :--- | :--- |
| **Gilon Prince Serrao** | CPU Core & Instruction Decoder | Architecture & Repository Lead | Create public GitHub repository, configure `.gitignore`, build project folder structure, set up `week-01` branch, and assign tasks. |
| **Preemal Simona Pinto** | Memory & Stack Management | System Documentation | Research simulator requirements, create, and finalize `README.md` file covering microcontroller specs and initial development plan. |
| **Asad Moidhin** | Data Structures & Process Control | Status Tracking & Reporting | Document decisions made by the team and complete the weekly-status report. |
| **Melbin K Vinod** | OS Scheduler & Context Switching | Unit Testing & QA | Record team discussions, document agenda and decisions, and create the meeting report. |

## 7. Selected Programming Language
* **Language:** Java
* **Reason for Selection:** We all learned Java in our previous classes, so we are comfortable with it. Object-Oriented Programming makes it really easy to treat the CPU, Memory, and Registers as separate objects. Also, Java has built-in queues and lists, which will save us a lot of time when building the OS scheduling part.

## 8. Initial System Architecture
![System Architecture](images/System%20Architecture%202.png)

## 9. Initial Development Plan
* **1: Setup & Basic Design**
  * Set up our GitHub repo, team branches, and basic workflow rules.
  * Draw the main system architecture diagram and write the README file.

* **2: Hardware Simulation**
  * Code the basic CPU registers (A, B, PC, SP) and set up how instructions run.
  * Build the memory layout for RAM and Flash memory space.
  * Add simple logic for pins, timers, and basic interrupts.

* **3: OS & Scheduling**
  * Build Process Control Blocks and queues to track running programs.
  * Code our scheduling methods (FCFS, Round Robin, Priority) and task switching.

* **4: Screen & Controls**
  * Build simple interface buttons like Run, Step, Reset, and Load Program.
  * Create panels to display live register values, memory use, and active tasks.
  * Connect the screen interface directly to our backend logic.

* **5: Testing & Wrap-Up**
  * Run test assembly programs on our simulator to catch any hidden bugs.
  * Calculate performance stats like waiting time, turnaround time, and CPU usage.
  * Clean up the code and prepare our final slides for presentation.
