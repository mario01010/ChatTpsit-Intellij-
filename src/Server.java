import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private static final int PORT = 12345; // scegli una porta libera
    private ChatManager chatManager;
    private UserManager userManager;

    public Server() {
        chatManager = new ChatManager();
        userManager = new UserManager();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server avviato sulla porta " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nuovo client connesso: " + clientSocket.getInetAddress());

                // crea e avvia un thread ClientHandler
                ClientHandler handler = new ClientHandler(clientSocket, chatManager, userManager);
                new Thread(handler).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
}
