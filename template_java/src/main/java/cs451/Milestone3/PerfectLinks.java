package cs451.Milestone3;

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
import cs451.Milestone1.Host.HostParams;

/**
 * The PerfectLinks class is responsible for sending and receiving messages between hosts using the perfect links abstraction.
 * Properties:
 *    - PL1 (Reliable delivery): If a correct process p sends a message m to a correct process q, then q eventually delivers m.
 *    - PL2 (No duplication): No message is delivered more than once.
 *    - PL3 (No creation): If some process q delivers a message m with sender p, then q has previously sent or received m.
 * 
 * All properties are directly handled in this class.
 */
public class PerfectLinks extends Host {

    private final Lattice lattice;
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
    
    public PerfectLinks(HostParams hostparams, Lattice lattice) {

        super.populate(hostparams);
        this.lattice = lattice;

        try {
            socket = new DatagramSocket(port());

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
                Message[] messagesToSend = new Message[MAX_MESSAGES_PER_PACKET];
                messagesToSend[0] = firstPair.first();
                // We assume this should not be greater than MAX_PAYLOAD_SIZE
                int currentPayloadSizeBytes = Character.BYTES +  6 * Integer.BYTES + messagesToSend[0].getContent().getBytes(StandardCharsets.UTF_8).length;

                /*
                * Physical Packet Format : 
                * [packetType (2 Byte)] 'M' 
                * [seqNum (4 Bytes)] 
                * [sentCount (4 Bytes)] 
                * [nbMessages (4 Bytes)] 
                * [contentSize1 (4 Bytes)] 
                * [content1 (contentSize1 Bytes)] 
                * [signature1 (4 Bytes)] 
                * [contentSize 2 (4 Bytes)]
                * [content2 (contentSize2 Bytes)]
                * [signature2 (4 Bytes)]
                * ...
                * [senderId (4 Bytes)] 
                */

                int addedMessages = 1;

                for (Pair<Message, Host> pair: messageQueue) {                    
                    // ContentSize(4), Content, Signature(4)
                    int nextPayloadSizeBytes = 2 * Integer.BYTES + pair.first().getContent().getBytes(StandardCharsets.UTF_8).length;
                    if (pair.second().id() != receiver.id() || (currentPayloadSizeBytes + nextPayloadSizeBytes) > MAX_PAYLOAD_SIZE) continue;
                    

                    messageQueue.remove(pair);

                    messagesToSend[addedMessages] = pair.first();
                    if (++addedMessages == MAX_MESSAGES_PER_PACKET) break;
                    currentPayloadSizeBytes += nextPayloadSizeBytes;
                }         

                // Ensure window size
                while (window.size() >= WINDOW_SIZE.get()) {
                    synchronized (window) {
                        window.wait();
                    }
                }

                // Assign sequence number and send the packet
                int seqNum = nextSeqNum.getAndIncrement();
                Packet packet = new Packet(seqNum, messagesToSend, addedMessages, receiver);
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

        ByteBuffer byteBuffer = Packet.serialize(packet, sentCount, this.id());

        byte[] packetData = byteBuffer.array();
        DatagramPacket physicalPacket = new DatagramPacket(packetData, packetData.length, receiverAddress, receiverPort);

        long startTime = System.currentTimeMillis();
        computedRTTs.computeIfAbsent(packet.seqNum(), k -> new ArrayList<Long>());
        try {
            List<Long> rttList = computedRTTs.get(packet.seqNum());
            if (rttList != null) rttList.add(startTime);
        } catch (NullPointerException e) {
            // can happen if we just removed the packet from the window
            e.printStackTrace();
        }

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
        * [ackWaiterId (4 Bytes)] // ID of the broadcaster that waits for the ACK.
        * [SenderId (4 Bytes)]
        */

        // Deserialize the ACK packet
        int ackSeqNum = buffer.getInt();
        int sentCount = buffer.getInt();
        int originalSenderId = buffer.getInt();
        int lastSenderId = buffer.getInt(); // Do not remove
        

        // Check if the ACK corresponds to a message in the window
        Packet packet = window.get(ackSeqNum);

        if (packet != null && this.id() == originalSenderId && packet.receiver().id() == lastSenderId) {

            // Update the RTT
            List<Long> rttList = computedRTTs.remove(ackSeqNum);;
            if (rttList != null) {
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
        * Physical Packet Format : 
        * [packetType (2 Byte)] 'M' (ALREADY READ)
        * [seqNum (4 Bytes)] 
        * [sentCount (4 Bytes)] 
        * [nbMessages (4 Bytes)] 
        * [contentSize1 (4 Bytes)] 
        * [content1 (contentSize1 Bytes)] 
        * [signature1 (4 Bytes)] 
        * [contentSize 2 (4 Bytes)]
        * [content2 (contentSize2 Bytes)]
        * [signature2 (4 Bytes)]
        * ...
        * [senderId (4 Bytes)] 
        */
        
        // Deserialize Packet
        int seqNb = buffer.getInt();
        int sentCount = buffer.getInt();
        int nbMessages = buffer.getInt();

        byte[][] contentBytes = new byte[nbMessages][];
        String[] content = new String[nbMessages];
        int[] signatures = new int[nbMessages];
        for (int i = 0; i < nbMessages; i++) {
            int contentSizeBytes = buffer.getInt();
            contentBytes[i] = new byte[contentSizeBytes];
            buffer.get(contentBytes[i]);
            content[i] = new String(contentBytes[i], StandardCharsets.UTF_8);
            signatures[i] = buffer.getInt();
        }
        int senderId = buffer.getInt();
        
        
        // Send individual ACK regardless of duplication
        sendAck(socket, packet.getAddress(), packet.getPort(), senderId, seqNb, sentCount);

        for (int i = 0; i < nbMessages; i++) {
            Message message = new Message(signatures[i], content[i]);
            lattice.bebDeliver(senderId, message);
        }
                
    }

    /**
     * Sends an ACK for a given sender and sequence number.
     *
     * @param socket   The DatagramSocket to send the ACK.
     * @param address  The address of the sender.
     * @param port     The port of the sender.
     * @param ackWaiterId The ID of the sender.
     * @param ackNum   The sequence number being acknowledged.
     */
    private void sendAck(DatagramSocket socket, InetAddress address, int port, int ackWaiterId, int ackNum, int sentCount) throws IOException{

        /*
        * ACK Packet Format :
        * [packetType (2 Bytes)] 'A'
        * [ackNum (4 Bytes)]
        * [sentCount (4 Bytes)]
        * [ackWaiterId (4 Bytes)] // ID of the broadcaster that waits for the ACK.
        * [SenderId (4 Bytes)]
        */

        // Create the ACK packet
        ByteBuffer ackBuffer = ByteBuffer.allocate(Character.BYTES + 4 * Integer.BYTES);
        ackBuffer.putChar(PACKET_TYPE_ACK);
        ackBuffer.putInt(ackNum);
        ackBuffer.putInt(sentCount);
        ackBuffer.putInt(ackWaiterId);
        ackBuffer.putInt(this.id());

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
