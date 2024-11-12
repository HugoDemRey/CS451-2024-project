package cs451.Milestone1;

public class Message {
    private int senderId;
    private String content;

    // Constructors
    public Message() {}

    public Message(int senderId, String content) {
        this.senderId = senderId;
        this.content = content;
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
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
        return this.senderId == other.senderId && this.content.equals(other.content);
    }

    @Override
    public String toString() {
        return "Message{" +
                ", senderId=" + senderId +
                ", content='" + content + '\'' +
                '}';
    }
}
