package cs451.Milestone2;

import java.util.List;

import cs451.Host;
import cs451.Milestone1.Message;
import cs451.Milestone1.Host.HostParams;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The FIFO class is responsible for the First-In-First-Out ordering of messages. It is built on top of the URB class.
 * Properties:
 *     - FRB1 (Validity): If a correct process broadcasts a message m, then every correct process eventually delivers m.
 *     - FRB2 (No duplication): No message is delivered more than once.
 *     - FRB3 (No creation): If some process q delivers a message m with sender p, then q has previously sent or received m.
 *     - FRB4 (Uniform Agreement): If a process (can be faulty) delivers a message m with sender p, then every correct process eventually delivers m.
 *     - FRB5 (FIFO delivery): If some process broadcasts messages m1 before m2, then no correct process delivers m2 unless it has already delivered m1.
 * 
 * FRB1, FRB2, FRB3, FRB4 are directly fullfilled by URB1, URB2, URB3, URB4 from the URB class.
 * FRB5 is handle in this class.
 */
public class FIFO {
    URB urb;
    ApplicationLayer applicationLayer;
    private Map<Integer, PriorityQueue<Message>> pendingMessages = new HashMap<>();
    private Map<Integer, Integer> nextExpected = new HashMap<>();
    

    public FIFO(String outputFilePath, HostParams hostParams, List<Host> hosts) {
        this.applicationLayer = new ApplicationLayer(outputFilePath, false);
        urb = new URB(hostParams, hosts, this);
    }

    public void FIFOBroadcast(Message m) {
        applicationLayer.broadcast(m.getContent());
        urb.urbBroadcast(m);
    }

    /**
     * @note We know that every message that is passed to this function has never been delivered before.
     * This is because the delivering system is handled by the URB class that makes sure that the message is delivered only once.
     * However, the goal of this class is to deliver the message in the order that it was broadcasted,
     * which is something that the URB class does not handle.
     * @param m The message to deliver triggered by the URB class.
     */
    public void FIFODeliver(Message m) {
        int initiatorId = m.getSignature();
    
        pendingMessages.putIfAbsent(initiatorId, new PriorityQueue<>((a, b) -> Integer.compare(Integer.parseInt(a.getContent()), Integer.parseInt(b.getContent()))));
        nextExpected.putIfAbsent(initiatorId, 1);
    
        PriorityQueue<Message> queue = pendingMessages.get(initiatorId);
        queue.add(m);
    
        while (!queue.isEmpty() && Integer.parseInt(queue.peek().getContent()) == nextExpected.get(initiatorId)) {
            Message nextMessage = queue.poll();
            applicationLayer.deliver(nextMessage.getSignature(), nextMessage.getContent());
            nextExpected.put(initiatorId, nextExpected.get(initiatorId) + 1);
        }
    }

    public void flushOutput() {
        applicationLayer.flushOutput();
    }
}