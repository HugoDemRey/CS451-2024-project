package cs451.Milestone2;

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

    private final Transceiver parentHost;
    
    public PerfectLinks(Transceiver parentHost) {
        this.parentHost = parentHost;
        // Start the consumer thread for sending messages
        executor.execute(this::processQueue);
        // TODO: Change and make one function listen() that listens for both ACKs and incoming messages
        executor.execute(this::listenForAcks);
    }

    /* SENDING PART */

    private DatagramSocket socket;
    private final BlockingQueue<Pair<Message, Host>> messageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2); // One for sending, one for ACKs
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
    private void processQueue() {
        try {
            while (true) {

                Pair<Message, Host> firstPair = messageQueue.take(); // Blocks if queue is empty
                Host receiver = firstPair.getSecond();
                Message[] messagesToSend = new Message[MAX_MESSAGES_PER_PACKET];
                messagesToSend[0] = firstPair.getFirst();
                int nbTreated = 1;
                // We assume this should not be greater than MAX_PAYLOAD_SIZE
                int currentPayloadSizeBytes = 5 * Integer.BYTES + messagesToSend[0].getContent().getBytes(StandardCharsets.UTF_8).length;

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
                * [senderId (4 Bytes)]
                */


                for (int i = 1; i < MAX_MESSAGES_PER_PACKET; i++) {
                    Pair<Message, Host> nextPair = messageQueue.peek();
                    if (nextPair == null) break;
                    
                    int nextPayloadSizeBytes = Integer.BYTES + nextPair.getFirst().getContent().getBytes(StandardCharsets.UTF_8).length;
                    if (nextPair.getSecond() != receiver || (currentPayloadSizeBytes + nextPayloadSizeBytes) > MAX_PAYLOAD_SIZE) break;

                    messageQueue.remove();

                    messagesToSend[i] = nextPair.getFirst();
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
    private void sendPacket(Packet packet, int sentCount) {
        try {

            InetAddress receiverAddress = InetAddress.getByName(packet.receiver().ip());
            int receiverPort = packet.receiver().port();

            byte[][] contentBytes = new byte[packet.nbMessages()][];
            int[] contentSizeBytes = new int[packet.nbMessages()];
            int contentTotalSize = 0;
            for (int i = 0; i < packet.nbMessages(); i++) {
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
             * [originalSenderId (4 Bytes)]
             * [lastSenderId (4 Bytes)]
             */

            // Allocation of the byte buffer
            ByteBuffer byteBuffer = ByteBuffer.allocate(5*Integer.BYTES + contentTotalSize);

            // The sequence number of the packet (used for acks) is the sequence number of the first message
            byteBuffer.putInt(packet.seqNum());
            byteBuffer.putInt(sentCount);
            byteBuffer.putInt(packet.nbMessages());
            for (int i = 0; i < packet.nbMessages(); i++) {
                byteBuffer.putInt(contentSizeBytes[i]);
                byteBuffer.put(contentBytes[i]);
            }
            byteBuffer.putInt(packet.messages()[0].getSenderId());
            byteBuffer.putInt(parentHost.id());

            byte[] packetData = byteBuffer.array();
            DatagramPacket physicalPacket = new DatagramPacket(packetData, packetData.length, receiverAddress, receiverPort);

            long startTime = System.currentTimeMillis();
            computedRTTs.computeIfAbsent(packet.seqNum(), k -> new ArrayList<Long>());
            computedRTTs.get(packet.seqNum()).add(startTime);
            socket.send(physicalPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Listens for ACKs from receivers and updates the sliding window accordingly.
     */
    private void listenForAcks() {
        try {
            while (true) {
                byte[] ackData = new byte[4 * Integer.BYTES]; // [OriginalSenderId (4)] [senderId (4)] + [ackSeqNum (4)]
                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);
                socket.receive(ackPacket);

                long endTime = System.currentTimeMillis();

                /*
                * ACK Packet Format :
                * [ackNum (4 Bytes)]
                * [sentCount (4 Bytes)]
                * [OriginalSenderId (4 Bytes)] // ID of the original packet sender.
                * [SenderId (4 Bytes)]
                */

                // Deserialize the ACK packet
                ByteBuffer byteBuffer = ByteBuffer.wrap(ackPacket.getData(), 0, ackPacket.getLength());
                int ackSeqNum = byteBuffer.getInt();
                int sentCount = byteBuffer.getInt();
                int originalSenderId = byteBuffer.getInt();
                int lastSenderId = byteBuffer.getInt(); // Do not remove
                
                // Check if the ACK corresponds to a message in the window
                Packet packet = window.get(ackSeqNum);



                if (packet != null && parentHost.id() == originalSenderId) {

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
                    //System.out.println("\n✔ | p" + this.getId() + " ← p" + ackSenderId + " : seq n." + ackSeqNum + "\n");

                    synchronized (window) {
                        window.notifyAll();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
                    //System.out.println("Timeout for SeqNum " + seqNum + " Packet SeqNum " + packet.getMessages()[0].getSeqNum());
                    sendPacket(packet, sentCount + 1);
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
            //System.out.println("Sender shutdown complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* RECEIVING PART */

    /**
     * Starts listening for incoming messages and sends ACKs accordingly.
     */
    public void listen() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(parentHost.port());
            byte[] buffer = new byte[MAX_PACKET_SIZE_BYTES];
            //System.out.println("Host " + getId() + " is listening on " + getIp() + "/" + getPort());

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                
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
                * [originalSenderId (4 Bytes)]
                * [lastSenderId (4 Bytes)]
                */
                
                
                // Deserialize Packet
                ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                int seqNb = byteBuffer.getInt();
                int sentCount = byteBuffer.getInt();
                int nbMessages = byteBuffer.getInt();

                byte[][] contentBytes = new byte[nbMessages][];
                String[] content = new String[nbMessages];
                for (int i = 0; i < nbMessages; i++) {
                    int contentSizeBytes = byteBuffer.getInt();
                    contentBytes[i] = new byte[contentSizeBytes];
                    byteBuffer.get(contentBytes[i]);
                    content[i] = new String(contentBytes[i], StandardCharsets.UTF_8);
                }
                int originalSenderId = byteBuffer.getInt();
                int lastSenderId = byteBuffer.getInt();
                
                
                // Send individual ACK regardless of duplication
                sendAck(socket, packet.getAddress(), packet.getPort(), lastSenderId, seqNb, sentCount);

                for (int i = 0; i < nbMessages; i++) {
                    parentHost.bebDeliver(lastSenderId, new Message(originalSenderId, content[i]));
                }
                
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                //System.out.println("Receiver socket closed");
            }
        }
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
    private void sendAck(DatagramSocket socket, InetAddress address, int port, int originalSenderId, int ackNum, int sentCount) {
        try {
            /*
             * ACK Packet Format :
             * [ackNum (4 Bytes)]
             * [sentCount (4 Bytes)]
             * [OriginalSenderId (4 Bytes)] // ID of the original packet sender.
             * [SenderId (4 Bytes)]
             */

            // Create the ACK packet
            ByteBuffer ackBuffer = ByteBuffer.allocate(4 * Integer.BYTES);
            ackBuffer.putInt(ackNum);
            ackBuffer.putInt(sentCount);
            ackBuffer.putInt(originalSenderId);
            ackBuffer.putInt(parentHost.id());

            byte[] ackData = ackBuffer.array();
            DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, address, port);
            socket.send(ackPacket);
            //System.out.println("↪ | p" + this.getId() + " → p" + originalSenderId + " : seq n." + ackNum + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
