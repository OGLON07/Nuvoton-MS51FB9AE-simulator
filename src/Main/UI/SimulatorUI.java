package Main.UI;

import Main.CPU.CPU;
import Main.CPU.Registers;
import Main.Memory.Memory;
import Main.instruction.Instruction;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class SimulatorUI extends JFrame {

    private final CPU cpu;

    private final JTextArea programArea = new JTextArea();
    private final JTextArea traceArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("Status: READY");
    private final JLabel currentInstructionLabel = new JLabel("Current instruction: —");

    private final JLabel aLabel = valueLabel();
    private final JLabel bLabel = valueLabel();
    private final JLabel pcLabel = valueLabel();
    private final JLabel spLabel = valueLabel();
    private final JLabel pswLabel = valueLabel();
    private final JLabel[] rLabels = new JLabel[8];
    private final JLabel cyLabel = valueLabel();
    private final JLabel acLabel = valueLabel();
    private final JLabel ovLabel = valueLabel();

    private final JButton loadButton = new JButton("LOAD");
    private final JButton resetButton = new JButton("RESET");
    private final JButton stepButton = new JButton("STEP");
    private final JButton runButton = new JButton("RUN");

    private File loadedFile;
    private boolean running;

    // Delay between each instruction during RUN, so execution is visible
    // step-by-step instead of finishing instantly.
    private static final int RUN_STEP_DELAY_MS = 400;

    private static final int[] DEMO_PROGRAM = {
            0x74, 0x0A,   // MOV A,#0A         (Data Transfer)
            0x7B, 0x05,   // MOV R3,#05        (Data Transfer)
            0x24, 0x05,   // ADD A,#05         (Arithmetic)
            0x94, 0x03,   // SUBB A,#03        (Arithmetic)
            0xA4,         // MUL AB            (Arithmetic)
            0x54, 0x0F,   // ANL A,#0F         (Logical)
            0x04,         // INC A             (Increment)
            0x14,         // DEC A             (Decrement)
            0x80, 0x02,   // SJMP +2           (Control Flow - skips next instr)
            0x74, 0xFF,   // MOV A,#FF         (dead code - proves the jump worked)
            0xFF          // HALT              (Termination)
    };

    private SimulatorUI() {
        super("MS51FB9AE Microcontroller Simulator");
        cpu = new CPU();
        buildUI();
        loadDemoProgram();
        refreshCPUState();
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            SimulatorUI ui = new SimulatorUI();
            ui.setVisible(true);
        });
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        JLabel title = new JLabel("MS51FB9AE MICROCONTROLLER SIMULATOR", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        root.add(title, BorderLayout.NORTH);

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createProgramPanel(), createCPUStatePanel());
        center.setResizeWeight(0.58);
        root.add(center, BorderLayout.CENTER);

        root.add(createBottomPanel(), BorderLayout.SOUTH);

        loadButton.addActionListener(e -> loadDemoProgram());
        resetButton.addActionListener(e -> resetCPU());
        stepButton.addActionListener(e -> stepCPU());
        runButton.addActionListener(e -> runCPU());
    }

    private JPanel createProgramPanel() {
        JPanel panel = titledPanel("PROGRAM");
        programArea.setEditable(false);
        programArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        programArea.setMargin(new Insets(8, 8, 8, 8));
        panel.add(new JScrollPane(programArea), BorderLayout.CENTER);
        panel.add(currentInstructionLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createCPUStatePanel() {
        JPanel panel = titledPanel("CPU STATE");
        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 5));

        addStateRow(grid, "A", aLabel);
        addStateRow(grid, "B", bLabel);
        addStateRow(grid, "PC", pcLabel);
        addStateRow(grid, "SP", spLabel);
        addStateRow(grid, "PSW", pswLabel);

        for (int i = 0; i < 8; i++) {
            rLabels[i] = valueLabel();
            addStateRow(grid, "R" + i, rLabels[i]);
        }

        addStateRow(grid, "CY", cyLabel);
        addStateRow(grid, "AC", acLabel);
        addStateRow(grid, "OV", ovLabel);

        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout(8, 8));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(loadButton);
        controls.add(resetButton);
        controls.add(stepButton);
        controls.add(runButton);
        controls.add(statusLabel);
        bottom.add(controls, BorderLayout.NORTH);

        JPanel tracePanel = titledPanel("EXECUTION TRACE");
        traceArea.setEditable(false);
        traceArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        traceArea.setRows(9);
        traceArea.setMargin(new Insets(6, 6, 6, 6));
        tracePanel.add(new JScrollPane(traceArea), BorderLayout.CENTER);
        bottom.add(tracePanel, BorderLayout.CENTER);

        return bottom;
    }

    private JPanel titledPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                new EmptyBorder(5, 5, 5, 5)
        ));
        return panel;
    }

    private static JLabel valueLabel() {
        JLabel label = new JLabel("00");
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        return label;
    }

    private static void addStateRow(JPanel panel, String name, JLabel value) {
        panel.add(new JLabel(name));
        panel.add(value);
    }

    private void loadDemoProgram() {
        cpu.getMemory().loadProgram(DEMO_PROGRAM);
        cpu.reset();
        loadedFile = null;
        traceArea.setText("Demo program loaded (all 10 instructions). Press STEP or RUN.\n");
        statusLabel.setText("Status: READY (DEMO PROGRAM)");
        refreshProgramDisplay();
        setControlsEnabled(true);
    }

    private void loadProgramFromFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Program Bytes");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Program files (*.txt, *.hex)", "txt", "hex"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            int[] program = parseProgramFile(file);
            cpu.getMemory().loadProgram(program);
            cpu.reset();
            loadedFile = file;
            traceArea.setText("Loaded: " + file.getName() + "\n");
            statusLabel.setText("Status: READY");
            refreshProgramDisplay();
            refreshCPUState();
        } catch (Exception ex) {
            showError("Could not load program: " + ex.getMessage());
            statusLabel.setText("Status: ERROR");
        }
    }

    private int[] parseProgramFile(File file) throws Exception {
        String text = Files.readString(file.toPath());
        text = text.replaceAll("(?m)#.*$", "");
        text = text.replaceAll("(?m)//.*$", "");
        String[] tokens = text.trim().split("[\\s,;]+");

        if (text.trim().isEmpty()) throw new IllegalArgumentException("Program file is empty");

        List<Integer> bytes = new ArrayList<>();
        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty()) continue;
            if (token.startsWith("0x") || token.startsWith("0X")) token = token.substring(2);
            if (token.endsWith("H") || token.endsWith("h")) token = token.substring(0, token.length() - 1);
            int value = Integer.parseInt(token, 16);
            if (value < 0 || value > 0xFF) throw new IllegalArgumentException("Invalid byte: " + token);
            bytes.add(value);
        }

        int[] result = new int[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) result[i] = bytes.get(i);
        return result;
    }

    private void resetCPU() {
        if (running) return;
        cpu.reset();
        traceArea.setText("CPU reset. Loaded program retained.\n");
        statusLabel.setText("Status: READY");
        refreshProgramDisplay();
        refreshCPUState();
        setControlsEnabled(true);
    }

    private void stepCPU() {
        if (running || cpu.isHalted()) {
            if (cpu.isHalted()) statusLabel.setText("Status: HALTED — press RESET to restart");
            return;
        }

        int oldPC = cpu.getRegisters().getPC();
        int oldA = cpu.getRegisters().getAccumulator();
        int oldB = cpu.getRegisters().getB();
        int oldSP = cpu.getRegisters().getSP();
        int oldPSW = cpu.getRegisters().getPSW();

        try {
            cpu.step();
            Instruction instruction = cpu.getDecodedInstruction();
            appendTrace(oldPC, instruction, oldA, oldB, oldSP, oldPSW);
            refreshCPUState();
            refreshProgramDisplay();
            statusLabel.setText(cpu.isHalted() ? "Status: HALTED" : "Status: READY");
        } catch (Exception ex) {
            statusLabel.setText("Status: ERROR");
            appendTrace("ERROR: " + ex.getMessage());
            showError(ex.getMessage());
        }
    }

    private void runCPU() {
        if (running || cpu.isHalted()) {
            if (cpu.isHalted()) statusLabel.setText("Status: HALTED — press RESET to restart");
            return;
        }

        running = true;
        setControlsEnabled(false);
        statusLabel.setText("Status: RUNNING");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            private Exception failure;
            private int steps;

            @Override
            protected Void doInBackground() {
                try {
                    while (!cpu.isHalted()) {
                        if (++steps > 100000) throw new IllegalStateException("Execution stopped after 100000 steps (possible infinite loop)");

                        int oldPC = cpu.getRegisters().getPC();
                        int oldA = cpu.getRegisters().getAccumulator();
                        int oldB = cpu.getRegisters().getB();
                        int oldSP = cpu.getRegisters().getSP();
                        int oldPSW = cpu.getRegisters().getPSW();

                        cpu.step();
                        Instruction instruction = cpu.getDecodedInstruction();
                        publish(formatTrace(oldPC, instruction, oldA, oldB, oldSP, oldPSW));

                        // Pause between instructions so RUN visibly executes
                        // one step at a time instead of finishing instantly.
                        if (!cpu.isHalted()) {
                            Thread.sleep(RUN_STEP_DELAY_MS);
                        }
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    failure = ex;
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) appendTrace(chunk);
                // Update the register/CPU-state panel after every single step,
                // not just once at the very end.
                refreshCPUState();
                refreshProgramDisplay();
            }

            @Override
            protected void done() {
                running = false;
                setControlsEnabled(true);
                try { get(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } catch (ExecutionException ignored) { }

                if (failure != null) {
                    statusLabel.setText("Status: ERROR");
                    appendTrace("ERROR: " + failure.getMessage());
                    showError(failure.getMessage());
                } else {
                    statusLabel.setText(cpu.isHalted() ? "Status: HALTED" : "Status: READY");
                }
                refreshCPUState();
                refreshProgramDisplay();
            }
        };
        worker.execute();
    }

    private String formatTrace(int oldPC, Instruction instruction, int oldA, int oldB, int oldSP, int oldPSW) {
        StringBuilder s = new StringBuilder();
        s.append("FETCH    PC=").append(String.format("%04X", oldPC))
                .append(" OPCODE=").append(String.format("%02X", cpu.getFetchedOpcode())).append('\n');
        s.append("DECODE   ").append(formatInstruction(instruction)).append('\n');
        s.append("EXECUTE  ").append(formatChanges(oldA, oldB, oldSP, oldPSW)).append('\n');
        s.append("\n");
        return s.toString();
    }

    private void appendTrace(int oldPC, Instruction instruction, int oldA, int oldB, int oldSP, int oldPSW) {
        appendTrace(formatTrace(oldPC, instruction, oldA, oldB, oldSP, oldPSW));
    }

    private void appendTrace(String text) {
        traceArea.append(text.endsWith("\n") ? text : text + "\n");
        traceArea.setCaretPosition(traceArea.getDocument().getLength());
    }

    private String formatChanges(int oldA, int oldB, int oldSP, int oldPSW) {
        Registers r = cpu.getRegisters();
        StringBuilder s = new StringBuilder();
        boolean changed = false;
        if (oldA != r.getAccumulator()) { s.append("A ").append(hex(oldA)).append(" -> ").append(hex(r.getAccumulator())).append("  "); changed = true; }
        if (oldB != r.getB()) { s.append("B ").append(hex(oldB)).append(" -> ").append(hex(r.getB())).append("  "); changed = true; }
        if (oldSP != r.getSP()) { s.append("SP ").append(hex(oldSP)).append(" -> ").append(hex(r.getSP())).append("  "); changed = true; }
        if (oldPSW != r.getPSW()) { s.append("PSW ").append(hex(oldPSW)).append(" -> ").append(hex(r.getPSW())).append("  "); changed = true; }
        if (!changed) s.append("No register/flag changes");
        return s.toString().trim();
    }

    private String formatInstruction(Instruction instruction) {
        if (instruction == null) return "—";
        switch (instruction.getOpcode()) {
            case MOV_A_IMM: return "MOV A,#" + hex(instruction.getOperand());
            case MOV_RN_IMM: return "MOV R" + instruction.getRegisterIndex() + ",#" + hex(instruction.getOperand());
            case ADD_A_IMM: return "ADD A,#" + hex(instruction.getOperand());
            case SUBB_A_IMM: return "SUBB A,#" + hex(instruction.getOperand());
            case MUL_AB: return "MUL AB";
            case ANL_A_IMM: return "ANL A,#" + hex(instruction.getOperand());
            case INC_A: return "INC A";
            case DEC_A: return "DEC A";
            case SJMP: return "SJMP " + instruction.getOperand();
            case HALT: return "HALT";
            default: return instruction.getOpcode().toString();
        }
    }

    private String hex(int value) { return String.format("%02X", value & 0xFF); }

    private void refreshCPUState() {
        Registers r = cpu.getRegisters();
        aLabel.setText(hex(r.getAccumulator()));
        bLabel.setText(hex(r.getB()));
        pcLabel.setText(String.format("%04X", r.getPC()));
        spLabel.setText(hex(r.getSP()));
        pswLabel.setText(hex(r.getPSW()));
        for (int i = 0; i < 8; i++) rLabels[i].setText(hex(r.getR(i)));
        cyLabel.setText(r.isCarry() ? "1" : "0");
        acLabel.setText(r.isAuxiliaryCarry() ? "1" : "0");
        ovLabel.setText(r.isOverflow() ? "1" : "0");
    }

    private void refreshProgramDisplay() {
        Memory memory = cpu.getMemory();
        StringBuilder text = new StringBuilder();
        int size = memory.getProgramSize();
        for (int address = 0; address < size;) {
            int opcode = memory.readProgram(address);
            int length = instructionLength(opcode);
            text.append(String.format("%04X  ", address));
            for (int i = 0; i < length && address + i < size; i++) {
                text.append(String.format("%02X ", memory.readProgram(address + i)));
            }
            text.append("   ").append(disassemble(memory, address, opcode)).append('\n');
            address += Math.min(length, Math.max(1, size - address));
        }
        programArea.setText(text.toString());
        Instruction decoded = cpu.getDecodedInstruction();
        if (decoded != null) {
            currentInstructionLabel.setText("Current instruction: " + formatInstruction(decoded));
        } else {
            int pc = cpu.getRegisters().getPC();
            currentInstructionLabel.setText("Current instruction: " + (pc < size ? disassemble(memory, pc, memory.readProgram(pc)) : "—"));
        }
    }

    private int instructionLength(int opcode) {
        if (opcode == 0x74 || opcode == 0x24 || opcode == 0x94 || opcode == 0x54 || opcode == 0x80 || (opcode >= 0x78 && opcode <= 0x7F)) return 2;
        return 1;
    }

    private String disassemble(Memory memory, int address, int opcode) {
        int operand = address + 1 < memory.getProgramSize() ? memory.readProgram(address + 1) : 0;
        switch (opcode) {
            case 0x74: return "MOV A,#" + hex(operand);
            case 0x78: case 0x79: case 0x7A: case 0x7B: case 0x7C: case 0x7D: case 0x7E: case 0x7F:
                return "MOV R" + (opcode - 0x78) + ",#" + hex(operand);
            case 0x24: return "ADD A,#" + hex(operand);
            case 0x94: return "SUBB A,#" + hex(operand);
            case 0xA4: return "MUL AB";
            case 0x54: return "ANL A,#" + hex(operand);
            case 0x04: return "INC A";
            case 0x14: return "DEC A";
            case 0x80: return "SJMP " + ((operand >= 128) ? operand - 256 : operand);
            case 0xFF: return "HALT";
            default: return "UNKNOWN";
        }
    }

    private void setControlsEnabled(boolean enabled) {
        loadButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
        stepButton.setEnabled(enabled && !cpu.isHalted());
        runButton.setEnabled(enabled && !cpu.isHalted());
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Simulator Error", JOptionPane.ERROR_MESSAGE);
    }
}