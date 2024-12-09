package cs451.Milestone3.MessageTypes;

import java.util.Set;

public class LatticeNACK extends LatticeMessage {
    /**
     * LatticeNACK Format:
     * [N][proposalNumber]-[val1],[val2],...,[valn]
     * Example:
     * "N1-1,2,3"       -> NACK for proposal number 1 with values 1, 2, 3
     * "N324-1,34,23,2" -> NACK for proposal number 324 with values 1, 34, 23, 2
     */

    private final Set<Integer> values;

    public LatticeNACK(int proposalNumber, Set<Integer> values) {
        super(proposalNumber);
        this.values = values;
    }

    public static LatticeNACK fromString(String content) {
        int dashIndex = content.indexOf('-');
        int proposalNumber = Integer.parseInt(content.substring(1, dashIndex));

        Set<Integer> values = unpackValues(content, dashIndex);

        return new LatticeNACK(proposalNumber, values);
    }


    public Set<Integer> getValues() {
        return values;
    }

    @Override
    public String toString() {
        return "N" + proposalNumber() + "-" + values.toString().replace("[", "").replace("]", "").replace(" ", "");
    }
    
}
