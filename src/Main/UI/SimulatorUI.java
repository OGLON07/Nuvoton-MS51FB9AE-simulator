package Main.UI;

import java.awt.*;

import javax.swing.*;

import javax.swing.border.EmptyBorder;



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
     * Called when a state update arrives to update GUI elements safely on the EDT
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

            System.out.println("[UI Process] Rendered updated CPU state -> PC: " 
                    + String.format("0x%04X", state.pc) + " | SP: " + String.format("0x%02X", state.sp));
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
}