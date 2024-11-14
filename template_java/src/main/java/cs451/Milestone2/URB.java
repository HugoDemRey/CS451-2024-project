package cs451.Milestone2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

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
    Set<Message> pending;
    Map<Message, Set<Integer>> acks; // <Message, Set<hostIds>>

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
        pending = new HashSet<>();
        acks = new HashMap<>();
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

        String broadcastString = "b " + m.getContent() + "\n";
        write(broadcastString);

        System.out.println("Broadcasting message: " + m.getContent());
        bebBroadcast(m);
    }

    public void bebBroadcast(Message m) {
        for (Host h : hosts) {
            if (h.id() == id()) {
                // TODO: Modify to allow concurrent access
                acks.computeIfAbsent(m, k -> new HashSet<>());
                acks.get(m).add(id());
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

    public void bebDeliver(int lastSenderId, Message m) {
        acks.computeIfAbsent(m, k -> new HashSet<>());
        acks.get(m).add(lastSenderId);
        
        if (pending.add(m)) {
            bebBroadcast(m);
        }
        
    }

    /** Run it every X second(s) on another thread */
    public void checkPending() {
        Set<Message> pendingCopy = new HashSet<>(pending);
        for (Message m : pendingCopy) {
            if (canDeliver(m)) {
                urbDeliver(m);
                pending.remove(m);
            }
        }
        
    }


    /* URB CRASH (To Change) */
    public boolean canDeliver(Message m) {
        if (!acks.containsKey(m)) return false;
        return acks.get(m).size() > hosts.size() / 2;
    }

}
