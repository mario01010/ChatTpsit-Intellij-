import java.util.List;

public class DM extends Chat {
    private List<User> participants;

    public DM(int ID, User u1, User u2) {
        super(ID);
        this.participants = List.of(u1, u2);
    }

    @Override
    public List<User> getParticipants() {
        return participants;
    }

    @Override
    public String getChatType() {
        return "DirectMessage";
    }
}
