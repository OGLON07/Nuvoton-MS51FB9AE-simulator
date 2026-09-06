package Main;

import Main.CPU.CPU;


// In progress still not comppleted


public class Main {

    public static void main(String[] args) {

        // Create the CPU
        CPU cpu = new CPU();

        // Set some register values
        cpu.getRegisters().setR(0, 25);
        cpu.getRegisters().setAccumulator(100);

        // Display the values
        System.out.println("Before reset:");
        System.out.println("R0 = " + cpu.getRegisters().getR(0));
        System.out.println("A  = " + cpu.getRegisters().getAccumulator());

        // Reset the CPU
        cpu.reset();

        // Display the values after reset
        System.out.println("\nAfter reset:");
        System.out.println("R0 = " + cpu.getRegisters().getR(0));
        System.out.println("A  = " + cpu.getRegisters().getAccumulator());
    }
}