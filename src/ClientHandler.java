import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ChatManager chatManager;
    private UserManager userManager;
    private User user;
    private BufferedReader in;
    private PrintWriter out;
    private DBManager dbManager = new DBManager();

    private static AtomicInteger userCounter = new AtomicInteger(1);
    private static AtomicInteger messageCounter = new AtomicInteger(1);

    public ClientHandler(Socket socket, ChatManager chatManager, UserManager userManager) {
        this.socket = socket;
        this.chatManager = chatManager;
        this.userManager = userManager;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // --------------------
            // LOGIN / REGISTER ORIGINALI
            // --------------------
            while (true) {
                out.println("Benvenuto! Digita 'login' o 'register':");
                String command = in.readLine();
                if (command == null) continue;

                if (command.equalsIgnoreCase("login")) {
                    out.println("Username:");
                    String username = in.readLine();
                    out.println("Password:");
                    String password = in.readLine();

                    User u = userManager.getUser(username);
                    if (u != null && u.getPassword().equals(password)) {
                        user = u;
                        user.setStatus(true);
                        break;
                    } else {
                        out.println("Login fallito. Prova di nuovo.");
                    }

                } else if (command.equalsIgnoreCase("register")) {
                    out.println("Scegli un username:");
                    String username = in.readLine();
                    out.println("Scegli una password:");
                    String password = in.readLine();

                    if (userManager.getUser(username) == null) {
                        int id = userCounter.getAndIncrement();

                        // crea User anonimo come nel tuo codice originale
                        user = new User(id, username, password, true) {
                            // classe astratta anonima
                        };
                        userManager.register(user);
                        break;

                    } else {
                        out.println("Username già esistente. Prova di nuovo.");
                    }

                } else {
                    out.println("Comando non valido.");
                }
            }

            // collega user→handler
            userManager.setClientHandler(user, this);
            out.println("Benvenuto, " + user.getUsername() + "! Digita /help per comandi.");

            // --------------------
            // LOOP PRINCIPALE
            // --------------------
            String line;
            while ((line = in.readLine()) != null) {

                // comando /...
                if (line.startsWith("/")) {
                    handleCommand(line);
                    continue;
                }

                // messaggio normale
                String[] parts = line.split("\\|", 2);
                if (parts.length < 2) {
                    out.println("Formato messaggio invalido. Usa: ID_CHAT|messaggio");
                    continue;
                }

                int chatId = Integer.parseInt(parts[0]);
                String text = parts[1];

                Chat chat = chatManager.getChatByID(chatId);
                if (chat == null) {
                    out.println("Chat non trovata.");
                    continue;
                }

                Message msg = new Message(
                        String.valueOf(user.getID()),
                        text,
                        String.valueOf(messageCounter.getAndIncrement()),
                        String.valueOf(chatId)
                );

                chat.addMessage(msg);

                // invia a tutti i partecipanti
                for (User u : chat.getParticipants()) {
                    ClientHandler ch = userManager.getClientHandler(u);
                    if (ch != null) ch.sendMessage(chat, msg);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
            if (user != null) {
                user.setStatus(false);
                userManager.removeClientHandler(user);
            }
        }
    }

    // -----------------------------------------------------------
    //     NUOVI COMANDI DEL CLIENT
    // -----------------------------------------------------------
    private void handleCommand(String cmd) {
        try {

            if (cmd.equals("/help")) {
                out.println("""
                        --- COMANDI DISPONIBILI ---
                        /list                    → elenco chat
                        /open <id>              → mostra messaggi della chat
                        /newdm <username>       → crea chat diretta
                        /newgroup <nome>        → crea un gruppo
                        /add <chatID> <user>    → aggiunge utente al gruppo
                        """);
                return;
            }

            if (cmd.equals("/list")) {
                List<Chat> chats = dbManager.getAllChatsByUser(user.getID());
                out.println("Le tue chat:");
                for (Chat c : chats) {
                    out.println(" • ID=" + c.getID() + "  (" + c.getChatType() + ")");
                }
                return;
            }

            if (cmd.startsWith("/open ")) {
                int id = Integer.parseInt(cmd.split(" ")[1]);
                Chat c = chatManager.getChatByID(id);

                if (c == null) {
                    out.println("Chat non trovata.");
                    return;
                }

                out.println("Messaggi della chat " + id + ":");
                for (Message m : c.getMessaggi()) {
                    out.println("[" + m.getSenderID() + "] " + m.getContent());
                }
                return;
            }

            if (cmd.startsWith("/newdm ")) {
                String username = cmd.split(" ")[1];
                User u = userManager.getUser(username);

                if (u == null) {
                    out.println("Utente non trovato.");
                    return;
                }

                Chat chat = chatManager.createDM(user, u);
                out.println("DM creata! ID=" + chat.getID());
                return;
            }

            if (cmd.startsWith("/newgroup ")) {
                String name = cmd.substring(10);
                Chat c = chatManager.createGroup(name, user);

                out.println("Gruppo creato! ID=" + c.getID());
                return;
            }

            if (cmd.startsWith("/add ")) {
                String[] p = cmd.split(" ");
                int chatID = Integer.parseInt(p[1]);
                String username = p[2];

                Chat c = chatManager.getChatByID(chatID);
                if (c == null) {
                    out.println("Chat non trovata.");
                    return;
                }

                // VERIFICA CHE SIA UN GRUPPO
                if (!c.getChatType().equals("Gruppo")) {
                    out.println("La chat non è un gruppo.");
                    return;
                }

                User u = userManager.getUser(username);
                if (u == null) {
                    out.println("Utente non trovato.");
                    return;
                }

                if (c.getParticipants().contains(u)) {
                    out.println("Utente già nel gruppo.");
                    return;
                }

                c.addParticipant(u);
                dbManager.addUserToChat(chatID, u.getID());
                out.println("Utente aggiunto!");
                return;
            }

            out.println("Comando non valido. Usa /help.");

        } catch (Exception e) {
            out.println("Errore comando: " + e.getMessage());
        }
    }

    public void sendMessage(Chat chat, Message msg) {
        out.println("[" + chat.getID() + "] " + msg.getSenderID() + ": " + msg.getContent());
    }
}
