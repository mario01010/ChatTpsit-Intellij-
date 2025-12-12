import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Client GUI per la chat.
 * - Richiede periodicamente /list al server (startChatListUpdater dopo login)
 * - Riceve e bufferizza la risposta multi-linea "Le tue chat:"
 * - Pulsante per creare DM, gruppo e per aggiungere utenti ai gruppi:
 *     invia: /adduser <chatId> <username>
 *
 * Nota: il server deve supportare i comandi:
 *   /list
 *   /open <id>
 *   /newdm <username>
 *   /newgroup <nome>
 *   /adduser <chatId> <username>
 *
 */
public class ClientChatGUI extends JFrame {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String currentUsername;
    private int currentUserId;
    private int currentChatId = -1;
    private ScheduledExecutorService scheduler;

    private JList<String> chatList;
    private DefaultListModel<String> chatListModel;
    private JList<String> onlineUsersList;
    private DefaultListModel<String> onlineUsersModel;
    private JTextArea chatArea;
    private JTextArea messageArea;
    private JButton sendButton;
    private JButton createGroupButton;
    private JButton createDMButton;
    private JButton refreshButton;
    private JButton loginButton;
    private JButton addUserButton;
    private JLabel statusLabel;

    // Thread per ricevere messaggi
    private ExecutorService executor;

    // Per raccogliere la lista delle chat
    private boolean readingChatList = false;
    private StringBuilder chatListBuffer = new StringBuilder();

