package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class DebugClient {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Scanner scanner;

    public DebugClient(String host, int port) {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            scanner = new Scanner(System.in);

            // Thread che legge dal server
            new Thread(this::listenServer).start();

            // Thread che manda comandi al server
            writeToServer();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listenServer() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[SERVER] " + line);
            }
        } catch (IOException e) {
            System.out.println("Connessione chiusa dal server");
        }
    }

    private void writeToServer() {
        while (true) {
            String msg = scanner.nextLine();
            out.println(msg);
        }
    }

    public static void main(String[] args) {
        new DebugClient("localhost", 12345);
    }
}
