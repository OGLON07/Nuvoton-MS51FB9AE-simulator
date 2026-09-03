# Initial System Architecture Design

Our team structured the MS51FB9AE microcontroller simulator into a 3-tier modular architecture. This architecture focuses on developing the first working CPU prototype capable of loading and executing a small program through the logical FETCH → DECODE → EXECUTE process and displaying the resulting processor state to the user.

---

## 1. CPU
The CPU is the core execution component of the simulator. It is responsible for processing instructions and maintaining the simulated processor state.

The CPU follows the logical execution sequence:

FETCH → DECODE → EXECUTE
* **Fetch:** Retrieves the next instruction from program memory using the Program Counter (PC).
* **Decode:** Identifies the instruction and determines its required operation and operands.
* **Execute:** Performs the operation and updates the appropriate registers, memory, and flags.

The Week 2 implementation represents these stages through separate fetch(), decode(), and execute() methods. These are logical software stages and do not represent separate hardware clock cycles.

The CPU state includes the relevant processor registers, Program Counter (PC), Stack Pointer (SP), and status/flag information required by the selected instructions.


## 2. Memory
The memory component provides the storage required by the simulator.
* **Program Memory:** Stores the program and instructions that are loaded into the simulator.
* **Data Memory:** Provides storage for data modified during instruction execution.
* **Stack:** Represents the processor stack where required by the simulator.

During instruction execution, the Program Counter identifies the next instruction in program memory, which is then passed to the CPU for processing. The Week 2 requirements specifically include program memory and instruction representation as part of the CPU foundation.

## 3. User Interface
The User Interface provides the controls and visual information required to interact with and observe the simulator.
The main execution controls are:
* **Load:** Loads a program into the simulator.
* **Reset:** Restores the simulator to its initial CPU state.
* **Step:** Executes one instruction through FETCH → DECODE → EXECUTE.
* **Run:** Continuously executes instructions until the program reaches its termination mechanism.
The interface displays the loaded program, current instruction, Program Counter, relevant CPU registers, flags/status, execution status, execution trace, and important register or memory changes.

## 4. CPU State and Execution Trace
The CPU state component maintains the current state of the simulated processor after instruction execution. Changes to registers, memory, the Program Counter, and flags can be observed through the User Interface.
The execution trace records the logical FETCH → DECODE → EXECUTE sequence so that the user can observe how each instruction is processed. When the Step control is used, the simulator performs fetch(), decode(), execute(), and then updates the displayed status.

## 5. Future Extensions
The current architecture is intentionally focused on the Week 2 CPU prototype. Additional components planned for later stages of the project include:
* **Peripheral Simulation:** GPIO, timers, and interrupts.
* **Process Management:** Process and PCB management.
* **CPU Scheduling:** FCFS, Round Robin, and Priority scheduling.
* **Context Switching:** Saving and restoring processor state between processes.
These components can be integrated with the current CPU and memory foundation as the simulator is extended in later weeks.




