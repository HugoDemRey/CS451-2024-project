package cs451.Milestone3.MessageTypes;

public abstract class LatticeMessageType {

    private final int proposalNumber;

    public LatticeMessageType(int proposalNumber) {
        this.proposalNumber = proposalNumber;
    }

    public int proposalNumber() {
        return proposalNumber;
    }

    public static LatticeMessageType fromContent(String content) {

        return null;
    }
    
}


