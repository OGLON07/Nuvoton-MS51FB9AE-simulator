package Main.UI;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SimulatorUI {

    static int pc = 0;
    static int acc = 0;
    static int bReg = 0;
    static int sp = 7;
    static boolean hasJumped = false;

    public static void main(String[] args) {
        JFrame window = new JFrame("8051 Microcontroller Simulator - MS51FB9AE");
        window.setSize(800, 500);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        JButton loadBtn = new JButton("Load");
        JButton resetBtn = new JButton("Reset");
        JButton stepBtn = new JButton("Step");
        JButton runBtn = new JButton("Run");

        topPanel.add(loadBtn);
        topPanel.add(resetBtn);
        topPanel.add(stepBtn);
        topPanel.add(runBtn);
        window.add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(1, 3, 5, 5));

        JTextArea programText = new JTextArea();
        programText.setEditable(false);
        programText.setText("PROGRAM MEMORY\n" +
                "------------------\n" +
                "0000: MOV ACC, #05\n" +
                "0001: MOV B, ACC\n" +
                "0002: ADD ACC, #03\n" +
                "0003: SUBB ACC, #02\n" +
                "0004: ANL ACC, #0F\n" +
                "0005: INC ACC\n" +
                "0006: DEC ACC\n" +
                "0007: MUL AB\n" +
                "0008: SJMP 0005\n" +
                "0009: END");

        JTextArea cpuText = new JTextArea();
        cpuText.setEditable(false);
        updateCpuText(cpuText, "IDLE");

        JTextArea traceText = new JTextArea();
        traceText.setEditable(false);
        traceText.setText("EXECUTION TRACE\n" +
                "------------------\n" +
                "Click Load or Step to start.");

        centerPanel.add(new JScrollPane(programText));
        centerPanel.add(new JScrollPane(cpuText));
        centerPanel.add(new JScrollPane(traceText));

        window.add(centerPanel, BorderLayout.CENTER);

        loadBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                pc = 0;
                acc = 0;
                bReg = 0;
                sp = 7;
                hasJumped = false;
                updateCpuText(cpuText, "LOADED");
                traceText.setText("EXECUTION TRACE\n------------------\nProgram loaded. PC set to 0000.");
            }
        });

        resetBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                pc = 0;
                acc = 0;
                bReg = 0;
                sp = 7;
                hasJumped = false;
                updateCpuText(cpuText, "RESET");
                traceText.setText("EXECUTION TRACE\n------------------\nSystem reset. PC = 0000.");
            }
        });

        stepBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (pc == 0) {
                    int oldAcc = acc;
                    acc = 5;
                    pc = 1;
                    updateCpuText(cpuText, "RUNNING");
                    traceText.setText(
                            "Current Instr: MOV ACC, #05\nCategory: Data Transfer\n\nFetch  : OK\nDecode : OK\nExecute: OK\n\nChanges:\nACC: "
                                    + oldAcc + " -> " + acc + "\nPC : 0000 -> 0001");
                } else if (pc == 1) {
                    int oldB = bReg;
                    bReg = acc;
                    pc = 2;
                    updateCpuText(cpuText, "RUNNING");
                    traceText.setText(
                            "Current Instr: MOV B, ACC\nCategory: Data Transfer\n\nFetch  : OK\nDecode : OK\nExecute: OK\n\nChanges:\nB  : "
                                    + oldB + " -> " + bReg + "\nPC : 0001 -> 0002");
                } else if (pc == 2) {
                    int oldAcc = acc;
                    acc += 3;
                    pc = 3;
                    updateCpuText(cpuText, "RUNNING");
                    traceText.setText(
                            "Current Instr: ADD ACC, #03\nCategory: Arithmetic\n\nFetch  : OK\nDecode : OK\nExecute: OK\n\nChanges:\nACC: "
                                    + oldAcc + " -> " + acc + "\nPC : 0002 -> 0003");
                } else if (pc == 3) {
                    int oldAcc = acc;
                    acc -= 2;
                    pc = 4;
                    updateCpuText(cpuText, "RUNNING");
                    traceText.setText(
                            "Current Instr: SUBB ACC, #02\nCategory: Arithmetic\n\nFetch  : OK\nDecode : OK\nExecute: OK\n\nChanges:\nACC: "
                                    + oldAcc + " -> " + acc + "\nPC : 0003 -> 0004");
                } else if (pc == 4) {
                    int oldAcc = acc;
                    acc = acc & 0x0F;
                    pc = 5;
                    updateCpuText(cpuText, "RUNNING");
                    traceText.setText(
                            "Current Instr: ANL ACC, #0F\nCategory: Logical Operation\n\nFetch  : OK\nDecode : OK\nExecute: OK\n\nChanges:\nACC: "
                                    + oldAcc + " -> " + acc + "\nPC : 0004 -> 0005");
                } else if (pc == 5) {
                    int oldAcc = acc;
                    acc++;
                    pc = 6;
                    updateCpuText(cpuText, "RUNNING");
                    traceText.setText(
                            "Current Instr: INC ACC\nCategory: Increment / Decrement\n\nFetch  : OK\nDecode : OK\nExecute: OK\n\nChanges:\nACC: "
                                    + oldAcc + " -> " + acc + "\nPC : 0005 -> 0006");
                } else if (pc == 6) {
                    int oldAcc = acc;
                    acc--;
                    pc = 7;
                    updateCpuText(cpuText, "RUNNING");
                    traceText.setText(
                            "Current Instr: DEC ACC\nCategory: Increment / Decrement\n\nFetch  : OK\nDecode : OK\nExecute: OK\n\nChanges:\nACC: "
                                    + oldAcc + " -> " + acc + "\nPC : 0006 -> 0007");
                } else if (pc == 7) {
                    int oldAcc = acc;
                    acc = acc * bReg;
                    pc = 8;
                    updateCpuText(cpuText, "RUNNING");
                    traceText.setText(
                            "Current Instr: MUL AB\nCategory: Arithmetic\n\nFetch  : OK\nDecode : OK\nExecute: OK\n\nChanges:\nACC: "
                                    + oldAcc + " -> " + acc + " (" + oldAcc + " * " + bReg + ")\nPC : 0007 -> 0008");
                } else if (pc == 8) {
                    if (!hasJumped) {
                        int oldPc = pc;
                        pc = 5;
                        hasJumped = true;
                        updateCpuText(cpuText, "JUMPING");
                        traceText.setText(
                                "Current Instr: SJMP 0005\nCategory: Control Flow\n\nFetch  : OK\nDecode : OK\nExecute: OK\n\nChanges:\nPC : 000"
                                        + oldPc + " -> 0005 (Jumped Back)");
                    } else {
                        pc = 9;
                        updateCpuText(cpuText, "RUNNING");
                        traceText.setText(
                                "Current Instr: SJMP 0005\nCategory: Control Flow\n\nLoop completed (already jumped once).\nProceeding to PC : 0009");
                    }
                } else {
                    updateCpuText(cpuText, "HALTED");
                    traceText.setText(
                            "Current Instr: END\nCategory: Program Termination\n\nFetch  : OK\nDecode : OK\nExecute: OK\n\nProgram execution ended.");
                }
            }
        });

        runBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                pc = 9;
                acc = 150;
                bReg = 5;
                updateCpuText(cpuText, "HALTED");
                traceText.setText("Program executed to completion.\n\nFinal State:\nACC = 150\nB = 5\nPC = 0009");
            }
        });

        window.setVisible(true);
    }

    private static void updateCpuText(JTextArea area, String status) {
        area.setText("CPU REGISTERS\n" +
                "------------------\n" +
                "Status : " + status + "\n" +
                "PC     : 000" + pc + "\n" +
                "ACC    : " + acc + "\n" +
                "B      : " + bReg + "\n" +
                "SP     : 0" + sp + "\n" +
                "PSW    : 00");
    }
}