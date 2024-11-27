package cs451.Milestone1;

public class Message implements Comparable<Message>{
    private int signature;
    private String content;

    // Constructors
    public Message() {}

    public Message(int signature, String content) {
        this.signature = signature;
        this.content = content;
    }

    public int getSignature() {
        return signature;
    }

    public void setSignature(int senderId) {
        this.signature = senderId;
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
        return this.signature == other.signature && this.content.equals(other.content);
    }

    @Override
    public int hashCode() {
        return this.content.hashCode();
    }

    @Override
    public String toString() {
        return "Message{" +
                "senderId=" + signature +
                ", content='" + content + '\'' +
                '}';
    }

    @Override
    public int compareTo(Message o) {
        return Integer.compare(this.signature, o.signature);
    }
}
