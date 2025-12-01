package Client;
import java.io.*;
import java.net.Socket;

public class Client {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private ChatGUI gui;
    private String serverAddress;
    private int serverPort;

    public Client(String serverAddress, int serverPort, ChatGUI gui) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.gui = gui;
    }

    public void start() throws IOException {
        // Connessione al server
        socket = new Socket(serverAddress, serverPort);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        // Avvia il thread per la ricezione dei messaggi dal server
        new Thread(this::listenForServerMessages).start();
    }

    /**
     * Invia un comando o un messaggio al server.
     */
    public void send(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * Ciclo di ascolto per messaggi in arrivo dal server
     */
    private void listenForServerMessages() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                // Invia la riga ricevuta alla GUI per la visualizzazione
                gui.appendMessage(line);
            }
        } catch (IOException e) {
            // Connessione chiusa o errore
            gui.appendMessage("Disconnesso dal server.");
            // e.printStackTrace();
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                // Ignorato
            }
        }
    }
}