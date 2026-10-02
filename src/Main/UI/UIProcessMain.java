package Main.UI;

import javax.swing.SwingUtilities;

public class UIProcessMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SimulatorUI ui = new SimulatorUI();

            // Attach Mock Communicator so buttons work in standalone mode
            CoreCommunicator mockCommunicator = new MockCoreCommunicator(ui);
            ui.setCommunicator(mockCommunicator);

            ui.setVisible(true);
            System.out.println("[UI Process] Started successfully in standalone mode.");
        });
    }
}