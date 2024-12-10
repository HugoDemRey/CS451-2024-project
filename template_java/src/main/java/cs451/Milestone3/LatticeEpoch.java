package cs451.Milestone3;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LatticeEpoch {
    protected AtomicBoolean active = new AtomicBoolean(true);
    protected AtomicInteger ackCount = new AtomicInteger(0);
    protected AtomicInteger nackCount = new AtomicInteger(0);
    protected AtomicInteger activeProposalNumber = new AtomicInteger(0);
    protected Set<Integer> proposedValues = new HashSet<>();
    protected Set<Integer> acceptedValues = new HashSet<>();

    public LatticeEpoch() {}

}
