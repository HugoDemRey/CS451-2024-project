package cs451.Milestone3.LatticeMessage;

public class LatticeACK extends LatticeMessage {
    /**
     * LatticeAck Format:
     * [A][proposalNumber]
     * Example: 
     * "A1" -> Acknowledgement for proposal number 1
     * "A4213" -> Acknowledgement for proposal number 4213
     */

    public LatticeACK(int proposalNumber) {
        super(proposalNumber);
    }

    public static LatticeACK fromString(String content) {
        int proposalNumber = Integer.parseInt(content.substring(1));
        return new LatticeACK(proposalNumber);
    }

    @Override
    public String toString() {
        return "A" + proposalNumber();
    }
    
}
