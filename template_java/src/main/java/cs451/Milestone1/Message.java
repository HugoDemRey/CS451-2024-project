package cs451.Milestone1;

import java.nio.charset.StandardCharsets;

public class Message {
    private int senderId;
    private String content;
    private int seqNum;


    public Message(int senderId, String content, int seqNum) {
        this.senderId = senderId;
        this.content = content;
        this.seqNum = 0;
    }

    public String getContent() {
        return content;
    }

    public int getSenderId(){
        return senderId;
    }

    public int getSeqNum(){
        return seqNum;
    }

    public void setSeqNum(int seqNumber){
        this.seqNum = seqNumber;
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