package Main.UI;

import java.awt.*;

import javax.swing.*;

import javax.swing.border.EmptyBorder;

import Main.IPC.IPCProtocol;


public class SimulatorUI extends JFrame {

    private CoreCommunicator communicator;

    // UI Components
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
    private final JButton pauseButton = new JButton("PAUSE");

    public SimulatorUI() {
        super("MS51FB9AE Microcontroller Simulator (UI Process)");
        buildUI();
    }

    /**
     * Attaches the communication interface (Mock or Real IPC) to the UI
     */
    public void setCommunicator(CoreCommunicator communicator) {
        this.communicator = communicator;
    }

    /**
     * Called when a legacy CPUState update arrives (mock mode).
     * Kept for backward compatibility with MockCoreCommunicator.
     */
    public void onStateReceived(CPUState state) {
        if (state == null) return;

        SwingUtilities.invokeLater(() -> {
            // 1. Update CPU Register Display
            pcLabel.setText(String.format("%04X", state.pc));
            spLabel.setText(hex(state.sp));
            aLabel.setText(hex(state.acc));

            // 2. Update RAM View
            if (state.ram != null) {
                refreshDataMemoryPanel(state.ram);
            }

            // 3. Update Stack View
            if (state.stackData != null) {
                refreshStackPanel(state.sp, state.stackData);
            }

            // 4. Update Execution Trace
            if (state.lastInstruction != null && !state.lastInstruction.isEmpty()) {
                appendTrace(state.lastInstruction);
                currentInstructionLabel.setText("Current instruction: " + state.lastInstruction);
            }

            System.err.println("[UI Process] Rendered updated CPU state -> PC: " 
                    + String.format("0x%04X", state.pc) + " | SP: " + String.format("0x%02X", state.sp));
        });
    }

    /**
     * Called when a full CoreStateSnapshot arrives via IPC.
     * Updates all UI panels with the complete simulator state.
     */
    public void onSnapshotReceived(IPCProtocol.SnapshotData snapshot) {
        if (snapshot == null) return;

        SwingUtilities.invokeLater(() -> {
            // 1. Update CPU Register Display
            pcLabel.setText(String.format("%04X", snapshot.pc));
            spLabel.setText(hex(snapshot.sp));
            aLabel.setText(hex(snapshot.acc));
            bLabel.setText(hex(snapshot.b));
            pswLabel.setText(hex(snapshot.psw));

            // R0-R7
            for (int i = 0; i < 8; i++) {
                if (rLabels[i] != null) {
                    rLabels[i].setText(hex(snapshot.r[i]));
                }
            }

            // Flags
            cyLabel.setText(snapshot.cy ? "1" : "0");
            acLabel.setText(snapshot.ac ? "1" : "0");
            ovLabel.setText(snapshot.ov ? "1" : "0");

            // 2. Update Status
            String status = snapshot.getStatusString();
            statusLabel.setText("Status: " + status);

            // 3. Update Data Memory
            if (snapshot.dataMemory != null && snapshot.dataMemory.length > 0) {
                refreshDataMemoryPanelFromInts(snapshot.dataMemory);
            }

            // 4. Update Stack View
            if (snapshot.stackContents != null) {
                refreshStackPanelFromInts(snapshot.sp, snapshot.stackContents);
            }

            // 5. Update Queue View
            refreshQueuePanel(snapshot);

            // 6. Update Program View
            if (snapshot.programMemory != null && snapshot.programMemory.length > 0) {
                refreshProgramPanel(snapshot.programMemory, snapshot.pc);
            }

            // 7. Trace info
            String traceMsg = String.format("PC=0x%04X A=0x%02X SP=0x%02X cycles=%d %s",
                    snapshot.pc, snapshot.acc, snapshot.sp,
                    snapshot.cycleCount, status);
            if (snapshot.lastInstruction != null) {
                traceMsg = "[" + snapshot.lastInstruction + "] " + traceMsg;
            }
            appendTrace(traceMsg);
            currentInstructionLabel.setText("State: " + traceMsg);

            System.err.println("[UI Process] Rendered IPC state -> PC: 0x"
                    + String.format("%04X", snapshot.pc) + " | Status: " + status);
        });
    }

