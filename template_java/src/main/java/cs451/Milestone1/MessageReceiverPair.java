package cs451.Milestone1.Host;

public class MessageReceiverPair {
    private final Message message;
    private final Host receiver;

    public MessageReceiverPair(Message message, Host receiver) {
        this.message = message;
        this.receiver = receiver;
    }

    public Message getMessage() {
        return message;
    }

    public Host getReceiver() {
        return receiver;
    }
}
