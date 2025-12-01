import java.util.Map;
import java.util.HashMap;
import java.sql.SQLException;

public class UserManager {
    private Map<String, User> userList;
    private Map<String, User> online;
    private Map<User, ClientHandler> clientHandlers;
    private DBManager dbManager;

    public UserManager() {
        userList = new HashMap<>();
        online = new HashMap<>();
        clientHandlers = new HashMap<>();
        dbManager = new DBManager();

        // carica utenti dal DB all'avvio
        loadUsersFromDB();
    }

    private void loadUsersFromDB() {
        try {
            for (User u : dbManager.getAllUsers()) {
                userList.put(u.getUsername(), u);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Errore nel caricamento utenti dal database.");
        }
    }

    public boolean register(User u) {
        if (userList.containsKey(u.getUsername())) {
            return false;
        } else {
            userList.put(u.getUsername(), u);
            try {
                dbManager.addUser(u.getUsername(), u.getPassword(), u.getStatus() ? 1 : 0);
            } catch (SQLException e) {
                e.printStackTrace();
                System.out.println("Errore durante la registrazione dell'utente nel database.");
            }
            return true;
        }
    }

    public User login(String username, String password) {
        User u = userList.get(username);
        if (u != null && u.getPassword().equals(password)) {
            u.setStatus(true);
            online.put(username, u);
            return u;
        } else {
            return null;
        }
    }

    public boolean logout(User u) {
        if (u != null && u.getStatus()) {
            u.setStatus(false);
            online.remove(u.getUsername());
            clientHandlers.remove(u);
            return true;
        } else {
            return false;
        }
    }

    public User getUser(String username) {
        return userList.get(username);
    }

    public User getOnline(String username) {
        return online.get(username);
    }

    public void setClientHandler(User user, ClientHandler handler) {
        clientHandlers.put(user, handler);
    }

    public ClientHandler getClientHandler(User user) {
        return clientHandlers.get(user);
    }

    public boolean isOnline(User user) {
        return online.containsKey(user.getUsername());
    }

    public boolean removeClientHandler(User user) {
        return clientHandlers.remove(user) != null;
    }
}

