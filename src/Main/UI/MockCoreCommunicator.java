package Main.UI;

public class MockCoreCommunicator implements CoreCommunicator {
    private final SimulatorUI ui;
    private int mockPC = 0x0000;
    private int mockSP = 0x07;
    private int mockACC = 0x00;

    public MockCoreCommunicator(SimulatorUI ui) {
        this.ui = ui;
    }

    @Override
    public void sendCommand(String command) {
        System.out.println("[Mock Core] Command received from UI: " + command);

        CPUState mockState = new CPUState();

        if ("STEP".equalsIgnoreCase(command)) {
            mockPC += 2;
            mockSP += 1;
            mockACC = (mockACC + 0x05) & 0xFF;
            mockState.lastInstruction = String.format("Executed STEP -> PC: 0x%04X, SP: 0x%02X", mockPC, mockSP);
        } else if ("RESET".equalsIgnoreCase(command)) {
            mockPC = 0x0000;
            mockSP = 0x07;
            mockACC = 0x00;
            mockState.lastInstruction = "System Reset completed";
        } else {
            mockState.lastInstruction = "Executed command: " + command;
        }

        mockState.pc = mockPC;
        mockState.sp = mockSP;
        mockState.acc = mockACC;

        // Dummy RAM and Stack values to test rendering
        mockState.ram[0] = (byte) 0xAA;
        mockState.ram[1] = (byte) 0xBB;
        for (int i = 0; i < mockState.stackData.length; i++) {
            mockState.stackData[i] = String.format("0x%02X", 0x08 + i);
        }

        // Pass simulated state update back to UI
        ui.onStateReceived(mockState);
    }

    @Override
    public void loadProgram(String programData) {
        System.out.println("[Mock Core] Program loaded into memory");
    }
}