    public ClientChatGUI() {
        setTitle("Chat Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        // Inizializza modelli
        chatListModel = new DefaultListModel<>();
        onlineUsersModel = new DefaultListModel<>();

        // Setup GUI
        setupGUI();

        // Disabilita tutto fino al login
        setChatComponentsEnabled(false);

        setVisible(true);

        // Mostra dialog di login
        showLoginDialog();
    }

    private void setupGUI() {
        // Panel principale con BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout());

        // TOP PANEL - Login button
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        loginButton = new JButton("Login/Registrati");
        loginButton.addActionListener(e -> showLoginDialog());
        topPanel.add(loginButton);

        statusLabel = new JLabel("Disconnesso - Effettua il login");
        topPanel.add(statusLabel);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // LEFT PANEL - Chat e Utenti Online
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(300, 700));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Chat & Utenti"));

        // Chat list
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBorder(BorderFactory.createTitledBorder("Le tue Chat"));
        chatList = new JList<>(chatListModel);
        chatList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chatList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && chatList.getSelectedIndex() != -1) {
                String selected = chatList.getSelectedValue();
                if (selected != null && !selected.equals("Nessuna chat disponibile")) {
                    try {
                        // Formato atteso: "ID=1  (DM)" oppure "1 - NomeGruppo (Gruppo)"
                        // Troviamo "ID=" oppure il primo numero
                        int chatId = parseChatIdFromListEntry(selected);
                        if (chatId != -1) {
                            openChat(chatId);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(this,
                                "Errore nell'aprire la chat: " + ex.getMessage());
                    }
                }
            }
        });

        JScrollPane chatScroll = new JScrollPane(chatList);
        chatPanel.add(chatScroll, BorderLayout.CENTER);

        // Pulsanti per nuove chat
        JPanel chatButtonsPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        createDMButton = new JButton("Nuova DM");
        createGroupButton = new JButton("Nuovo Gruppo");
        addUserButton = new JButton("Aggiungi Utente al Gruppo");
        createDMButton.addActionListener(e -> createDM());
        createGroupButton.addActionListener(e -> createGroup());
        addUserButton.addActionListener(e -> addUserToSelectedGroup());
        chatButtonsPanel.add(createDMButton);
        chatButtonsPanel.add(createGroupButton);
        chatButtonsPanel.add(addUserButton);
        chatPanel.add(chatButtonsPanel, BorderLayout.SOUTH);

        // Online users list
        JPanel onlinePanel = new JPanel(new BorderLayout());
        onlinePanel.setBorder(BorderFactory.createTitledBorder("Utenti Online"));
        onlineUsersList = new JList<>(onlineUsersModel);
        JScrollPane onlineScroll = new JScrollPane(onlineUsersList);
        onlinePanel.add(onlineScroll, BorderLayout.CENTER);

        refreshButton = new JButton("Aggiorna");
        refreshButton.addActionListener(e -> refreshOnlineUsers());
        onlinePanel.add(refreshButton, BorderLayout.SOUTH);

        leftPanel.add(chatPanel, BorderLayout.NORTH);
        leftPanel.add(onlinePanel, BorderLayout.CENTER);

        // CENTER PANEL - Chat messages
        JPanel centerPanel = new JPanel(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(BorderFactory.createTitledBorder("Messaggi"));
        centerPanel.add(chatScrollPane, BorderLayout.CENTER);

        // BOTTOM PANEL - Input messaggio
        JPanel bottomPanel = new JPanel(new BorderLayout());
        messageArea = new JTextArea(3, 20);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        JScrollPane messageScroll = new JScrollPane(messageArea);

        sendButton = new JButton("Invia");
        sendButton.addActionListener(e -> sendMessage());

        // Invio con CTRL+ENTER
        messageArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessage();
                }
            }
        });

        bottomPanel.add(messageScroll, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        centerPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Aggiungi tutto al main panel
        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        add(mainPanel);
    }

    private int parseChatIdFromListEntry(String selected) {
        if (selected == null || selected.equals("Nessuna chat disponibile")) {
            return -1;
        }

        // Nuovo formato: "id - nome (tipo)"
        try {
            // Prende tutto prima del primo spazio o "-"
            String[] parts = selected.split("\\s+");
            if (parts.length > 0) {
                return Integer.parseInt(parts[0].trim());
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private void showLoginDialog() {
            JDialog loginDialog = new JDialog(this, "Login/Registrazione", true);
            loginDialog.setLayout(new BorderLayout());
            loginDialog.setSize(420, 220);
            loginDialog.setLocationRelativeTo(this);

            JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
            JTextField usernameField = new JTextField();
            JPasswordField passwordField = new JPasswordField();
            JComboBox<String> actionCombo = new JComboBox<>(new String[]{"Login", "Register"});
            JButton connectButton = new JButton("Connetti");

            panel.add(new JLabel("Azione:"));
            panel.add(actionCombo);
            panel.add(new JLabel("Username:"));
            panel.add(usernameField);
            panel.add(new JLabel("Password:"));
            panel.add(passwordField);
            panel.add(new JLabel(""));
            panel.add(connectButton);

            loginDialog.add(panel, BorderLayout.CENTER);

            // Pulsante Annulla
            JButton cancelButton = new JButton("Annulla");
            cancelButton.addActionListener(e -> loginDialog.dispose());
            JPanel buttonPanel = new JPanel();
            buttonPanel.add(cancelButton);
            loginDialog.add(buttonPanel, BorderLayout.SOUTH);

            connectButton.addActionListener(e -> {

                String username = usernameField.getText().trim();
                String password = new String(passwordField.getPassword());
                String action = (String) actionCombo.getSelectedItem();

                if (username.isEmpty() || password.isEmpty()) {
                    JOptionPane.showMessageDialog(loginDialog, "Username e password obbligatori");
                    return;
                }

                new Thread(() -> {

                    if (!connectToServer()) {
                        return;
                    }

                if (performLogin(action.toLowerCase(), username, password)) {

                    SwingUtilities.invokeLater(() -> {
                        loginDialog.dispose();
                        setChatComponentsEnabled(true);
                        loginButton.setEnabled(false);
                    });

                } else {
                    try {
                        if (socket != null) socket.close();
                    } catch (IOException ignored) {
                    }
                }

            }).start();
        });

        loginDialog.setVisible(true);
    }

    private boolean connectToServer() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Errore di connessione al server: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private boolean performLogin(String action, String username, String password) {
        try {
            String welcomeMessage = in.readLine();

            // Invia comando login/register
            out.println(action);

            // Prompt username/password (server side prompts)
            String prompt1 = in.readLine();
            if (prompt1 == null) prompt1 = "";
            out.println(username);
            String prompt2 = in.readLine();
            if (prompt2 == null) prompt2 = "";
            out.println(password);

            // Leggi risposta finale
            String response = in.readLine();

            if (response != null && response.contains("Benvenuto")) {
                currentUsername = username;
                statusLabel.setText("Connesso come: " + username);

                // Avvia thread di ricezione **solo ora**
                executor = Executors.newSingleThreadExecutor();
                executor.submit(this::receiveMessages);

                // Avvia updater lista chat periodico
                startChatListUpdater();

                // Carica chat e utenti online subito
                SwingUtilities.invokeLater(() -> {
                    loadChats();
                    refreshOnlineUsers();
                });

                return true;
            } else {
                JOptionPane.showMessageDialog(this, response != null ? response : "Login fallito");
                return false;
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Errore durante il login: " + e.getMessage());
            return false;
        }
    }

    private void setChatComponentsEnabled(boolean enabled) {
        chatList.setEnabled(enabled);
        messageArea.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        createDMButton.setEnabled(enabled);
        createGroupButton.setEnabled(enabled);
        refreshButton.setEnabled(enabled);
        onlineUsersList.setEnabled(enabled);
        addUserButton.setEnabled(enabled);
    }

    private void receiveMessages() {
        try {
            String line;

            while ((line = in.readLine()) != null) {
                System.out.println("DEBUG Ricevuto: " + line);

                final String message = line;

                SwingUtilities.invokeLater(() -> {
                    // Gestione lista chat
                    if (message.startsWith("DM:") || message.startsWith("GRUPPO:")) {
                        String[] parts = message.split(":");
                        if (parts.length >= 3) {
                            String tipo = parts[0]; // "DM" o "GRUPPO"
                            String id = parts[1];
                            String nome = parts[2];

                            // Formatta per la visualizzazione
                            String displayText = id + " - " + nome + " (" + tipo + ")";

                            // Evita duplicati
                            boolean exists = false;
                            for (int i = 0; i < chatListModel.size(); i++) {
                                String existing = chatListModel.getElementAt(i);
                                if (existing.startsWith(id + " - ")) {
                                    exists = true;
                                    break;
                                }
                            }

                            if (!exists) {
                                chatListModel.addElement(displayText);
                            }
                        }
                        return;
                    }

                    // Fine lista delle chat
                    if (message.equals("LIST_END")) {
                        if (chatListModel.isEmpty()) {
                            chatListModel.addElement("Nessuna chat disponibile");
                            chatList.setEnabled(false);
                        } else {
                            chatList.setEnabled(true);
                        }
                        return;
                    }

                    // Messaggi normali
                    if (message.startsWith("[") && message.contains("]")) {
                        try {
                            String[] parts = message.split("] ", 2);
                            String chatInfo = parts[0].substring(1);
                            String messageContent = parts[1];
                            int chatId = Integer.parseInt(chatInfo);
                            if (chatId == currentChatId) {
                                chatArea.append(messageContent + "\n");
                                chatArea.setCaretPosition(chatArea.getDocument().getLength());
                            }
                        } catch (Exception e) {
                            chatArea.append(message + "\n");
                        }
                        return;
                    }

                    // Altri messaggi
                    chatArea.append(message + "\n");
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                });
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Disconnesso dal server");
                JOptionPane.showMessageDialog(this, "Connessione al server persa");
                setChatComponentsEnabled(false);
                loginButton.setEnabled(true);
            });
        }
    }

    private void handleChatListResponse(String response) {
        chatListModel.clear();
        if (response == null || response.isEmpty()) {
            chatListModel.addElement("Nessuna chat disponibile");
            chatList.setEnabled(false);
            return;
        }

        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line == null) continue;
            line = line.trim();
            if (line.isEmpty()) continue;

            // Ignora intestazioni duplicate
            if (line.equalsIgnoreCase("Le tue chat:")) continue;

            // Rimuovi eventuale "• "
            if (line.startsWith("• ")) line = line.substring(2);

            chatListModel.addElement(line);
        }

        if (chatListModel.isEmpty()) {
            chatListModel.addElement("Nessuna chat disponibile");
            chatList.setEnabled(false);
        } else {
            chatList.setEnabled(true);
        }
    }

    private void handleChatMessagesResponse(String response) {
        // Attende una risposta multilinea del server come:
        // Messaggi della chat X:
        // [1] user1: ciao
        // [1] user2: hello
        String[] lines = response.split("\n");
        chatArea.setText("");

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (!line.trim().isEmpty()) {
                // Cerchiamo il formato "[id] sender: content"
                if (line.startsWith("[")) {
                    String[] parts = line.split("] ", 2);
                    if (parts.length == 2) {
                        String left = parts[0];
                        String content = parts[1];
                        // left = "[1" -> sender id inside content if server formattato diversamente
                        chatArea.append(content + "\n");
                        continue;
                    }
                }
                chatArea.append(line + "\n");
            }
        }

        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void loadChats() {
        if (out != null) {
            out.println("/list");
        }
    }

    private void refreshOnlineUsers() {
        onlineUsersModel.clear();
        // TODO: Implementa comando reale per ottenere utenti online
        if (currentUsername != null) {
            onlineUsersModel.addElement(currentUsername + " (tu)");
        }
    }

    private void openChat(int chatId) {
        currentChatId = chatId;
        if (out != null) {
            out.println("/open " + chatId);
        }
    }

    private void sendMessage() {
        String message = messageArea.getText().trim();
        if (!message.isEmpty() && currentChatId != -1 && out != null) {
            out.println(currentChatId + "|" + message);
            chatArea.append("tu: " + message + "\n");
            messageArea.setText("");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        } else if (currentChatId == -1) {
            JOptionPane.showMessageDialog(this, "Seleziona prima una chat dalla lista.");
        }
    }

    private void createDM() {
        String username = JOptionPane.showInputDialog(this, "Inserisci username per DM:");
        if (username != null && !username.trim().isEmpty() && out != null) {
            out.println("/newdm " + username.trim());
        }
    }

    private void createGroup() {
        String groupName = JOptionPane.showInputDialog(this, "Inserisci nome gruppo:");
        if (groupName != null && !groupName.trim().isEmpty() && out != null) {
            out.println("/newgroup " + groupName.trim());
        }
    }

    private void addUserToSelectedGroup() {
        // prende la chat selezionata e invia /adduser <chatId> <username>
        String selected = chatList.getSelectedValue();
        if (selected == null || selected.equals("Nessuna chat disponibile")) {
            JOptionPane.showMessageDialog(this, "Seleziona prima un gruppo dalla lista delle chat.");
            return;
        }

        int chatId = parseChatIdFromListEntry(selected);
        if (chatId == -1) {
            JOptionPane.showMessageDialog(this, "Impossibile determinare l'ID della chat selezionata.");
            return;
        }

        String usernameToAdd = JOptionPane.showInputDialog(this, "Inserisci username da aggiungere al gruppo (solo 1):");
        if (usernameToAdd == null) return; // annullato
        usernameToAdd = usernameToAdd.trim();
        if (usernameToAdd.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username vuoto.");
            return;
        }

        if (out != null) {
            out.println("/adduser " + chatId + " " + usernameToAdd);
            // attendiamo risposta dal server che verrà mostrata nella chat area quando arriva
        }
    }

    private void startChatListUpdater() {
        // Avvia uno scheduler che chiede /list ogni 5 secondi
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            if (out != null) {
                out.println("/list");
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        try {
            if (executor != null) {
                executor.shutdownNow();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new ClientChatGUI();
        });
    }
}
