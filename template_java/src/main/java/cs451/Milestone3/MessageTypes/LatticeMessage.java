package cs451.Milestone3.MessageTypes;

import java.util.HashSet;
import java.util.Set;

public abstract class LatticeMessage {

    private final int proposalNumber;

    public LatticeMessage(int proposalNumber) {
        this.proposalNumber = proposalNumber;
    }

    public int proposalNumber() {
        return proposalNumber;
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


