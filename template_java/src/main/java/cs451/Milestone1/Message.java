package cs451.Milestone1;

public class Message {
    private int initiatorId;
    private String content;

    // Constructors
    public Message() {}

    public Message(int initiatorId, String content) {
        this.initiatorId = initiatorId;
        this.content = content;
    }

    public int getInitiatorId() {
        return initiatorId;
    }

    public void setInitiatorId(int senderId) {
        this.initiatorId = senderId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != this.getClass()) {
            return false;
        }
        Message other = (Message) obj;
        return this.initiatorId == other.initiatorId && this.content.equals(other.content);
    }

    @Override
    public int hashCode() {
        return this.initiatorId + this.content.hashCode();
    }    

    @Override
    public String toString() {
        return "Message{" +
                "senderId=" + initiatorId +
                ", content='" + content + '\'' +
                '}';
    }
}
