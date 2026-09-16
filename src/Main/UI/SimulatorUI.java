package Main.UI;

import Main.CPU.CPU;
import Main.CPU.Registers;
import Main.Memory.Memory;
import Main.Queue.Queue;
import Main.instruction.Instruction;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;

public class SimulatorUI extends JFrame {

    private final CPU cpu;
    private final Queue queue;

    private final JTextPane programArea = new JTextPane();
    private final JTextArea traceArea = new JTextArea();
    private final JTextArea dataMemoryArea = new JTextArea();
    private final JTextArea stackArea = new JTextArea();
    private final JTextArea queueArea = new JTextArea();
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
    private final JButton stackDemoButton = new JButton("STACK DEMO");
    private final JButton queueDemoButton = new JButton("QUEUE DEMO");
    private final JButton resetButton = new JButton("RESET");
    private final JButton stepButton = new JButton("STEP");
    private final JButton runButton = new JButton("RUN");

    private boolean running;

    private static final int RUN_STEP_DELAY_MS = 400;

    private static final int[] DEMO_PROGRAM = {
            0x74, 0x0A,   // MOV A,#0A
            0x7B, 0x05,   // MOV R3,#05
            0x24, 0x05,   // ADD A,#05
            0x94, 0x03,   // SUBB A,#03
            0xA4,         // MUL AB
            0x54, 0x0F,   // ANL A,#0F
            0x04,         // INC A
            0x14,         // DEC A
            0x74, 0x42,   // MOV A,#42
            0xF5, 0x30,   // MOV 30H,A
            0x74, 0x00,   // MOV A,#00
            0xE5, 0x30,   // MOV A,30H
            0x80, 0x02,   // SJMP +2
            0x74, 0xFF,   // MOV A,#FF
            0xFF          // HALT
    };

    private static final int[] STACK_DEMO_PROGRAM = {
            0x74, 0x55,   // MOV A,#55H   (Load A with 0x55)
            0xC0,         // PUSH A       (Push 0x55 onto Stack, SP becomes 0x08)
            0x74, 0xAA,   // MOV A,#AAH   (Load A with 0xAA)
            0xC0,         // PUSH A       (Push 0xAA onto Stack, SP becomes 0x09)
            0x74, 0x00,   // MOV A,#00H   (Clear A to 0x00)
            0xD0,         // POP A        (Pop 0xAA back into A, SP returns to 0x08)
            0xD0,         // POP A        (Pop 0x55 back into A, SP returns to 0x07)
            0xFF          // HALT
    };

    private static final int[] QUEUE_DEMO_PROGRAM = {
            0x74, 0x00, 0xF5, 0x30, 0xF5, 0x31, 0xF5, 0x32,
            0x74, 0xAA, 0xF5, 0x40, 0x74, 0x01, 0xF5, 0x31, 0xF5, 0x32,
            0x74, 0xBB, 0xF5, 0x41, 0x74, 0x02, 0xF5, 0x31, 0xF5, 0x32,
            0x74, 0xCC, 0xF5, 0x42, 0x74, 0x03, 0xF5, 0x31, 0xF5, 0x32,
            0xE5, 0x40, 0xF5, 0x50, 0x74, 0x01, 0xF5, 0x30, 0x74, 0x02, 0xF5, 0x32,
            0xE5, 0x41, 0xF5, 0x51, 0x74, 0x02, 0xF5, 0x30, 0x74, 0x01, 0xF5, 0x32,
            0x74, 0xDD, 0xF5, 0x43, 0x74, 0x04, 0xF5, 0x31, 0x74, 0x02, 0xF5, 0x32,
            0xE5, 0x42, 0xF5, 0x52, 0x74, 0x03, 0xF5, 0x30, 0x74, 0x01, 0xF5, 0x32,
            0xE5, 0x43, 0xF5, 0x53, 0x74, 0x04, 0xF5, 0x30, 0x74, 0x00, 0xF5, 0x32,
            0xFF
    };

