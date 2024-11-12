package cs451.Milestone2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cs451.Host;
import cs451.Milestone1.Message;
import cs451.Milestone1.Pair;
import cs451.Milestone1.Host.ActiveHost;
import cs451.Milestone1.Host.HostParams;

public class Transceiver extends ActiveHost {

    List<Host> hosts;

    Set<Message> delivered; // <HostId, Message>
    Set<Pair<Integer, Message>> pending;
    Map<Message, Set<Integer>> acks;

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
    }

    /* URB BROADCAST */

    public void urbBroadcast(Message m) {
        pending.add(new Pair<>(this.id(), m));

        String broadcastString = "b " + m.getContent() + "\n";
        write(broadcastString);
        bebBroadcast(m);
    }

    public void bebBroadcast(Message m) {
        for (Host h : hosts) {
            perfectLinks.enqueueMessage(m, h);
        }
    }

    /* URB DELIVER */

    public void urbDeliver(Message m) {
        String deliverString = "d " + m.getSenderId() + " " + m.getContent() + "\n";
        write(deliverString);
        delivered.add(m);
    }

    public void bebDeliver(int lastSenderId, Message m) {
        acks.get(m).add(lastSenderId);
        Pair<Integer, Message> pair = new Pair<>(lastSenderId, m);
        if (!pending.contains(pair)) {
            pending.add(pair);
            bebBroadcast(m);
        }
    }



    /* URB CRASH (To Change) */

    public boolean canDeliver(Message m) {
        return acks.get(m).size() > hosts.size() / 2;
    }

}
