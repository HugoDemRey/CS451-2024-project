package cs451.Milestone3.LatticeMessage;

public class LatticeACK extends LatticeMessage {
    /**
     * LatticeAck Format:
     * <A><epoch>.<proposalNumber>
     * Example: 
     * "A1.1" -> Acknowledgement for proposal number 1 at epoch 1
     * "A67.4213" -> Acknowledgement for proposal number 4213 at epoch 67
     */

    public LatticeACK(int epoch, int proposalNumber) {
        super(epoch, proposalNumber);
    }

    public static LatticeACK fromString(String content) {
        int pointIndex = content.indexOf('.');

        int epoch = Integer.parseInt(content.substring(1, pointIndex));
        int proposalNumber = Integer.parseInt(content.substring(pointIndex + 1));

        return new LatticeACK(epoch, proposalNumber);
    }

    @Override
    public String toString() {
        return "A" + epoch() + "." + proposalNumber();
    }
    
}