    private SimulatorUI() {
        super("MS51FB9AE Microcontroller Simulator");
        cpu = new CPU();
        queue = new Queue(cpu.getMemory());
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

    public static void main(String[] args) {
        launch();
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setMinimumSize(new Dimension(1100, 750));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        JLabel title = new JLabel("MS51FB9AE MICROCONTROLLER SIMULATOR", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        root.add(title, BorderLayout.NORTH);

        JPanel rightPanel = new JPanel(new BorderLayout(8, 8));
        rightPanel.add(createCPUStatePanel(), BorderLayout.NORTH);

        JTabbedPane memoryTabs = new JTabbedPane();
        memoryTabs.addTab("DATA MEMORY (RAM)", createDataMemoryPanel());
        memoryTabs.addTab("STACK", createStackPanel());
        memoryTabs.addTab("QUEUE (FIFO)", createQueuePanel());
        rightPanel.add(memoryTabs, BorderLayout.CENTER);

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createProgramPanel(), rightPanel);
        center.setResizeWeight(0.45);
        root.add(center, BorderLayout.CENTER);

        root.add(createBottomPanel(), BorderLayout.SOUTH);

        loadButton.addActionListener(e -> loadDemoProgram());
        stackDemoButton.addActionListener(e -> loadStackDemoProgram());
        queueDemoButton.addActionListener(e -> loadQueueDemoProgram());
        resetButton.addActionListener(e -> resetCPU());
        stepButton.addActionListener(e -> stepCPU());
        runButton.addActionListener(e -> runCPU());
    }

    private JPanel createProgramPanel() {
        JPanel panel = titledPanel("PROGRAM");
        programArea.setEditable(false);
        programArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        programArea.setMargin(new Insets(8, 8, 8, 8));
        programArea.putClientProperty("JEditorPane.honorDisplayProperties", Boolean.TRUE);
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

    private JPanel createDataMemoryPanel() {
        JPanel panel = titledPanel("DATA MEMORY (RAM)");
        dataMemoryArea.setEditable(false);
        dataMemoryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        dataMemoryArea.setMargin(new Insets(6, 6, 6, 6));
        panel.add(new JScrollPane(dataMemoryArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStackPanel() {
        JPanel panel = titledPanel("STACK VIEW");
        stackArea.setEditable(false);
        stackArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        stackArea.setMargin(new Insets(6, 6, 6, 6));
        panel.add(new JScrollPane(stackArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createQueuePanel() {
        JPanel panel = titledPanel("QUEUE (FIFO)");
        queueArea.setEditable(false);
        queueArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        queueArea.setMargin(new Insets(6, 6, 6, 6));
        panel.add(new JScrollPane(queueArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout(8, 8));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(loadButton);
        controls.add(stackDemoButton);
        controls.add(queueDemoButton);
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

        traceArea.setText("Demo program loaded. Press STEP or RUN.\n");
        statusLabel.setText("Status: READY (DEMO PROGRAM)");
        refreshProgramDisplay();
        refreshCPUState();
    }

    private void loadStackDemoProgram() {
        cpu.getMemory().loadProgram(STACK_DEMO_PROGRAM);
        cpu.reset();

        traceArea.setText("Stack demo program loaded. Press STEP to view PUSH/POP in STACK panel.\n");
        statusLabel.setText("Status: READY (STACK DEMO)");
        refreshProgramDisplay();
        refreshCPUState();
        setControlsEnabled(true);
    }

    private void loadQueueDemoProgram() {
        cpu.getMemory().loadProgram(QUEUE_DEMO_PROGRAM);
        cpu.reset();
        traceArea.setText("Queue demo loaded. Press STEP or RUN.\n");
        statusLabel.setText("Status: READY (QUEUE DEMO)");
        refreshProgramDisplay();
        refreshCPUState();
        setControlsEnabled(true);
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
                        if (++steps > 100000) throw new IllegalStateException("Infinite loop threshold exceeded");

                        int oldPC = cpu.getRegisters().getPC();
                        int oldA = cpu.getRegisters().getAccumulator();
                        int oldB = cpu.getRegisters().getB();
                        int oldSP = cpu.getRegisters().getSP();
                        int oldPSW = cpu.getRegisters().getPSW();

                        cpu.step();
                        Instruction instruction = cpu.getDecodedInstruction();
                        publish(formatTrace(oldPC, instruction, oldA, oldB, oldSP, oldPSW));

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
        s.append("EXECUTE  ").append(formatChanges(oldA, oldB, oldSP, oldPSW)).append("\n\n");
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
            case MOV_A_ADDR: return "MOV A," + hex(instruction.getOperand());
            case MOV_ADDR_A: return "MOV " + hex(instruction.getOperand()) + ",A";
            case MOV_RN_IMM: return "MOV R" + instruction.getRegisterIndex() + ",#" + hex(instruction.getOperand());
            case ADD_A_IMM: return "ADD A,#" + hex(instruction.getOperand());
            case SUBB_A_IMM: return "SUBB A,#" + hex(instruction.getOperand());
            case MUL_AB: return "MUL AB";
            case ANL_A_IMM: return "ANL A,#" + hex(instruction.getOperand());
            case INC_A: return "INC A";
            case DEC_A: return "DEC A";
            case PUSH_A: return "PUSH A";
            case POP_A: return "POP A";
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

        refreshDataMemoryPanel();
        refreshStackPanel();
        refreshQueuePanel();
    }

    private void refreshDataMemoryPanel() {
        StringBuilder sb = new StringBuilder();
        sb.append("ADDR  00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F\n");
        sb.append("-------------------------------------------------------\n");
        Memory mem = cpu.getMemory();
        for (int row = 0; row < Memory.RAM_SIZE; row += 16) {
            sb.append(String.format("0x%02X  ", row));
            for (int col = 0; col < 16; col++) {
                sb.append(String.format("%02X ", mem.readData(row + col)));
                if (col == 7) sb.append(" ");
            }
            sb.append("\n");
        }
        dataMemoryArea.setText(sb.toString());
        dataMemoryArea.setCaretPosition(0);
    }

    private void refreshStackPanel() {
        StringBuilder sb = new StringBuilder();
        int currentSP = cpu.getRegisters().getSP();
        sb.append(String.format("Stack Pointer (SP): 0x%02X\n", currentSP));
        sb.append("----------------------------------\n");

        for (int addr = 0x17; addr >= 0x07; addr--) {
            int val = cpu.getMemory().readData(addr);
            String marker = (addr == currentSP) ? "  <-- SP" : "";
            sb.append(String.format("RAM[0x%02X]: 0x%02X%s\n", addr, val, marker));
        }
        stackArea.setText(sb.toString());
        stackArea.setCaretPosition(0);
    }

    private void refreshQueuePanel() {
        StringBuilder sb = new StringBuilder();

        int head = queue.getHead();
        int tail = queue.getTail();
        int count = queue.getCount();
        int capacity = queue.getCapacity();

        sb.append(String.format("HEAD: %02X   TAIL: %02X   COUNT: %d / %d\n", head, tail, count, capacity));

        String status = (count == 0) ? "EMPTY" : (count >= capacity) ? "FULL" : count + " items";
        sb.append("Status: ").append(status).append("\n\n");

        sb.append("Queue Contents (FIFO order):\n");
        if (count == 0) {
            sb.append("  (empty)\n");
        } else {
            int[] contents = queue.getContentsInOrder();
            sb.append("  ");
            for (int i = 0; i < contents.length; i++) {
                if (i > 0) sb.append(" \u2192 ");
                sb.append(String.format("%02X", contents[i]));
            }
            sb.append("\n");
        }

        sb.append("\nBuffer [0x40\u20130x47]:\n");
        for (int i = 0; i < capacity; i++) {
            sb.append(String.format("  [%d]=%02X", i, queue.getBufferElement(i)));
            if (i == head && i == tail) {
                sb.append(" \u25C0H,T");
            } else if (i == head % capacity && count > 0) {
                sb.append(" \u25C0H");
            } else if (i == tail % capacity) {
                sb.append(" \u25C0T");
            }
            if (i == 3) sb.append("\n");
        }
        sb.append("\n");

        queueArea.setText(sb.toString());
        queueArea.setCaretPosition(0);
    }

    private void refreshProgramDisplay() {
        Memory memory = cpu.getMemory();
        int size = memory.getProgramSize();
        int currentPC = cpu.getRegisters().getPC();

        StyledDocument doc = programArea.getStyledDocument();
        programArea.setText("");

        SimpleAttributeSet normal = new SimpleAttributeSet();
        StyleConstants.setFontFamily(normal, Font.MONOSPACED);
        StyleConstants.setFontSize(normal, 14);

        SimpleAttributeSet arrowStyle = new SimpleAttributeSet(normal);
        StyleConstants.setForeground(arrowStyle, Color.RED);
        StyleConstants.setBold(arrowStyle, true);

        try {
            for (int address = 0; address < size;) {
                int opcode = memory.readProgram(address);
                int length = instructionLength(opcode);

                if (address == currentPC) {
                    doc.insertString(doc.getLength(), "\u25B6 ", arrowStyle);
                } else {
                    doc.insertString(doc.getLength(), "  ", normal);
                }

                StringBuilder line = new StringBuilder();
                line.append(String.format("%04X  ", address));
                for (int i = 0; i < length && address + i < size; i++) {
                    line.append(String.format("%02X ", memory.readProgram(address + i)));
                }
                line.append("   ").append(disassemble(memory, address, opcode)).append('\n');
                doc.insertString(doc.getLength(), line.toString(), normal);

                address += Math.min(length, Math.max(1, size - address));
            }
        } catch (BadLocationException ignored) { }

        Instruction decoded = cpu.getDecodedInstruction();
        if (decoded != null) {
            currentInstructionLabel.setText("Current instruction: " + formatInstruction(decoded));
        } else {
            currentInstructionLabel.setText("Current instruction: " + (currentPC < size ? disassemble(memory, currentPC, memory.readProgram(currentPC)) : "\u2014"));
        }
    }

    private int instructionLength(int opcode) {
        if (opcode == 0x74 || opcode == 0x24 || opcode == 0x94 || opcode == 0x54
                || opcode == 0x80 || (opcode >= 0x78 && opcode <= 0x7F)
                || opcode == 0xE5 || opcode == 0xF5) return 2;
        return 1;
    }

    private String disassemble(Memory memory, int address, int opcode) {
        int operand = address + 1 < memory.getProgramSize() ? memory.readProgram(address + 1) : 0;
        switch (opcode) {
            case 0x74: return "MOV A,#" + hex(operand);
            case 0xE5: return "MOV A," + hex(operand);
            case 0xF5: return "MOV " + hex(operand) + ",A";
            case 0x78: case 0x79: case 0x7A: case 0x7B: case 0x7C: case 0x7D: case 0x7E: case 0x7F:
                return "MOV R" + (opcode - 0x78) + ",#" + hex(operand);
            case 0x24: return "ADD A,#" + hex(operand);
            case 0x94: return "SUBB A,#" + hex(operand);
            case 0xA4: return "MUL AB";
            case 0x54: return "ANL A,#" + hex(operand);
            case 0x04: return "INC A";
            case 0x14: return "DEC A";
            case 0xC0: return "PUSH A";
            case 0xD0: return "POP A";
            case 0x80: return "SJMP " + ((operand >= 128) ? operand - 256 : operand);
            case 0xFF: return "HALT";
            default: return "UNKNOWN";
        }
    }

    private void setControlsEnabled(boolean enabled) {
        loadButton.setEnabled(enabled);
        stackDemoButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
        stepButton.setEnabled(enabled && !cpu.isHalted());
        runButton.setEnabled(enabled && !cpu.isHalted());
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Simulator Error", JOptionPane.ERROR_MESSAGE);
    }
}