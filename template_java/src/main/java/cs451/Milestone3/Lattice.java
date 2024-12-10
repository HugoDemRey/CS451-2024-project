package cs451.Milestone3;

import static cs451.Constants.CHECK_NEW_PROPOSITION_INTERVAL;
import static cs451.Constants.DECISION_CHECK_INTERVAL;
import static cs451.Constants.MAX_EPOCH_SIMULTANEOUSLY;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import cs451.Host;
import cs451.Milestone1.Message;
import cs451.Milestone1.Host.HostParams;
import cs451.Milestone3.LatticeMessage.LatticeACK;
import cs451.Milestone3.LatticeMessage.LatticeMessage;
import cs451.Milestone3.LatticeMessage.LatticeNACK;
import cs451.Milestone3.LatticeMessage.LatticeProposal;

public class Lattice {

    List<Host> hosts;
    PerfectLinks perfectLinks;
    ApplicationLayer applicationLayer;

    // Lattice Pseudocode Variables
    private final Map<Integer, LatticeEpoch> latticeEpochs = new ConcurrentHashMap<>();
    private final int f; // represents the half of the number of hosts
    private final AtomicInteger nextEpochToDecide = new AtomicInteger(1);
    private final Map<Integer, Set<Integer>> toDecide = new HashMap<>();
    private final AtomicInteger currentActiveEpochs = new AtomicInteger(0);


    public Lattice(HostParams hostParams, List<Host> hosts, String outputFilePath) {
        this.hosts = hosts;
        this.f = hosts.size() / 2;
        perfectLinks = new PerfectLinks(hostParams, this);
        this.applicationLayer = new ApplicationLayer(outputFilePath, false);

        // Start checking for pending messages
        new Thread(() -> {
            while (true) {
                checkForReproposition();
                checkForDecision();
                try {
                    Thread.sleep(DECISION_CHECK_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    int lastProposed = 0;
    int lastDecided = 0;
    private void printInfo(int p, int d) {
        if (p == -1) {
            lastDecided = d;
            System.out.println("|| " + lastProposed + " | " + d + " ||");
        } else if (d == -1) {
            lastProposed = p;
            System.out.println("|| " + p + " | " + lastDecided + " ||");
        } else {
            System.out.println("|| " + p + " | " + d + " ||");
        }
    }

    public void propose(int epoch, Set<Integer> proposal, boolean firstTime) {

        if (firstTime) {
            while (currentActiveEpochs.get() > MAX_EPOCH_SIMULTANEOUSLY) {
                try {
                    Thread.sleep(CHECK_NEW_PROPOSITION_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            printInfo(epoch, -1);
        }
    
        latticeEpochs.putIfAbsent(epoch, new LatticeEpoch());
        LatticeEpoch latticeEpoch = latticeEpochs.get(epoch);
    
        if (latticeEpoch.activeProposalNumber.get() == 0) currentActiveEpochs.incrementAndGet();
        latticeEpoch.proposedValues = proposal;
        latticeEpoch.active.set(true);
        latticeEpoch.ackCount.set(0);
        latticeEpoch.nackCount.set(0);

        LatticeProposal latticeProposal = new LatticeProposal(epoch, latticeEpoch.activeProposalNumber.incrementAndGet(), proposal);
        Message m = new Message(perfectLinks.id(), latticeProposal.toString());
        bebBroadcast(m);
    }

    public void bebBroadcast(Message m) {
        for (Host h : hosts) {
            if (h.id() == perfectLinks.id()) {
                continue;
            }
            perfectLinks.enqueueMessage(m, h);
        }
    }

    public void bebSend(Message m, int receiverId) {
        Host host = hosts.get(receiverId-1);
        perfectLinks.enqueueMessage(m, host);
    }

    /**
     * This method is called by the perfect links layer when a message is received
     * @param fromHostId The id of the host that sent the message
     * @param m The message that was sent
     * @implNote all messages passed to this function are never duplicates, the perfectlinks class handle this.
     */
    public void bebDeliver(int fromHostId, Message m) {
        LatticeMessage lm = LatticeMessage.fromContent(m.getContent());
        if (lm instanceof LatticeProposal) {
            handleIncomingProposal((LatticeProposal) lm, fromHostId);
        } else if (lm instanceof LatticeACK) {
            handleIncomingAck((LatticeACK) lm);
        } else if (lm instanceof LatticeNACK) {
            handleIncomingNack((LatticeNACK) lm);
        }
    }

    public void handleIncomingProposal(LatticeProposal proposal, int fromHostId) {
        latticeEpochs.putIfAbsent(proposal.epoch(), new LatticeEpoch());
        LatticeEpoch latticeEpoch = latticeEpochs.get(proposal.epoch());
        Message m;
        if (proposal.getValues().containsAll(latticeEpoch.acceptedValues)) {
            latticeEpoch.acceptedValues = proposal.getValues();
            LatticeACK ack = new LatticeACK(proposal.epoch(), proposal.proposalNumber());
            m = new Message(perfectLinks.id(), ack.toString());
        } else {
            latticeEpoch.acceptedValues.addAll(proposal.getValues());
            LatticeNACK nack = new LatticeNACK(proposal.epoch(), proposal.proposalNumber(), latticeEpoch.acceptedValues);
            m = new Message(perfectLinks.id(), nack.toString());
        }

        bebSend(m, fromHostId);
    }

    public void handleIncomingAck(LatticeACK ack) {
        LatticeEpoch latticeEpoch = latticeEpochs.get(ack.epoch());
        if (ack.proposalNumber() != latticeEpoch.activeProposalNumber.get()) return;

        latticeEpoch.ackCount.incrementAndGet();
    }

    public void handleIncomingNack(LatticeNACK nack) {
        LatticeEpoch latticeEpoch = latticeEpochs.get(nack.epoch());
        if (nack.proposalNumber() != latticeEpoch.activeProposalNumber.get()) return;

        latticeEpoch.proposedValues.addAll(nack.getValues());
        latticeEpoch.nackCount.incrementAndGet();
    }

    private void checkForReproposition() {
        for (int epoch : latticeEpochs.keySet()) {
            LatticeEpoch latticeEpoch = latticeEpochs.get(epoch);
            if (!latticeEpoch.active.get() || !(latticeEpoch.nackCount.get() > 0) || !(latticeEpoch.ackCount.get() + latticeEpoch.nackCount.get() >= f+1)) continue;
            
            propose(epoch, latticeEpoch.proposedValues, false);
        }
    }

    private void checkForDecision() {
        for (int epoch : latticeEpochs.keySet()) {
            LatticeEpoch latticeEpoch = latticeEpochs.get(epoch);
            if (!latticeEpoch.active.get() || !(latticeEpoch.ackCount.get() >= f+1)) continue;


            currentActiveEpochs.decrementAndGet();
            latticeEpoch.active.set(false);
            if (nextEpochToDecide.compareAndSet(epoch, epoch + 1)) {
                printInfo(-1, epoch);
                applicationLayer.decide(latticeEpoch.proposedValues);
            } else {
                toDecide.put(epoch, latticeEpoch.proposedValues);
            }
        }

        while (toDecide.containsKey(nextEpochToDecide.get())) {
            printInfo(-1, nextEpochToDecide.get());
            applicationLayer.decide(toDecide.get(nextEpochToDecide.get()));
            toDecide.remove(nextEpochToDecide.get());
            nextEpochToDecide.incrementAndGet();
        }

    }

    public void flushOutput() {
        applicationLayer.flushOutput();
    }
}
