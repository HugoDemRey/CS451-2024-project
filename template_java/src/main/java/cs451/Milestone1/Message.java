package cs451.Milestone1;


public class Message {
    private int senderId;
    private String content;


    public Message(int senderId, String content) {
        this.senderId = senderId;
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public int getSenderId(){
        return senderId;
    }

    @Override
    public String toString() {
        return "Message{" +
                "senderId=" + senderId +
                "seqNumber=" + content +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        // if (obj == null) {
        //     return false;
        // }
        // if (obj == this) {
        //     return true;
        // }
        // if (obj.getClass() != getClass()) {
        //     return false;
        // }
        Message other = (Message) obj;
        System.out.println("");
        return this.senderId == other.senderId && this.content == other.content;
    }
}