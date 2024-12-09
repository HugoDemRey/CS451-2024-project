package cs451.Milestone3.MessageTypes;

public class LatticeACK extends LatticeMessageType {
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
    
}
