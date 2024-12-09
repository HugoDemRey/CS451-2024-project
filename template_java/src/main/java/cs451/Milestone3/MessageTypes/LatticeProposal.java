package cs451.Milestone3.MessageTypes;

import java.util.Set;

public class LatticeProposal extends LatticeMessageType{
    /**
     * LatticeProposal Format:
     * [P][proposalNumber]-[val1],[val2],...,[valn]
     * Example:
     * "P1-1,2,3"       -> Proposal for proposal number 1 with values 1, 2, 3
     * "P324-1,34,23,2" -> Proposal for proposal number 324 with values 1, 34, 23, 2
     */

     private final Set<Integer> values;

    public LatticeProposal(int proposalNumber, Set<Integer> values) {
        super(proposalNumber);
        this.values = values;
    }

    public Set<Integer> getValues() {
        return values;
    }
    
}
