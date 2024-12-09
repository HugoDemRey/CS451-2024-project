package cs451.Milestone3.MessageTypes;

import java.util.Set;

public class LatticeNACK extends LatticeMessageType {
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

    public Set<Integer> getValues() {
        return values;
    }
    
}
