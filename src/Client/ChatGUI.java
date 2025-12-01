package Client;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ChatGUI extends JFrame {
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private Client client;

    // Nuovi componenti per la gestione dei pannelli
    private JPanel commandPanel; // Contenitore dei bottoni di comando (dinamico)
    private JPanel chatListPanel; // Contenitore verticale per i bottoni delle chat
    private JScrollPane chatListScrollPane;

    // Stato
    private int selectedChatId = -1;
    private final Map<Integer, JButton> chatButtons = new HashMap<>();

    // COSTRUTTORE
    public ChatGUI() {
        super("Semplice Chat Client");
        initializeGUI();
        setupClient();
    }

    /**
     * Inizializza tutti i componenti Swing e il layout.
     */
    private void initializeGUI() {
        // --- Area di visualizzazione dei messaggi ---
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane chatAreaScrollPane = new JScrollPane(chatArea);

        // --- Pannello di Input Messaggio ---
        messageField = new JTextField();
        sendButton = new JButton("Invia");

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        // --- Inizializza i pannelli di comando/lista ---
        commandPanel = new JPanel();
        chatListPanel = new JPanel();
        chatListPanel.setLayout(new BoxLayout(chatListPanel, BoxLayout.Y_AXIS));
        chatListScrollPane = new JScrollPane(chatListPanel);
        chatListScrollPane.setPreferredSize(new Dimension(150, 0));
        chatListScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // --- Layout Principale della Finestra ---

        // Suddividi l'area principale (ChatArea) e la barra laterale (ChatList)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatListScrollPane, chatAreaScrollPane);
        splitPane.setDividerLocation(150);

        // Pannello per contenere Input e Comandi (in basso)
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(commandPanel, BorderLayout.NORTH);
        southPanel.add(inputPanel, BorderLayout.SOUTH);

        // Aggiungi al contenitore principale
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(splitPane, BorderLayout.CENTER);
        contentPane.add(southPanel, BorderLayout.SOUTH);

        // --- Gestione Eventi ---
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        // I controlli di chat sono disabilitati finché non si effettua il login
        toggleChatControls(false);

        // Gestione chiusura finestra
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        // Configurazione Finestra
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);

        // Mostra il pannello di Login/Register all'avvio
        showLoginPanel();
    }

    /**
     * Avvia il client di rete.
     */
    private void setupClient() {
        try {
            // Indirizzo e porta del server (adatta se necessario)
            String serverAddress = "127.0.0.1";
            int serverPort = 12345;

            client = new Client(serverAddress, serverPort, this);
            client.start();
            appendMessage("Connesso al server " + serverAddress + ":" + serverPort);

        } catch (IOException e) {
            appendMessage("ERRORE: Impossibile connettersi al server. Avviare il server.");
            toggleChatControls(false);
        }
    }

    /**
     * Mostra i pulsanti Login e Register.
     */
    private void showLoginPanel() {
        commandPanel.removeAll();
        commandPanel.setLayout(new FlowLayout(FlowLayout.CENTER));

        JButton loginBtn = new JButton("Login");
        JButton registerBtn = new JButton("Register");

        loginBtn.addActionListener(e -> promptInput("login"));
        registerBtn.addActionListener(e -> promptInput("register"));

        commandPanel.add(loginBtn);
        commandPanel.add(registerBtn);
        commandPanel.revalidate();
        commandPanel.repaint();
    }

    /**
     * Chiede all'utente username/password tramite dialog e invia al server.
     */
    private void promptInput(String command) {
        String username = JOptionPane.showInputDialog(this, "Inserisci Username:", command.toUpperCase());
        if (username == null || username.trim().isEmpty()) return;

        String password = JOptionPane.showInputDialog(this, "Inserisci Password:", command.toUpperCase());
        if (password == null || password.trim().isEmpty()) return;

        // Il ClientHandler si aspetta 'login'/'register', seguito da username e password su righe separate
        client.send(command);
        client.send(username);
        client.send(password);
    }

    /**
     * Nasconde il Login panel e mostra i pulsanti di comando chat.
     */
    private void showCommandPanel() {
        commandPanel.removeAll();
        commandPanel.setLayout(new FlowLayout(FlowLayout.LEFT));

        // Aggiungo i bottoni per i comandi principali
        JButton listBtn = new JButton("/list");
        JButton newDmBtn = new JButton("/newdm");
        JButton newGroupBtn = new JButton("/newgroup");
        JButton addBtn = new JButton("/add"); // Aggiunto /add

        listBtn.addActionListener(e -> client.send("/list"));
        newDmBtn.addActionListener(e -> promptForNewChat("/newdm", "Username DM:"));
        newGroupBtn.addActionListener(e -> promptForNewChat("/newgroup", "Nome Gruppo:"));
        addBtn.addActionListener(e -> promptForAddUser()); // Gestione del comando /add

        commandPanel.add(listBtn);
        commandPanel.add(newDmBtn);
        commandPanel.add(newGroupBtn);
        commandPanel.add(addBtn);

        commandPanel.revalidate();
        commandPanel.repaint();
    }

    private void promptForNewChat(String command, String prompt) {
        String input = JOptionPane.showInputDialog(this, prompt, command.substring(1).toUpperCase());
        if (input != null && !input.trim().isEmpty()) {
            client.send(command + " " + input.trim());
        }
    }

    private void promptForAddUser() {
        try {
            String chatIdStr = JOptionPane.showInputDialog(this, "ID Chat Gruppo:");
            if (chatIdStr == null || chatIdStr.trim().isEmpty()) return;
            int chatId = Integer.parseInt(chatIdStr);

            String username = JOptionPane.showInputDialog(this, "Username da aggiungere:");
            if (username == null || username.trim().isEmpty()) return;

            client.send("/add " + chatId + " " + username.trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "ID Chat non valido.", "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Attiva/disattiva l'input e l'area chat.
     */
    private void toggleChatControls(boolean enabled) {
        sendButton.setEnabled(enabled);
        messageField.setEditable(enabled);
        messageField.setEnabled(enabled);
        chatListScrollPane.setEnabled(enabled);
    }

    /**
     * Aggiunge un bottone cliccabile per una specifica chat ID alla lista.
     */
    public void addChatButton(int id, String displayName) {
        if (chatButtons.containsKey(id)) {
            // Aggiorna solo il nome se la chat esiste già
            chatButtons.get(id).setText("Chat #" + id + " (" + displayName + ")");
            return;
        }

        JButton chatBtn = new JButton("Chat #" + id + " (" + displayName + ")");
        chatBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        chatBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, chatBtn.getMinimumSize().height));

        chatBtn.addActionListener(e -> selectChat(id));

        chatButtons.put(id, chatBtn);
        chatListPanel.add(chatBtn);
        chatListPanel.revalidate();
        chatListPanel.repaint();
    }

    /**
     * Gestisce il click sul bottone di una chat.
     */
    private void selectChat(int id) {
        if (selectedChatId != -1) {
            // Resetta lo stile del bottone precedentemente selezionato
            JButton prev = chatButtons.get(selectedChatId);
            if(prev != null) prev.setBackground(null);
        }

        selectedChatId = id;
        JButton current = chatButtons.get(id);
        current.setBackground(Color.LIGHT_GRAY);

        // Carica i messaggi della chat cliccata
        client.send("/open " + id);

        appendMessage("--- Chat selezionata: #" + id + " ---");
    }

    /**
     * Gestisce l'invio del messaggio (comando o chat)
     */
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty() || client == null) {
            return;
        }

        if (message.startsWith("/")) {
            // È un comando, invia direttamente
            client.send(message);
        } else if (selectedChatId != -1) {
            // Messaggio normale, aggiunge l'ID della chat selezionata
            String formattedMessage = selectedChatId + "|" + message;
            client.send(formattedMessage);
        } else {
            appendMessage("Seleziona una chat prima di inviare un messaggio.");
        }

        // Pulisce il campo di input
        messageField.setText("");
    }

    /**
     * Aggiunge un messaggio all'area di testo e gestisce i comandi di sistema dal server.
     */
    public void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            // 1. Logica di Transizione (se il login ha successo)
            if (message.startsWith("Benvenuto, ") && message.contains("! Digita /help")) {
                showCommandPanel();
                toggleChatControls(true);
            }

            // 2. Logica di Aggiornamento Chat List (dopo /list)
            if (message.startsWith(" • ID=")) {
                try {
                    String idPart = message.split("ID=")[1].split(" ")[0];
                    int id = Integer.parseInt(idPart);
                    String type = message.split("\\(")[1].split("\\)")[0];
                    addChatButton(id, type);
                } catch (Exception ignored) {
                    // ignora errori di parsing su righe non standard
                }
            }

            // 3. Logica Nuova Chat Creata o Utente Aggiunto
            if (message.contains("DM creata! ID=") || message.contains("Gruppo creato! ID=")) {
                try {
                    int id = Integer.parseInt(message.split("ID=")[1].trim());
                    // Dopo la creazione, forza un /list per popolare il bottone
                    client.send("/list");
                    // Seleziona automaticamente la nuova chat
                    // selectChat(id); // Questo è rischioso, meglio lasciar fare a /list
                } catch (Exception ignored) {}
            }

            // 4. Aggiungi il messaggio all'area di testo
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    // MAIN
    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatGUI::new);
    }
}