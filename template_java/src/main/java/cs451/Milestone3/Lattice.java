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
        activeProposalNumber.incrementAndGet();
        ackCount.set(0);
        nackCount.set(0);

        LatticeProposal latticeProposal = new LatticeProposal(activeProposalNumber.get(), proposal);
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

    public void bebDeliver(int lastSenderId, Message m) {

        // TODO : Check that exactly these (lastSenderId, m) were not already delivered.
        // I.e. Drop duplicates. 

        LatticeMessage lm = LatticeMessage.fromContent(m.getContent());
        if (lm instanceof LatticeProposal) {
            handleIncomingProposal((LatticeProposal) lm, lastSenderId);
        } else if (lm instanceof LatticeACK) {
            handleIncomingAck((LatticeACK) lm);
        } else if (lm instanceof LatticeNACK) {
            handleIncomingNack((LatticeNACK) lm);
        }
    }

    public void handleIncomingProposal(LatticeProposal proposal, int lastSenderId) {
        System.out.println("Received proposal from " + lastSenderId + " with proposal " + proposal.toString());
        LatticeACK ack = new LatticeACK(proposal.proposalNumber());
        Message m = new Message(perfectLinks.id(), ack.toString());
        bebSend(m, lastSenderId);
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
