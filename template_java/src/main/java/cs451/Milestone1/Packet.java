package cs451.Milestone1;

import cs451.Host;

public class Packet {
    private final Message[] messages;
    private final int nbMessages;
    private final Host receiver;

    public Packet(Message[] messages, int nbMessages, Host receiver) {
        this.messages = messages;
        this.nbMessages = nbMessages;
        this.receiver = receiver;
    }

    public Message[] getMessages() {
        return messages;
    }

    public int getNbMessages() {
        return nbMessages;
    }

    public Host getReceiver() {
        return receiver;
    }
}
