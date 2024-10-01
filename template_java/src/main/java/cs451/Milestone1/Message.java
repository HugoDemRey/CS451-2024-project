package cs451.Milestone1;


public class Message {
    private int seqNumber;

    public Message(int seqNumber) {
        this.seqNumber = seqNumber;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    @Override
    public String toString() {
        return "Message{" +
                "seqNumber=" + seqNumber +
                '}';
    }
}