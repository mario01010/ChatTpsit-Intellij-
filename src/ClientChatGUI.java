import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


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
    private JLabel statusLabel;

    // Thread per ricevere messaggi
    private ExecutorService executor;

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
        leftPanel.setPreferredSize(new Dimension(250, 700));
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
                        // Formato: "ID=1  (DM)"
                        // Estrai solo il numero dell'ID
                        int startIndex = selected.indexOf("ID=");
                        if (startIndex != -1) {
                            // Trova lo spazio dopo l'ID
                            int endIndex = selected.indexOf(" ", startIndex + 3);
                            if (endIndex == -1) {
                                // Se non c'è spazio, prendi fino alla parentesi
                                endIndex = selected.indexOf("(", startIndex + 3);
                            }

                            if (endIndex != -1) {
                                String idStr = selected.substring(startIndex + 3, endIndex).trim();
                                int chatId = Integer.parseInt(idStr);
                                openChat(chatId);
                            } else {
                                // Fallback: prendi tutto dopo "ID="
                                String idStr = selected.substring(startIndex + 3).trim();
                                // Rimuovi eventuali parentesi alla fine
                                if (idStr.contains("(")) {
                                    idStr = idStr.substring(0, idStr.indexOf("(")).trim();
                                }
                                int chatId = Integer.parseInt(idStr);
                                openChat(chatId);
                            }
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
        JPanel chatButtonsPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        createDMButton = new JButton("Nuova DM");
        createGroupButton = new JButton("Nuovo Gruppo");
        createDMButton.addActionListener(e -> createDM());
        createGroupButton.addActionListener(e -> createGroup());
        chatButtonsPanel.add(createDMButton);
        chatButtonsPanel.add(createGroupButton);
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

    private void showLoginDialog() {
        // Crea un JDialog MODALE
        JDialog loginDialog = new JDialog(this, "Login/Registrazione", true);
        loginDialog.setLayout(new BorderLayout());
        loginDialog.setSize(400, 200);
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

        // Listener del bottone Connetti
        connectButton.addActionListener(e -> {

            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            String action = (String) actionCombo.getSelectedItem();

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(loginDialog, "Username e password obbligatori");
                return;
            }

            // Thread separato per evitare il blocco della GUI
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
                    } catch (IOException ignored) {}
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
            // Leggi messaggio di benvenuto
            String welcomeMessage = in.readLine();

            // Invia comando login/register
            out.println(action);

            // Prompt username/password
            in.readLine();
            out.println(username);
            in.readLine();
            out.println(password);

            // Leggi risposta finale
            String response = in.readLine();

            if (response != null && response.contains("Benvenuto")) {
                currentUsername = username;
                statusLabel.setText("Connesso come: " + username);

                // Avvia thread di ricezione **solo ora**
                executor = Executors.newSingleThreadExecutor();
                executor.submit(this::receiveMessages);

                // Carica chat e utenti online
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
    }

    private void receiveMessages() {
        try {
            StringBuilder buffer = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null) {
                System.out.println("DEBUG Ricevuto: " + line); // Debug

                final String message = line;
                SwingUtilities.invokeLater(() -> {
                    if (message.startsWith("[") && message.contains("]")) {
                        // Gestione messaggio in chat
                        try {
                            String[] parts = message.split("] ", 2);
                            String chatInfo = parts[0].substring(1);
                            String messageContent = parts[1];

                            int chatId = Integer.parseInt(chatInfo);
                            if (chatId == currentChatId) {
                                chatArea.append(messageContent + "\n");
                                chatArea.setCaretPosition(chatArea.getDocument().getLength());
                            } else {
                                // Mostra notifica per altre chat
                                System.out.println("Nuovo messaggio in chat " + chatId + ": " + messageContent);
                            }
                        } catch (Exception e) {
                            chatArea.append(message + "\n");
                        }
                    } else if (message.startsWith("Le tue chat:")) {
                        handleChatListResponse(message);
                    } else if (message.contains("• ID=")) {
                        if (message.startsWith("[SERVER] ")) {
                            String chatInfo = message.substring(9).trim();
                            if (chatInfo.startsWith("Le tue chat:")) {
                                handleChatListResponse(chatInfo);
                            } else if (chatInfo.startsWith("• ID=")) {
                                handleChatListResponse("Le tue chat:\n" + chatInfo);
                            }
                        } else if (message.startsWith("• ID=")) {
                            handleChatListResponse("Le tue chat:\n" + message);
                        }
                    } else if (message.startsWith("Messaggi della chat")) {
                        handleChatMessagesResponse(message);
                    } else if (message.startsWith("ID: ")) {
                        // Gestisci la risposta quando crei una nuova chat
                        JOptionPane.showMessageDialog(this, message);
                        loadChats(); // Ricarica la lista delle chat
                    } else {
                        // Mostra altri messaggi generici
                        chatArea.append("[SERVER] " + message + "\n");
                    }
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
        String[] lines = response.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("• ")) {
                chatListModel.addElement(line.substring(2));
            }
        }
        if (chatListModel.isEmpty()) {
            chatListModel.addElement("Nessuna chat disponibile");
            chatList.setEnabled(false);
        } else {
            chatList.setEnabled(true);
        }
    }
    private void handleChatMessagesResponse(String response) {
        String[] lines = response.split("\n");
        chatArea.setText("");

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (!line.trim().isEmpty()) {
                String[] parts = line.split("] ", 2);
                if (parts.length == 2) {
                    String senderId = parts[0].substring(1);
                    String message = parts[1];

                    if (senderId.equals(String.valueOf(currentUserId))) {
                        chatArea.append("tu: " + message + "\n");
                    } else {
                        chatArea.append(senderId + ": " + message + "\n");
                    }
                } else {
                    chatArea.append(line + "\n");
                }
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
        onlineUsersModel.addElement(currentUsername + " (tu)");
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

    private void startChatListUpdater() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            if (out != null) {
                out.println("/list");
            }
        }, 0, 5, TimeUnit.SECONDS); // aggiorna ogni 5 secondi
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
            if(scheduler != null) {
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