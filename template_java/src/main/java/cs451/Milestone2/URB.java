package cs451.Milestone2;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import cs451.Host;
import cs451.Milestone1.Message;
import cs451.Milestone1.Host.ActiveHost;
import cs451.Milestone1.Host.HostParams;

/**
 * Uniform Reliable Broadcast
 */
public class URB extends ActiveHost {

    List<Host> hosts;

    Set<Message> delivered;
    ConcurrentLinkedQueue<Message> pending;
    ConcurrentHashMap<Message, Set<Integer>> acks; // <Message, Set<hostIds>>

    PerfectLinks perfectLinks;

    public boolean populate(HostParams hostParams, String outputFilePath, List<Host> hosts) {
        boolean result = super.populate(hostParams, outputFilePath);
        this.hosts = hosts;
        init();
        return result;
    }

    /* URB INIT */

    public void init() {
        delivered = new HashSet<>();
        pending = new ConcurrentLinkedQueue<>();
        acks = new ConcurrentHashMap<>();
        perfectLinks = new PerfectLinks(this);

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

    /* URB BROADCAST */

    public void urbBroadcast(Message m) {
        pending.add(m);
        acks.put(m, new HashSet<>(List.of(id())));

        debug("Broadcasting message: " + m.getContent() + "\n");

        String broadcastString = "b " + m.getContent() + "\n";
        write(broadcastString);

        //System.out.println("Broadcasting message: " + m.getContent());
        bebBroadcast(m);
    }

    public void bebBroadcast(Message m) {

        while (pending.size() >= 1000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        for (Host h : hosts) {
            if (h.id() == id()) {
                continue;
            }
            perfectLinks.enqueueMessage(m, h);
        }
    }

    /* URB DELIVER */

    public void urbDeliver(Message m) {
        if (delivered.contains(m)) return;
        String deliverString = "d " + m.getInitiatorId() + " " + m.getContent() + "\n";
        write(deliverString);
        delivered.add(m);
    }

    int rebroadcasts = 0;

    public void bebDeliver(int lastSenderId, Message m) {
        acks.putIfAbsent(m, new HashSet<>());
        Set<Integer> acksM = acks.get(m);
        boolean neverReceivedByLastSenderId = acksM.add(lastSenderId);
        
        if (neverReceivedByLastSenderId && acksM.size() == 1 && m.getInitiatorId() != id()) {
            debug(rebroadcasts++ + " rebroadcasts\n");
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


    /* URB CRASH (To Change) */
    public boolean canDeliver(Message m) {
        if (!acks.containsKey(m)) return false;
        return acks.get(m).size() >= (hosts.size() / 2);
    }

}
