package cs451.Milestone1;


public class Message {
    private int senderId;
    private int seqNumber;


    public Message(int senderId, int seqNumber) {
        this.senderId = senderId;
        this.seqNumber = seqNumber;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public int getSenderId(){
        return senderId;
    }

    @Override
    public String toString() {
        return "Message{" +
                "senderId=" + senderId +
                "seqNumber=" + seqNumber +
                '}';
    }
}