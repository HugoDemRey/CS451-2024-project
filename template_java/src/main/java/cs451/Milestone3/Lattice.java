package cs451.Milestone3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import cs451.Host;
import cs451.Milestone1.Message;
import cs451.Milestone1.Host.HostParams;
import cs451.Milestone2.ApplicationLayer;
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


    public Lattice(HostParams hostParams, List<Host> hosts, String outputFilePath) {
        this.hosts = hosts;
        perfectLinks = new PerfectLinks(hostParams, this);
        this.applicationLayer = new ApplicationLayer(outputFilePath, false);
    }

    public void propose(Set<Integer> proposal) {
        proposedValues = proposal;
        active.set(true);
        ackCount.set(0);
        nackCount.set(0);

        LatticeProposal latticeProposal = new LatticeProposal(activeProposalNumber.getAndIncrement(), proposal);
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
        LatticeACK ack = new LatticeACK(proposal.proposalNumber());
        Message m = new Message(perfectLinks.id(), ack.toString());
        bebSend(m, fromHostId);
    }

    public void handleIncomingAck(LatticeACK ack) {
        System.out.println("Received A" + ack.proposalNumber());
    }

    public void handleIncomingNack(LatticeNACK nack) {
        System.out.println("Received N" + nack.proposalNumber());
    }


    public void flushOutput() {
        applicationLayer.flushOutput();
    }
}
