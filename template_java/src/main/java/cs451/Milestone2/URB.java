package cs451.Milestone2;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import cs451.Host;
import cs451.Milestone1.Message;
import cs451.Milestone1.Host.HostParams;

/**
 * The URB class is responsible for the Uniform Reliable Broadcast. It is built on top of the PerfectLinks class.
 * Properties:
 *     - URB1 (Validity): If a correct process broadcasts a message m, then every correct process eventually delivers m.
 *     - URB2 (No duplication): No message is delivered more than once.
 *     - URB3 (No creation): If some process q delivers a message m with sender p, then q has previously sent or received m.
 *     - URB4 (Uniform Agreement): If a process (can be faulty) delivers a message m with sender p, then every correct process eventually delivers m.
 * 
 * URB1, URB2, URB3 are directly fullfilled by PL1, PL2, PL3 from the PerfectLinks class.
 * URB4 is handled in this class.
 * 
 * @implNote The URB class uses the Best-Effort Broadcast (BEB) abstraction to broadcast messages to all hosts.
 * The creation of an independant BEB class was considered useless in this project. 
 */
public class URB {

    List<Host> hosts;

    Set<Message> delivered;
    ConcurrentLinkedQueue<Message> pending;

    ConcurrentHashMap<Message, Set<Integer>> acks; // <Message, Set<hostIds>>

    PerfectLinks perfectLinks;
    FIFO fifo;

    public URB(HostParams hostParams, List<Host> hosts, FIFO fifo) {
        this.fifo = fifo;
        this.hosts = hosts;
        perfectLinks = new PerfectLinks(hostParams, this);
        init();
    }


    /* URB INIT */
    public void init() {
        delivered = new HashSet<>();
        pending = new ConcurrentLinkedQueue<>();
        acks = new ConcurrentHashMap<>();

        // Start checking for pending messages
        new Thread(() -> {
            while (true) {
                checkPending();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void broadcastSleep(int pendingCond) {
        while (pending.size() >= pendingCond) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /* URB BROADCAST */

    public void urbBroadcast(Message m) {

        broadcastSleep(1500);

        pending.add(m);
        acks.put(m, new HashSet<>(List.of(perfectLinks.id())));

        //System.out.println("Broadcasting message: " + m.getContent());
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

    /* URB DELIVER */

    public void urbDeliver(Message m) {
        if (delivered.contains(m)) return;
        delivered.add(m);
        fifo.FIFODeliver(m);
    }

    int rebroadcasts = 0;

    public void bebDeliver(int lastSenderId, Message m) {
        acks.putIfAbsent(m, new HashSet<>());
        Set<Integer> acksM = acks.get(m);
        boolean neverReceivedByLastSenderId = acksM.add(lastSenderId);
        
        if (neverReceivedByLastSenderId && acksM.size() == 1 && m.getInitiatorId() != perfectLinks.id()) {
            acksM.add(perfectLinks.id());
            pending.add(m);
            bebBroadcast(m);
        }
        
    }

    /** Run it every X second(s) on another thread */
    public void checkPending() {
        ConcurrentLinkedQueue<Message> pendingCopy = new ConcurrentLinkedQueue<>(pending);
        ConcurrentLinkedQueue<Message> toRemove = new ConcurrentLinkedQueue<>();
        System.out.println("Checking pending messages: " + pendingCopy.size() + " messages");
        Message m;
        while ((m = pendingCopy.poll()) != null) {
            if (canDeliver(m)) {
                toRemove.add(m);
                urbDeliver(m);
            }
        }
        System.out.println("Finished Checking Pending, treated " + toRemove.size() + " messages\n");
        pending.removeAll(toRemove);
        
    }

    int myMessagesDelivered = 0;

    /* URB CRASH (To Change) */
    public boolean canDeliver(Message m) {
        if (!acks.containsKey(m)) return false;

        boolean condition = acks.get(m).size() > (hosts.size() / 2);
        return condition;
    }

}
