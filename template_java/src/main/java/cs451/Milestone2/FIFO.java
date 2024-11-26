package cs451.Milestone2;

import java.util.List;

import cs451.Host;
import cs451.Milestone1.Message;
import cs451.Milestone1.Host.ActiveHost;
import cs451.Milestone1.Host.HostParams;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class FIFO extends ActiveHost {
    URB urb;
    private Map<Integer, PriorityQueue<Message>> pendingMessages = new HashMap<>();
    private Map<Integer, Integer> nextExpected = new HashMap<>();
    

    public FIFO(HostParams hostParams, String outputFilePath, List<Host> hosts) {
        urb = new URB(this);
        urb.populate(hostParams, outputFilePath, hosts);
        this.populate(hostParams, outputFilePath);
    }


    public void FIFOBroadcast(Message m) {
        String broadcastString = "b " + m.getContent() + "\n";
        write(broadcastString);

        urb.urbBroadcast(m);
    }

    /**
     * @note We know that every message that is passed to this function has never been delivered before.
     * This is because the delering system is handled by the URB class that makes sure that the message is delivered only once.
     * However, the goal of this class is to deliver the message in the order that it was broadcasted,
     * which is something that the URB class does not handle.
     * @param m The message to deliver triggered by the URB class.
     */
    public void FIFODeliver(Message m) {
        int initiatorId = m.getInitiatorId();
    
        pendingMessages.putIfAbsent(initiatorId, new PriorityQueue<>((a, b) -> Integer.compare(Integer.parseInt(a.getContent()), Integer.parseInt(b.getContent()))));
        nextExpected.putIfAbsent(initiatorId, 1);
    
        PriorityQueue<Message> queue = pendingMessages.get(initiatorId);
        queue.add(m);
    
        while (!queue.isEmpty() && Integer.parseInt(queue.peek().getContent()) == nextExpected.get(initiatorId)) {
            Message nextMessage = queue.poll();
            String deliverString = "d " + nextMessage.getInitiatorId() + " " + nextMessage.getContent() + "\n";
            write(deliverString);
            nextExpected.put(initiatorId, nextExpected.get(initiatorId) + 1);
        }
    }
}