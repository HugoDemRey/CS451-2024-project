package cs451.Milestone1;

import cs451.Host;

public class Packet {
    private final int seqNum;
    private final Message[] messages;
    private final int nbMessages;
    private final Host receiver;

    public Packet(int seqNum, Message[] messages, int nbMessages, Host receiver) {
        this.seqNum = seqNum;
        this.messages = messages;
        this.nbMessages = nbMessages;
        this.receiver = receiver;
    }

    public int seqNum() {
        return seqNum;
    }

    public Message[] messages() {
        return messages;
    }

    public int nbMessages() {
        return nbMessages;
    }

    public Host receiver() {
        return receiver;
    }

    @Override
    public String toString() {
        return "Packet{" +
                "messages=" + messages +
                ", nbMessages=" + nbMessages +
                ", receiver=" + receiver +
                '}';
    }
}
