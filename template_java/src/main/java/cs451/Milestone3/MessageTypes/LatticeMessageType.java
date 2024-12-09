package cs451.Milestone3.MessageTypes;

public abstract class LatticeMessageType {

    private final int proposalNumber;

    public LatticeMessageType(int proposalNumber) {
        this.proposalNumber = proposalNumber;
    }

    public int proposalNumber() {
        return proposalNumber;
    }

    public static LatticeMessageType fromContent(String content) {
        int dashIndex = input.indexOf('-');
        if (dashIndex == -1) {
            throw new IllegalArgumentException("Missing delimiter '-'");
        }

        // Extract proposal number
        int proposalNumber = Integer.parseInt(input.substring(1, dashIndex));

        // Parse values
        List<Integer> values = new ArrayList<>();
        StringBuilder currentNumber = new StringBuilder();
        
        for (int i = dashIndex + 1; i < input.length(); i++) {
            char c = input.charAt(i);
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

        return new LatticeNACK(proposalNumber, values);
    }
        return null;
    }
    
}


