package cs451.Milestone3.LatticeMessage;

import java.util.Set;

public class LatticeProposal extends LatticeMessage {
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

    public static LatticeProposal fromString(String content) {
        int dashIndex = content.indexOf('-');
        int proposalNumber = Integer.parseInt(content.substring(1, dashIndex));

        Set<Integer> values = unpackValues(content, dashIndex);

        return new LatticeProposal(proposalNumber, values);
    }

    public Set<Integer> getValues() {
        return values;
    }

    @Override
    public String toString() {
        return "P" + proposalNumber() + "-" + values.toString().replace("[", "").replace("]", "").replace(" ", "");
    }
    
}
