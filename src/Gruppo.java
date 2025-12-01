import java.util.ArrayList;
import java.util.List;

public class Gruppo extends Chat {
    private String nome;
    private List<User> participants;

    public Gruppo(int ID, String nome) {
        super(ID);
        this.nome = nome;
        this.participants = new ArrayList<>();
    }

    public String getNome(){
        return nome;
    }

    public void setNome(String nome){
        this.nome = nome;
    }

    @Override
    public List<User> getParticipants() {
        return participants;
    }

    @Override
    public String getChatType() {
        return "Gruppo";
    }

    public void addParticipant(User u) {
        if (!participants.contains(u)) {
            participants.add(u);
        }
    }

    public void removeParticipant(User u) {
        participants.remove(u);
    }
}