import java.time.LocalDateTime;


public class Message {
    private String senderID;
    private String content;
    private LocalDateTime timestamp;
    private String ID;
    private String chatID;

    public Message(String senderID, String content, String ID, String chatID) {
        this.senderID = senderID;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.ID = ID;
        this.chatID = chatID;
    }

    public String getSenderID() {
        return senderID;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    public String getID() {
        return ID;
    }
    public String getChatID() {
        return chatID;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setChatID(String chatID) {
        this.chatID = chatID;
    }

    public void setSenderID(String senderID) {
        this.senderID = senderID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }
}