    /**
     * Called when an error response arrives via IPC.
     */
    public void onErrorReceived(String errorMessage) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Status: ERROR");
            appendTrace("ERROR: " + errorMessage);
            currentInstructionLabel.setText("Error: " + errorMessage);
        });
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

        // Connect Button Clicks to IPC Communicator
        loadButton.addActionListener(e -> sendCommandToCore("LOAD"));
        stackDemoButton.addActionListener(e -> sendCommandToCore("LOAD_STACK_DEMO"));
        queueDemoButton.addActionListener(e -> sendCommandToCore("LOAD_QUEUE_DEMO"));
        resetButton.addActionListener(e -> sendCommandToCore("RESET"));
        stepButton.addActionListener(e -> sendCommandToCore("STEP"));
        runButton.addActionListener(e -> sendCommandToCore("RUN"));
        pauseButton.addActionListener(e -> sendCommandToCore("PAUSE"));
    }

    private void sendCommandToCore(String command) {
        if (communicator != null) {
            communicator.sendCommand(command);
            statusLabel.setText("Status: SENT " + command);
        } else {
            statusLabel.setText("Status: ERROR (No Communicator Attached)");
        }
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
        controls.add(pauseButton);
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

    private void appendTrace(String text) {
        traceArea.append(text.endsWith("\n") ? text : text + "\n");
        traceArea.setCaretPosition(traceArea.getDocument().getLength());
    }

    private String hex(int value) {
        return String.format("%02X", value & 0xFF);
    }

    // ==================== Data Memory rendering ====================

    private void refreshDataMemoryPanel(byte[] ram) {
        StringBuilder sb = new StringBuilder();
        sb.append("ADDR  00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F\n");
        sb.append("-------------------------------------------------------\n");
        for (int row = 0; row < ram.length; row += 16) {
            sb.append(String.format("0x%02X  ", row));
            for (int col = 0; col < 16; col++) {
                if (row + col < ram.length) {
                    sb.append(String.format("%02X ", ram[row + col]));
                }
                if (col == 7) sb.append(" ");
            }
            sb.append("\n");
        }
        dataMemoryArea.setText(sb.toString());
        dataMemoryArea.setCaretPosition(0);
    }

    private void refreshDataMemoryPanelFromInts(int[] ram) {
        StringBuilder sb = new StringBuilder();
        sb.append("ADDR  00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F\n");
        sb.append("-------------------------------------------------------\n");
        for (int row = 0; row < ram.length; row += 16) {
            sb.append(String.format("0x%02X  ", row));
            for (int col = 0; col < 16; col++) {
                if (row + col < ram.length) {
                    sb.append(String.format("%02X ", ram[row + col] & 0xFF));
                }
                if (col == 7) sb.append(" ");
            }
            sb.append("\n");
        }
        dataMemoryArea.setText(sb.toString());
        dataMemoryArea.setCaretPosition(0);
    }

    // ==================== Stack rendering ====================

    private void refreshStackPanel(int sp, String[] stackData) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Stack Pointer (SP): 0x%02X\n", sp));
        sb.append("----------------------------------\n");

        if (stackData != null) {
            for (int i = 0; i < stackData.length; i++) {
                int addr = 0x07 + i;
                String marker = (addr == sp) ? "  <-- SP" : "";
                sb.append(String.format("RAM[0x%02X]: %s%s\n", addr, stackData[i], marker));
            }
        }
        stackArea.setText(sb.toString());
        stackArea.setCaretPosition(0);
    }

    private void refreshStackPanelFromInts(int sp, int[] stackContents) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Stack Pointer (SP): 0x%02X\n", sp));
        sb.append("----------------------------------\n");

        if (sp <= 0x07) {
            sb.append("Stack is empty\n");
        } else {
            for (int i = 0; i < stackContents.length; i++) {
                int addr = 0x08 + i;
                String marker = (addr == sp) ? "  <-- SP (top)" : "";
                sb.append(String.format("RAM[0x%02X]: 0x%02X%s\n", addr, stackContents[i] & 0xFF, marker));
            }
        }
        stackArea.setText(sb.toString());
        stackArea.setCaretPosition(0);
    }

    // ==================== Queue rendering ====================

    private void refreshQueuePanel(IPCProtocol.SnapshotData snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Head: %d  |  Tail: %d  |  Count: %d  |  Capacity: %d\n",
                snapshot.queueHead, snapshot.queueTail, snapshot.queueCount, snapshot.queueCapacity));
        sb.append("------------------------------------------\n");

        if (snapshot.queueContents != null && snapshot.queueContents.length > 0) {
            sb.append("Contents (FIFO order):\n");
            for (int i = 0; i < snapshot.queueContents.length; i++) {
                sb.append(String.format("  [%d]: 0x%02X\n", i, snapshot.queueContents[i] & 0xFF));
            }
        } else {
            sb.append("Queue is empty\n");
        }

        queueArea.setText(sb.toString());
        queueArea.setCaretPosition(0);
    }

    // ==================== Program rendering ====================

    private void refreshProgramPanel(int[] programMemory, int currentPC) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-8s %-10s %-20s\n", "ADDR", "HEX", "INSTRUCTION"));
        sb.append("------------------------------------------------\n");

        int i = 0;
        while (i < programMemory.length) {
            int addr = i;
            int opcode = programMemory[i] & 0xFF;
            String hexBytes;
            String mnemonic;
            int nextI;

            switch (opcode) {
                case 0x74: // MOV A,#data
                    if (i + 1 < programMemory.length) {
                        int imm = programMemory[i + 1] & 0xFF;
                        hexBytes = String.format("%02X %02X", opcode, imm);
                        mnemonic = String.format("MOV A,#0x%02X", imm);
                        nextI = i + 2;
                    } else {
                        hexBytes = String.format("%02X", opcode);
                        mnemonic = "MOV A,???";
                        nextI = i + 1;
                    }
                    break;

                case 0x24: // ADD A,#data
                    if (i + 1 < programMemory.length) {
                        int imm = programMemory[i + 1] & 0xFF;
                        hexBytes = String.format("%02X %02X", opcode, imm);
                        mnemonic = String.format("ADD A,#0x%02X", imm);
                        nextI = i + 2;
                    } else {
                        hexBytes = String.format("%02X", opcode);
                        mnemonic = "ADD A,???";
                        nextI = i + 1;
                    }
                    break;

                case 0x94: // SUBB A,#data
                    if (i + 1 < programMemory.length) {
                        int imm = programMemory[i + 1] & 0xFF;
                        hexBytes = String.format("%02X %02X", opcode, imm);
                        mnemonic = String.format("SUBB A,#0x%02X", imm);
                        nextI = i + 2;
                    } else {
                        hexBytes = String.format("%02X", opcode);
                        mnemonic = "SUBB A,???";
                        nextI = i + 1;
                    }
                    break;

                case 0x54: // ANL A,#data
                    if (i + 1 < programMemory.length) {
                        int imm = programMemory[i + 1] & 0xFF;
                        hexBytes = String.format("%02X %02X", opcode, imm);
                        mnemonic = String.format("ANL A,#0x%02X", imm);
                        nextI = i + 2;
                    } else {
                        hexBytes = String.format("%02X", opcode);
                        mnemonic = "ANL A,???";
                        nextI = i + 1;
                    }
                    break;

                case 0xE5: // MOV A,addr
                    if (i + 1 < programMemory.length) {
                        int direct = programMemory[i + 1] & 0xFF;
                        hexBytes = String.format("%02X %02X", opcode, direct);
                        mnemonic = String.format("MOV A,0x%02X", direct);
                        nextI = i + 2;
                    } else {
                        hexBytes = String.format("%02X", opcode);
                        mnemonic = "MOV A,???";
                        nextI = i + 1;
                    }
                    break;

                case 0xF5: // MOV addr,A
                    if (i + 1 < programMemory.length) {
                        int direct = programMemory[i + 1] & 0xFF;
                        hexBytes = String.format("%02X %02X", opcode, direct);
                        mnemonic = String.format("MOV 0x%02X,A", direct);
                        nextI = i + 2;
                    } else {
                        hexBytes = String.format("%02X", opcode);
                        mnemonic = "MOV ???,A";
                        nextI = i + 1;
                    }
                    break;

                case 0x80: // SJMP rel
                    if (i + 1 < programMemory.length) {
                        int rel = (byte) programMemory[i + 1];
                        hexBytes = String.format("%02X %02X", opcode, programMemory[i + 1] & 0xFF);
                        mnemonic = String.format("SJMP %+d", rel);
                        nextI = i + 2;
                    } else {
                        hexBytes = String.format("%02X", opcode);
                        mnemonic = "SJMP ???";
                        nextI = i + 1;
                    }
                    break;

                case 0x04: // INC A
                    hexBytes = String.format("%02X", opcode);
                    mnemonic = "INC A";
                    nextI = i + 1;
                    break;

                case 0x14: // DEC A
                    hexBytes = String.format("%02X", opcode);
                    mnemonic = "DEC A";
                    nextI = i + 1;
                    break;

                case 0xA4: // MUL AB
                    hexBytes = String.format("%02X", opcode);
                    mnemonic = "MUL AB";
                    nextI = i + 1;
                    break;

                case 0xC0: // PUSH A
                    hexBytes = String.format("%02X", opcode);
                    mnemonic = "PUSH A";
                    nextI = i + 1;
                    break;

                case 0xD0: // POP A
                    hexBytes = String.format("%02X", opcode);
                    mnemonic = "POP A";
                    nextI = i + 1;
                    break;

                case 0xFF: // HALT
                    hexBytes = String.format("%02X", opcode);
                    mnemonic = "HALT";
                    nextI = i + 1;
                    break;

                default:
                    if (opcode >= 0x78 && opcode <= 0x7F) { // MOV Rn,#data
                        int r = opcode - 0x78;
                        if (i + 1 < programMemory.length) {
                            int imm = programMemory[i + 1] & 0xFF;
                            hexBytes = String.format("%02X %02X", opcode, imm);
                            mnemonic = String.format("MOV R%d,#0x%02X", r, imm);
                            nextI = i + 2;
                        } else {
                            hexBytes = String.format("%02X", opcode);
                            mnemonic = String.format("MOV R%d,???", r);
                            nextI = i + 1;
                        }
                    } else {
                        hexBytes = String.format("%02X", opcode);
                        mnemonic = String.format("DB 0x%02X", opcode);
                        nextI = i + 1;
                    }
                    break;
            }

            String marker = (addr == currentPC) ? "  <-- PC" : "";
            sb.append(String.format("0x%04X:  %-7s  %-16s%s\n", addr, hexBytes, mnemonic, marker));
            i = nextI;
        }

        programArea.setText(sb.toString());
        programArea.setCaretPosition(0);
    }
}