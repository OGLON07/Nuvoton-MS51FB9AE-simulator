package Main.UI;

public interface CoreCommunicator {
    /**
     * Sends execution commands (e.g., "STEP", "RUN", "RESET", "PAUSE")
     */
    void sendCommand(String command);

    /**
     * Sends custom machine code or program files to load into Core memory
     */
    void loadProgram(String programData);
}