package cs451.Milestone3.LatticeMessage;

import java.util.HashSet;
import java.util.Set;

public abstract class LatticeMessage {

    private final int epoch;
    private final int proposalNumber;

    public LatticeMessage(int epoch, int proposalNumber) {
        this.epoch = epoch;
        this.proposalNumber = proposalNumber;
    }

    public int proposalNumber() {
        return proposalNumber;
    }

    public int epoch() {
        return epoch;
    }

    public static Set<Integer> unpackValues(String content, int dashIndex) {
        // Parse values
        Set<Integer> values = new HashSet<>();
        StringBuilder currentNumber = new StringBuilder();
        
        for (int i = dashIndex + 1; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == ',') {
                if (currentNumber.length() > 0) {
                    values.add(Integer.parseInt(currentNumber.toString()));
                    currentNumber.setLength(0);
                }
            } else {
                currentNumber.append(c);
            }
        }
        
        // Add the last number
        if (currentNumber.length() > 0) {
            values.add(Integer.parseInt(currentNumber.toString()));
        }

        return values;
    }

    public static LatticeMessage fromContent(String content) {

        char messageType = content.charAt(0);

        switch (messageType) {
            case 'A': return LatticeACK.fromString(content);
            case 'N': return LatticeNACK.fromString(content);
            case 'P': return LatticeProposal.fromString(content);
        }

        return null;
    }


    
}


