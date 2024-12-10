package cs451.Milestone3;

import static cs451.Constants.DECISION_CHECK_INTERVAL;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private AtomicBoolean active = new AtomicBoolean(false);
    private AtomicInteger ackCount = new AtomicInteger(0);
    private AtomicInteger nackCount = new AtomicInteger(0);
    private AtomicInteger activeProposalNumber = new AtomicInteger(0);
    private Set<Integer> proposedValues = new HashSet<>();
    private Set<Integer> acceptedValues = new HashSet<>();
    private final int f; // represents the half of the number of hosts


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

    public void propose(Set<Integer> proposal) {
        proposedValues = proposal;
        active.set(true);
        ackCount.set(0);
        nackCount.set(0);

        LatticeProposal latticeProposal = new LatticeProposal(activeProposalNumber.incrementAndGet(), proposal);
        Message m = new Message(perfectLinks.id(), latticeProposal.toString());
        System.out.println("Proposing " + latticeProposal.toString());
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

        System.out.println("Received proposal from " + fromHostId + " with proposal " + proposal.toString());
        Message m;
        if (proposal.getValues().containsAll(acceptedValues)) {
            acceptedValues = proposal.getValues();
            LatticeACK ack = new LatticeACK(proposal.proposalNumber());
            m = new Message(perfectLinks.id(), ack.toString());
            System.out.println("Sending A" + ack.proposalNumber());
        } else {
            acceptedValues.addAll(proposal.getValues());
            LatticeNACK nack = new LatticeNACK(proposal.proposalNumber(), acceptedValues);
            m = new Message(perfectLinks.id(), nack.toString());
            System.out.println("Sending N" + nack.proposalNumber());
        }

        bebSend(m, fromHostId);
    }

    public void handleIncomingAck(LatticeACK ack) {
        System.out.println("Received A" + ack.proposalNumber() + " with activeProposalNumber: " + activeProposalNumber.get());
        if (ack.proposalNumber() != activeProposalNumber.get()) return;

        ackCount.incrementAndGet();
    }

    public void handleIncomingNack(LatticeNACK nack) {
        System.out.println("Received N" + nack.proposalNumber() + " with activeProposalNumber: " + activeProposalNumber.get());
        if (nack.proposalNumber() != activeProposalNumber.get()) return;

        proposedValues.addAll(nack.getValues());
        nackCount.incrementAndGet();
    }

    private void checkForReproposition() {
        if (!(nackCount.get() > 0) || !(ackCount.get() + nackCount.get() >= f+1) || !active.get()) return;
        
        System.out.println("Reproposing!");
        propose(proposedValues);
    }

    private void checkForDecision() {
        //System.out.println("Checking for decision, ackCount: " + ackCount.get() + " nackCount: " + nackCount.get() + " active: " + active.get());
        if (!(ackCount.get() >= f+1) || !active.get()) return;

        System.out.println("Deciding!");
        active.set(false);
        applicationLayer.decide(proposedValues);
    }

    public void flushOutput() {
        applicationLayer.flushOutput();
    }
}
