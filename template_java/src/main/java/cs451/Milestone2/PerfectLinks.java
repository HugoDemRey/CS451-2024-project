package cs451.Milestone2;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import cs451.Host;
import static cs451.Constants.*;
import cs451.Milestone1.Message;
import cs451.Milestone1.Packet;
import cs451.Milestone1.Pair;

public class PerfectLinks {

    private final URB parentHost;
    private DatagramSocket socket;
    private final BlockingQueue<Pair<Message, Host>> messageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2); // One for sending, one for receiving
    private static AtomicInteger WINDOW_SIZE = new AtomicInteger(STANDARD_WINDOW_SIZE); // Example window size
    private static AtomicInteger TIMEOUT = new AtomicInteger(STANDARD_TIMEOUT); // Example timeout in milliseconds
    private final AtomicInteger nextSeqNum = new AtomicInteger(1); // The next sequence number to be used
    private final Map<Integer, Packet> window = new ConcurrentHashMap<>();
    private final Map<Integer, List<Long>> computedRTTs = new ConcurrentHashMap<>();
    private long estimatedRTT = STANDARD_TIMEOUT; // actual RTT in milliseconds
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<Integer, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final double ALPHA = 0.875;
    private final double BETA = 0.25;
    
    public PerfectLinks(URB parentHost) {
        this.parentHost = parentHost;

        try {
            socket = new DatagramSocket(parentHost.port());

            // can go up to 8 threads, here we only use 2.

            executor.execute(() -> {
                try {
                    processQueue();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            executor.execute(() -> {
                try {
                    listen();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            
        } catch (IOException e) {
            // All IO exceptions of the class are caught here.
            e.printStackTrace();
        } finally {
            // if (socket != null && !socket.isClosed()) {
            //     socket.close();
            // }
        }
        
    }

    /* SENDING PART */


    /**
     * Adds a message and its receiver to the sending queue.
     *
     * @param message  The message to send.
     * @param receiver The intended receiver of the message.
     */
    public void enqueueMessage(Message message, Host receiver) {
        try {
            messageQueue.put(new Pair<Message, Host>(message, receiver));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while enqueueing message: " + e.getMessage());
        }
    }

    /**
     * Continuously processes the message queue, sending messages according to the sliding window protocol.
     */
    private void processQueue() throws IOException {
        try {
            while (true) {

                Pair<Message, Host> firstPair = messageQueue.take(); // Blocks if queue is empty
                Host receiver = firstPair.second();
                int initiatorId = firstPair.first().getInitiatorId();
                Message[] messagesToSend = new Message[MAX_MESSAGES_PER_PACKET];
                messagesToSend[0] = firstPair.first();
                int nbTreated = 1;
                // We assume this should not be greater than MAX_PAYLOAD_SIZE
                int currentPayloadSizeBytes = Character.BYTES +  5 * Integer.BYTES + messagesToSend[0].getContent().getBytes(StandardCharsets.UTF_8).length;

                /*
                * Physical Packet Format : 
                * [seqNum (4 Bytes)]
                * [sentCount (4 Bytes)] 
                * [nbMessages (4 Bytes)]
                * [contentSize1 (4 Bytes)] 
                * [content1 (contentSize1 Bytes)]
                * [contentSize 2 (4 Bytes)]
                * [content2 (contentSize2 Bytes)]
                * ...
                * [originalSenderId (4 Bytes)]
                * [lastSenderId (4 Bytes)]
                */


                for (int i = 1; i < MAX_MESSAGES_PER_PACKET; i++) {
                    Pair<Message, Host> nextPair = messageQueue.peek();
                    if (nextPair == null) break;
                    
                    int nextPayloadSizeBytes = Integer.BYTES + nextPair.first().getContent().getBytes(StandardCharsets.UTF_8).length;
                    if (nextPair.second() != receiver || nextPair.first().getInitiatorId() != initiatorId || (currentPayloadSizeBytes + nextPayloadSizeBytes) > MAX_PAYLOAD_SIZE) break;

                    messageQueue.remove();

                    messagesToSend[i] = nextPair.first();
                    currentPayloadSizeBytes += nextPayloadSizeBytes;
                    nbTreated++;
                }                

                // Ensure window size
                while (window.size() >= WINDOW_SIZE.get()) {
                    synchronized (window) {
                        window.wait();
                    }
                }

                // Assign sequence number and send the packet
                int seqNum = nextSeqNum.getAndIncrement();
                Packet packet = new Packet(seqNum, messagesToSend, nbTreated, receiver);
                window.put(seqNum, packet);
                sendPacket(packet, 0);

                // Start timer for the packet
                startTimer(seqNum, 0);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Sender queue processing interrupted: " + e.getMessage());
        }
    }

    /**
     * Sends a packet containing the message to the specified receiver.
     *
     * @param messages  The message to send.
     * @param receiver The intended receiver.
     * @param sentCount Whether this is the first time the packet is being sent.
     * @param nbMessages The number of messages in the packet.
     * @param sentCount The number of times the packets has already been sent (for retransmissions).
     */
    private void sendPacket(Packet packet, int sentCount) throws IOException {


        InetAddress receiverAddress = InetAddress.getByName(packet.receiver().ip());
        int receiverPort = packet.receiver().port();

        byte[][] contentBytes = new byte[packet.nbMessages()][];
        int[] contentSizeBytes = new int[packet.nbMessages()];
        int contentTotalSize = 0;
        for (int i = 0; i < packet.nbMessages(); i++) {
            //parentHost.debug("s " + packet.messages()[i].getInitiatorId() + " " + packet.messages()[i].getContent() + " seqNum: " + packet.seqNum() + "\n");
            contentBytes[i] = packet.messages()[i].getContent().getBytes(StandardCharsets.UTF_8);
            contentSizeBytes[i] = contentBytes[i].length;
            contentTotalSize += Integer.BYTES + contentSizeBytes[i];
        }

        /*
        * Packet Format : 
        * [seqNum (4 Bytes)]
        * [sentCount (4 Bytes)] 
        * [nbMessages (4 Bytes)]
        * [contentSize1 (4 Bytes)] 
        * [content1 (contentSize1 Bytes)]
        * [contentSize 2 (4 Bytes)]
        * [content2 (contentSize2 Bytes)]
        * ...
        * [messageInitiatorId (4 Bytes)]
        * [lastSenderId (4 Bytes)]
        */

        // Allocation of the byte buffer
        ByteBuffer byteBuffer = ByteBuffer.allocate(Character.BYTES + 5*Integer.BYTES + contentTotalSize);

        // The sequence number of the packet (used for acks) is the sequence number of the first message
        byteBuffer.putChar(PACKET_TYPE_CONTENT);
        byteBuffer.putInt(packet.seqNum());
        byteBuffer.putInt(sentCount);
        byteBuffer.putInt(packet.nbMessages());
        for (int i = 0; i < packet.nbMessages(); i++) {
            byteBuffer.putInt(contentSizeBytes[i]);
            byteBuffer.put(contentBytes[i]);
        }
        byteBuffer.putInt(packet.messages()[0].getInitiatorId());
        byteBuffer.putInt(parentHost.id());

        byte[] packetData = byteBuffer.array();
        DatagramPacket physicalPacket = new DatagramPacket(packetData, packetData.length, receiverAddress, receiverPort);

        long startTime = System.currentTimeMillis();
        computedRTTs.computeIfAbsent(packet.seqNum(), k -> new ArrayList<Long>());
        try {
            computedRTTs.get(packet.seqNum()).add(startTime);
        } catch (NullPointerException e) {
            // can happen if we just removed the packet from the window
        }
        // System.out.print("S | p" + parentHost.id() + " → p" + packet.receiver().id() + " : seq n." + packet.seqNum());
        // System.out.print(" - Packet Content = [");
        // for (int i = 0; i < packet.nbMessages(); i++) {
        //     System.out.print(" " + packet.messages()[i] + " ");
        // }
        // System.out.print("]\n\n");

        socket.send(physicalPacket);
    }

    /**
     * Listens for ACKs from receivers and updates the sliding window accordingly.
     */
    private void handleAck(ByteBuffer buffer) {

        long endTime = System.currentTimeMillis();
        /*
        * ACK Packet Format :
        * /!\ ([packetType (1 byte)] - ALREADY READ)
        *
        * [ackNum (4 Bytes)]
        * [sentCount (4 Bytes)]
        * [InitiatorHostId (4 Bytes)]
        * [OriginalSenderId (4 Bytes)] // ID of the original packet sender.
        * [lastSenderId (4 Bytes)]
        */

        // Deserialize the ACK packet
        int ackSeqNum = buffer.getInt();
        int sentCount = buffer.getInt();
        int initiatorHostId = buffer.getInt();
        int originalSenderId = buffer.getInt();
        int lastSenderId = buffer.getInt(); // Do not remove
        

        // Check if the ACK corresponds to a message in the window
        Packet packet = window.get(ackSeqNum);

        if (packet != null && parentHost.id() == originalSenderId) {
            
            // for (int i = 0; i < packet.nbMessages(); i++) {
            //     parentHost.debug("ACK " + initiatorHostId + " " + packet.messages()[i].getContent() + " seqNum: " + ackSeqNum + "\n");
            // }

            // Update the RTT
            List<Long> rttList = computedRTTs.remove(ackSeqNum);;
            if (rttList != null) {
                //write("seqNum : " + ackSeqNum + " packet n." + sentCount + " received.\n");
                //write("RTT : " + (endTime - rttList.get(sentCount)) + " list = " + rttList + "\n");
                long startTime = rttList.get(sentCount);
                adaptTimeout(endTime - startTime);
            }

            // Adapt the sliding window
            adaptSlidingWindow(true);

            // Remove the message from the window
            window.remove(ackSeqNum);
            // Cancel the timer
            ScheduledFuture<?> timer = timers.remove(ackSeqNum);
            if (timer != null) {
                timer.cancel(false);
            }
            // System.out.print("\n✔ | p" + parentHost.id() + " ← p" + lastSenderId + " : seq n." + ackSeqNum);
            // System.out.print(" - Packet Content = [");
            // for (int i = 0; i < packet.nbMessages(); i++) {
            //     System.out.print(" " + packet.messages()[i] + " ");
            // }
            // System.out.print("]\n\n");
            

            synchronized (window) {
                window.notifyAll();
            }
        }
    }

    /**
 * Adapt the timeout value based on the sampleRTT.
 * @param sampleRTT The sample RTT to adapt the timeout value.
 */
    private void adaptTimeout(long sampleRTT) {

        long smoothedRTT = (long) (ALPHA * estimatedRTT + (1 - ALPHA) * sampleRTT);
        estimatedRTT = smoothedRTT;

        long deviation = Math.abs(sampleRTT - smoothedRTT);
        TIMEOUT.set((int) (smoothedRTT + 4 * BETA * deviation));

        //write("SampleRTT : " + sampleRTT + " | EstimatedRTT : " + estimatedRTT + " | Deviation : " + deviation + " | Timeout : " + TIMEOUT.get() + " | Window Size : " + WINDOW_SIZE.get() + "\n\n");

    }
    
    /**
     * Adapt the sliding window size based on the ACKs received.
     * @param isAck Whether the packet was acknowledged.
     */
    private void adaptSlidingWindow(boolean isAck) {

        if (isAck) {
            int newWindowSize = (int) (WINDOW_SIZE.get() * GROWING_FACTOR);
            WINDOW_SIZE.set(Math.min(newWindowSize, MAX_WINDOW_SIZE));

        } else {
            int newWindowSize = (int) (WINDOW_SIZE.get() * SHRINKING_FACTOR);
            WINDOW_SIZE.set(Math.max(newWindowSize, MIN_WINDOW_SIZE));
        }

    }

    /**
     * Starts a timer for the given sequence number. If the timer expires, the packet is retransmitted.
     *
     * @param seqNum The sequence number of the packet.
     */
    private void startTimer(int seqNum, int sentCount) {
        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            try {
                Packet packet = window.get(seqNum);
                if (packet != null) {
                    sendPacket(packet, sentCount + 1);
                    // for (int i = 0; i < packet.nbMessages(); i++) {
                    //     parentHost.debug("s " + packet.messages()[i].getInitiatorId() + " " + packet.messages()[i].getContent() + " seqNum: " + seqNum + "\n");
                    // }
                    startTimer(seqNum, sentCount + 1); // Restart the timer
                    // Adapt the sliding window
                    adaptSlidingWindow(false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, TIMEOUT.get(), TimeUnit.MILLISECONDS);

        timers.put(seqNum, timer);
    }

    /**
     * Gracefully shuts down the sender, closing sockets and executors.
     */
    public void shutdown() {
        try {
            executor.shutdownNow();
            scheduler.shutdownNow();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* RECEIVING PART */

    /**
     * Starts listening for incoming messages and sends ACKs accordingly.
     */
    public void handleIncomingMessage(ByteBuffer buffer, DatagramPacket packet) throws IOException {

                
        /*
        * Packet Format : 
        * ( /!\ [packet_type (1 Byte)] ALREADY READ)
        *
        * [seqNum (4 Bytes)] 
        * [sentCount (4 Bytes)]
        * [nbMessages (4 Bytes)]
        * [contentSize1 (4 Bytes)] 
        * [content1 (contentSize1 Bytes)]
        * [contentSize 2 (4 Bytes)]
        * [content2 (contentSize2 Bytes)]
        * ...
        * [initiatorHostId (4 Bytes)]
        * [lastSenderId (4 Bytes)]
        */
        
        // Deserialize Packet
        int seqNb = buffer.getInt();
        int sentCount = buffer.getInt();
        int nbMessages = buffer.getInt();

        byte[][] contentBytes = new byte[nbMessages][];
        String[] content = new String[nbMessages];
        for (int i = 0; i < nbMessages; i++) {
            int contentSizeBytes = buffer.getInt();
            contentBytes[i] = new byte[contentSizeBytes];
            buffer.get(contentBytes[i]);
            content[i] = new String(contentBytes[i], StandardCharsets.UTF_8);
        }
        int initiatorHostId = buffer.getInt();
        int lastSenderId = buffer.getInt();
        
        

        
        // Send individual ACK regardless of duplication
        sendAck(socket, packet.getAddress(), packet.getPort(), initiatorHostId, lastSenderId, seqNb, sentCount);

        //System.out.print("R | p" + parentHost.id() + " ← p" + lastSenderId + " : seq n." + seqNb);
        //System.out.print(" - Packet Content = [");
        for (int i = 0; i < nbMessages; i++) {
            Message message = new Message(initiatorHostId, content[i]);
            //System.out.print(" " + message + " ");
            //parentHost.debug("r " + initiatorHostId + " " + content[i] + " seqNum: " + seqNb + "\n");
            parentHost.bebDeliver(lastSenderId, message);
        }
        //System.out.print("]\n\n");
                
    }

    /**
     * Sends an ACK for a given sender and sequence number.
     *
     * @param socket   The DatagramSocket to send the ACK.
     * @param address  The address of the sender.
     * @param port     The port of the sender.
     * @param originalSenderId The ID of the sender.
     * @param ackNum   The sequence number being acknowledged.
     */
    private void sendAck(DatagramSocket socket, InetAddress address, int port, int initiatorHostId, int originalSenderId, int ackNum, int sentCount) throws IOException{

        /*
            * ACK Packet Format :
            * [packetType (2 Bytes)] 'A'
            * [ackNum (4 Bytes)]
            * [sentCount (4 Bytes)]
            * [InitiatorHostId (4 Bytes)]
            * [OriginalSenderId (4 Bytes)] // ID of the broadcaster that waits for the ACK.
            * [SenderId (4 Bytes)]
            */

        // Create the ACK packet
        ByteBuffer ackBuffer = ByteBuffer.allocate(Character.BYTES + 5 * Integer.BYTES);
        ackBuffer.putChar(PACKET_TYPE_ACK);
        ackBuffer.putInt(ackNum);
        ackBuffer.putInt(sentCount);
        ackBuffer.putInt(initiatorHostId);
        ackBuffer.putInt(originalSenderId);
        ackBuffer.putInt(parentHost.id());

        byte[] ackData = ackBuffer.array();
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, address, port);
        socket.send(ackPacket);
    }

    /* COMMON PART */

    /**
     * Can be multi-threaded to improve performance.
     */
    private void listen() throws IOException {
        

        while (true) {
            byte[] buffer = new byte[MAX_PACKET_SIZE_BYTES];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            /* ACK Packet Format :
            * 
            * [packetType (2 Bytes)] 'A'
            * ...
            */
            
            /* Packet Format : 
            *
            * [packetType (2 Byte)] 'M'
            * ...
            */
            
            
            // Deserialize Packet
            ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
            char packetType = byteBuffer.getChar();

            switch (packetType) {
                case PACKET_TYPE_ACK:
                    handleAck(byteBuffer);
                    break;
                case PACKET_TYPE_CONTENT:
                    handleIncomingMessage(byteBuffer, packet);
                    break;
            }
            
        }
    }

}
