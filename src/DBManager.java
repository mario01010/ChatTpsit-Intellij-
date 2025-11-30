import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DBManager {
    private static final String DB_URL = "jdbc:postgresql://pg-3d352acb-chattpsit.g.aivencloud.com:15477/defaultdb?ssl=require&user=avnadmin&password=[Password]";
    private Connection conn;

    public DBManager() {
        try {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(DB_URL);
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    // Inserimento utente
    public int addUser(String username, String password, int status) throws SQLException {
        String sql = "INSERT INTO utente (username, password, status) VALUES (?, ?, ?) RETURNING id_utente";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, username);
        ps.setString(2, password);
        ps.setInt(3, status);
        ResultSet rs = ps.executeQuery();
        int id = -1;
        if (rs.next()) id = rs.getInt("id_utente");
        rs.close();
        ps.close();
        return id;
    }

    // Inserimento chat
    public int addChat(String tipo) throws SQLException {
        String sql = "INSERT INTO chat (tipo) VALUES (?) RETURNING id_chat";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, tipo);
        ResultSet rs = ps.executeQuery();
        int id = -1;
        if (rs.next()) id = rs.getInt("id_chat");
        rs.close();
        ps.close();
        return id;
    }

    // Aggiungi utente a chat
    public void addUserToChat(int idChat, int idUtente) throws SQLException {
        String sql = "INSERT INTO chatutente (id_chat, id_utente) VALUES (?, ?) ON CONFLICT DO NOTHING";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, idChat);
        ps.setInt(2, idUtente);
        ps.executeUpdate();
        ps.close();
    }

    // Inserimento messaggio
    public int addMessage(int idChat, int idUtente, String content) throws SQLException {
        String sql = "INSERT INTO messaggio (content, id_chat, id_utente) VALUES (?, ?, ?) RETURNING id_messaggio";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, content);
        ps.setInt(2, idChat);
        ps.setInt(3, idUtente);
        ResultSet rs = ps.executeQuery();
        int id = -1;
        if (rs.next()) id = rs.getInt("id_messaggio");
        rs.close();
        ps.close();
        return id;
    }

    // Inserimento gruppo
    public void addGroup(int idChat, String nome) throws SQLException {
        String sql = "INSERT INTO gruppo (id_chat, nome) VALUES (?, ?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, idChat);
        ps.setString(2, nome);
        ps.executeUpdate();
        ps.close();
    }

    // Lettura messaggi di una chat (per output semplice)
    public List<String> getMessages(int idChat) throws SQLException {
        String sql = "SELECT m.id_messaggio, m.content, u.username, m.time " +
                "FROM messaggio m " +
                "JOIN utente u ON m.id_utente = u.id_utente " +
                "WHERE m.id_chat = ? " +
                "ORDER BY m.time ASC";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, idChat);
        ResultSet rs = ps.executeQuery();

        List<String> messages = new ArrayList<>();
        while (rs.next()) {
            messages.add(rs.getInt("id_messaggio") + " | " +
                    rs.getString("username") + ": " +
                    rs.getString("content") + " (" +
                    rs.getTimestamp("time") + ")");
        }
        rs.close();
        ps.close();
        return messages;
    }

    // Recupera tutti gli utenti
    public List<User> getAllUsers() throws SQLException {
        String sql = "SELECT id_utente, username, password, status FROM utente";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        List<User> users = new ArrayList<>();
        while (rs.next()) {
            int id = rs.getInt("id_utente");
            String username = rs.getString("username");
            String password = rs.getString("password");
            boolean status = rs.getInt("status") == 1;
            users.add(new User(id, username, password, status));
        }
        rs.close();
        stmt.close();
        return users;
    }

    // Recupera tutti gli utenti in una chat
    private List<User> getUsersInChat(int chatId) throws SQLException {
        String sql = "SELECT u.id_utente, u.username, u.password, u.status " +
                "FROM utente u " +
                "JOIN chatutente cu ON u.id_utente = cu.id_utente " +
                "WHERE cu.id_chat = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, chatId);
        ResultSet rs = ps.executeQuery();

        List<User> users = new ArrayList<>();
        while (rs.next()) {
            int id = rs.getInt("id_utente");
            String username = rs.getString("username");
            String password = rs.getString("password");
            boolean status = rs.getInt("status") == 1;
            users.add(new User(id, username, password, status));
        }
        rs.close();
        ps.close();
        return users;
    }

    // Recupera i messaggi di una chat
    private List<Message> getMessagesForChat(int chatId) throws SQLException {
        String sql = "SELECT id_messaggio, content, id_utente, time " +
                "FROM messaggio WHERE id_chat = ? ORDER BY time ASC";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, chatId);
        ResultSet rs = ps.executeQuery();

        List<Message> messages = new ArrayList<>();
        while (rs.next()) {
            int messageId = rs.getInt("id_messaggio");
            String content = rs.getString("content");
            int senderId = rs.getInt("id_utente");
            Message message = new Message(String.valueOf(senderId), content, String.valueOf(messageId), String.valueOf(chatId));
            messages.add(message);
        }

        rs.close();
        ps.close();
        return messages;
    }

    // Recupera tutte le chat (DM e Gruppo)
    public List<Chat> getAllChats() throws SQLException {
        List<Chat> chats = new ArrayList<>();
        String chatSql = "SELECT tipo, id_chat FROM chat";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(chatSql);

        while (rs.next()) {
            int chatId = rs.getInt("id_chat");
            String tipo = rs.getString("tipo");

            if (tipo.equalsIgnoreCase("DirectMessage")) {
                List<User> dmUsers = getUsersInChat(chatId);
                if (dmUsers.size() == 2) {
                    DM dm = new DM(chatId, dmUsers.get(0), dmUsers.get(1));
                    dm.getMessaggi().addAll(getMessagesForChat(chatId));
                    chats.add(dm);
                }
            } else if (tipo.equalsIgnoreCase("Gruppo")) {
                String groupSql = "SELECT nome FROM gruppo WHERE id_chat = ?";
                PreparedStatement groupPs = conn.prepareStatement(groupSql);
                groupPs.setInt(1, chatId);
                ResultSet groupRs = groupPs.executeQuery();

                if (groupRs.next()) {
                    String nomeGruppo = groupRs.getString("nome");
                    Gruppo gruppo = new Gruppo(chatId, nomeGruppo);
                    gruppo.getParticipants().addAll(getUsersInChat(chatId));
                    gruppo.getMessaggi().addAll(getMessagesForChat(chatId));
                    chats.add(gruppo);
                }
                groupRs.close();
                groupPs.close();
            }
        }

        rs.close();
        stmt.close();
        return chats;
    }

    public List<Chat> getAllChatsByUser(int userId) throws SQLException {
        List<Chat> chats = new ArrayList<>();
        String sql = "SELECT c.id_chat, c.tipo " +
                "FROM chat c " +
                "JOIN chatutente cu ON cu.id_chat = c.id_chat " +
                "WHERE cu.id_utente = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int chatId = rs.getInt("id_chat");
                    String tipo = rs.getString("tipo");

                    if (tipo.equalsIgnoreCase("DirectMessage") || tipo.equalsIgnoreCase("DM")) {
                        List<User> dmUsers = getUsersInChat(chatId);
                        if (dmUsers.size() == 2) {
                            DM dm = new DM(chatId, dmUsers.get(0), dmUsers.get(1));
                            dm.getMessaggi().addAll(getMessagesForChat(chatId));
                            chats.add(dm);
                        }
                    } else if (tipo.equalsIgnoreCase("Gruppo")) {
                        String groupSql = "SELECT nome FROM gruppo WHERE id_chat = ?";
                        try (PreparedStatement groupPs = conn.prepareStatement(groupSql)) {
                            groupPs.setInt(1, chatId);
                            try (ResultSet groupRs = groupPs.executeQuery()) {
                                if (groupRs.next()) {
                                    String nomeGruppo = groupRs.getString("nome");
                                    Gruppo gruppo = new Gruppo(chatId, nomeGruppo);
                                    gruppo.getParticipants().addAll(getUsersInChat(chatId));
                                    gruppo.getMessaggi().addAll(getMessagesForChat(chatId));
                                    chats.add(gruppo);
                                }
                            }
                        }
                    }
                }
            }
        }
        return chats;
    }



    // Chiudi connessione
    public void close() throws SQLException {
        if (conn != null) conn.close();
    }
}
