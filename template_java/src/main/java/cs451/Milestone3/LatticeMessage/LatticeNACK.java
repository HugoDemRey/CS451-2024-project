package cs451.Milestone3.LatticeMessage;

import java.util.Set;

public class LatticeNACK extends LatticeMessage {
    /**
     * LatticeNACK Format:
     * [N][proposalNumber]-[val1],[val2],...,[valn]
     * Example:
     * "N1.2-1,2,3"       -> NACK for proposal number 2 at epoch 1 with values 1, 2, 3
     * "N324.32-1,34,23,2" -> NACK for proposal number 32 at epoch 324 with values 1, 34, 23, 2
     */

    private final Set<Integer> values;

    public LatticeNACK(int epoch, int proposalNumber, Set<Integer> values) {
        super(epoch, proposalNumber);
        this.values = values;
    }

    public static LatticeNACK fromString(String content) {
        int pointIndex = content.indexOf('.');
        int dashIndex = content.indexOf('-');

        int epoch = Integer.parseInt(content.substring(1, pointIndex));
        int proposalNumber = Integer.parseInt(content.substring(pointIndex + 1, dashIndex));

        Set<Integer> values = unpackValues(content, dashIndex);

        return new LatticeNACK(epoch, proposalNumber, values);
    }


    public Set<Integer> getValues() {
        return values;
    }

    @Override
    public String toString() {
        return "N" + epoch() + "." + proposalNumber() + "-" + values.toString().replace("[", "").replace("]", "").replace(" ", "");
    }
    
}
