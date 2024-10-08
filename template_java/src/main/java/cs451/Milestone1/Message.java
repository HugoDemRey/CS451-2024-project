package cs451.Milestone1;

public class Message {
    private int seqNum;
    private int senderId;
    private String content;

    // Constructors
    public Message() {}

    public Message(int senderId, String content) {
        this.senderId = senderId;
        this.content = content;
    }

    // Getters and Setters
    public int getSeqNum() {
        return seqNum;
    }

    public void setSeqNum(int seqNum) {
        this.seqNum = seqNum;
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
}
