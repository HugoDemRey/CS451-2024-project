package cs451.Milestone1.Host;

import java.net.DatagramSocket;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import cs451.Host;
import cs451.Milestone1.Message;
import cs451.Milestone1.MessageReceiverPair;
import cs451.Milestone1.Packet;

import static cs451.Constants.*;

public class SenderWorks extends ActiveHost {
    private DatagramSocket socket;
    private final BlockingQueue<MessageReceiverPair> messageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2); // One for sending, one for ACKs

    // Sliding window variables
    private static AtomicInteger WINDOW_SIZE = new AtomicInteger(STANDARD_WINDOW_SIZE); // Example window size
    private static AtomicInteger TIMEOUT = new AtomicInteger(STANDARD_TIMEOUT); // Example timeout in milliseconds
    private final AtomicInteger nextSeqNum = new AtomicInteger(1); // The next sequence number to be used
    private final Map<Integer, Packet> window = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<Integer, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    @Override
    public boolean populate(String idString, String ipString, String portString, String outputFilePath) {
        boolean result = super.populate(idString, ipString, portString, outputFilePath);
        try {
            socket = new DatagramSocket();
            // Start the consumer thread for sending messages
            executor.execute(this::processQueue);
            // Start the listener thread for receiving ACKs
            executor.execute(this::listenForAcks);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return result;
    }

    /**
     * Adds a message and its receiver to the sending queue.
     *
     * @param message  The message to send.
     * @param receiver The intended receiver of the message.
     */
    public void enqueueMessage(Message message, Host receiver) {
        try {
            messageQueue.put(new MessageReceiverPair(message, receiver));
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

                MessageReceiverPair firstPair = messageQueue.take(); // Blocks if queue is empty
                Host receiver = firstPair.getReceiver();
                Message[] messagesToSend = new Message[MAX_MESSAGES_PER_PACKET];
                messagesToSend[0] = firstPair.getMessage();
                int nbTreated = 1;
                // We assume this should not be greater than MAX_PAYLOAD_SIZE
                int currentPayloadSizeBytes = 4 * Integer.BYTES + messagesToSend[0].getContent().getBytes(StandardCharsets.UTF_8).length;

                /*
                * Packet Format : 
                * [seqNum (4 Bytes)] 
                * [nbMessages (4 Bytes)]
                * [contentSize1 (4 Bytes)] 
                * [content1 (contentSize1 Bytes)]
                * [contentSize 2 (4 Bytes)]
                * [content2 (contentSize2 Bytes)]
                * ...
                * [senderId (4 Bytes)]
                */


                for (int i = 1; i < MAX_MESSAGES_PER_PACKET; i++) {
                    MessageReceiverPair nextPair = messageQueue.peek();
                    if (nextPair == null) break;
                    
                    int nextPayloadSizeBytes = Integer.BYTES + nextPair.getMessage().getContent().getBytes(StandardCharsets.UTF_8).length;
                    if (nextPair.getReceiver() != receiver || (currentPayloadSizeBytes + nextPayloadSizeBytes) > MAX_PAYLOAD_SIZE) break;

                    messageQueue.remove();

                    messagesToSend[i] = nextPair.getMessage();
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
                messagesToSend[0].setSeqNum(seqNum);
                Packet packet = new Packet(messagesToSend, nbTreated, receiver);
                window.put(seqNum, packet);
                sendPacket(messagesToSend, nbTreated, receiver, true);

                // Start timer for the packet
                startTimer(seqNum);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Sender queue processing interrupted: " + e.getMessage());
        }
    }


    private AtomicInteger nbUnacks = new AtomicInteger(0);
    private AtomicInteger nbAcks = new AtomicInteger(0);
    private final int windowIncreaseChangeThreshold = 100;
    private final int windowDecreaseChangeThreshold = 100;



    private void adaptSlidingWindow(boolean isAck) {

        if (isAck) TIMEOUT.set(Math.max(STANDARD_TIMEOUT, TIMEOUT.get() - 10));
        else TIMEOUT.set(Math.min(TIMEOUT.get() + 1, MAX_TIMEOUT));
        //write(TIMEOUT.get() + "\n");

        if (isAck && nbAcks.incrementAndGet() >= windowIncreaseChangeThreshold) {
            nbAcks.set(0);
            WINDOW_SIZE.set(Math.min(WINDOW_SIZE.get() + 800, MAX_WINDOW_SIZE));
            write("I : " + WINDOW_SIZE.get() + " | " + TIMEOUT.get() + "\n");

        } else if (!isAck && nbUnacks.incrementAndGet() >= windowDecreaseChangeThreshold) {
            nbUnacks.set(0);
            WINDOW_SIZE.set(Math.max((int) (WINDOW_SIZE.get() - 200), STANDARD_WINDOW_SIZE));
            write("D : " + WINDOW_SIZE.get() + " | " + TIMEOUT.get() + "\n");
        }

    }


    /**
     * Sends a packet containing the message to the specified receiver.
     *
     * @param messages  The message to send.
     * @param receiver The intended receiver.
     * @param isFirstTime Whether this is the first time the packet is being sent.
     * @param nbMessages The number of messages in the packet.
     */
    private void sendPacket(Message[] messages, int nbMessages, Host receiver, boolean isFirstTime) {
        try {

            InetAddress receiverAddress = InetAddress.getByName(receiver.getIp());
            int receiverPort = receiver.getPort();

            byte[][] contentBytes = new byte[nbMessages][];
            int[] contentSizeBytes = new int[nbMessages];
            int contentTotalSize = 0;
            for (int i = 0; i < nbMessages; i++) {
                contentBytes[i] = messages[i].getContent().getBytes(StandardCharsets.UTF_8);
                contentSizeBytes[i] = contentBytes[i].length;
                contentTotalSize += Integer.BYTES + contentSizeBytes[i];
            }

            /*
             * Packet Format : 
             * [seqNum (4 Bytes)] 
             * [nbMessages (4 Bytes)]
             * [contentSize1 (4 Bytes)] 
             * [content1 (contentSize1 Bytes)]
             * [contentSize 2 (4 Bytes)]
             * [content2 (contentSize2 Bytes)]
             * ...
             * [senderId (4 Bytes)]
             */

            // Allocation of the byte buffer
            ByteBuffer byteBuffer = ByteBuffer.allocate(3*Integer.BYTES + contentTotalSize);

            // The sequence number of the packet (used for acks) is the sequence number of the first message
            byteBuffer.putInt(messages[0].getSeqNum());
            byteBuffer.putInt(nbMessages);
            for (int i = 0; i < nbMessages; i++) {
                byteBuffer.putInt(contentSizeBytes[i]);
                byteBuffer.put(contentBytes[i]);
            }
            byteBuffer.putInt(messages[0].getSenderId());

            byte[] packetData = byteBuffer.array();
            DatagramPacket packet = new DatagramPacket(packetData, packetData.length, receiverAddress, receiverPort);
            socket.send(packet);
            if (isFirstTime) {
                StringBuilder toWriteBuilder = new StringBuilder("");
                for (int i = 0; i < nbMessages; i++) {
                    toWriteBuilder.append("b " + messages[i].getContent() + "\n");
                }
                //write(toWriteBuilder.toString());
                //System.out.println("↪ | p" + this.getId() + " → p" + receiver.getId() + " : seq n." + messages[0].getSeqNum() + " | message qty = " + nbMessages);
            } else {
                //System.out.println("⟳ | p" + this.getId() + " → p" + receiver.getId() + " : seq n." + messages[0].getSeqNum() + " | message qty = " + nbMessages);
            }
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
                byte[] ackData = new byte[3 * Integer.BYTES]; // [OriginalSenderId (4)] [senderId (4)] + [ackSeqNum (4)]
                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);
                socket.receive(ackPacket);

                ByteBuffer byteBuffer = ByteBuffer.wrap(ackPacket.getData(), 0, ackPacket.getLength());
                int ackSenderId = byteBuffer.getInt();  
                int ackOriginalSenderId = byteBuffer.getInt();
                int ackSeqNum = byteBuffer.getInt();

                
                // Check if the ACK corresponds to a message in the window
                Packet packet = window.get(ackSeqNum);
                if (packet != null && this.getId() == ackOriginalSenderId) {
                    // Remove the message from the window
                    window.remove(ackSeqNum);
                    // Cancel the timer
                    ScheduledFuture<?> timer = timers.remove(ackSeqNum);
                    if (timer != null) {
                        timer.cancel(false);
                    }
                    //System.out.println("\n✔ | p" + this.getId() + " ← p" + ackSenderId + " : seq n." + ackSeqNum + "\n");

                    // Adapt the sliding window
                    adaptSlidingWindow(true);

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
     * Starts a timer for the given sequence number. If the timer expires, the packet is retransmitted.
     *
     * @param seqNum The sequence number of the packet.
     */
    private void startTimer(int seqNum) {
        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            try {
                Packet packet = window.get(seqNum);
                if (packet != null) {
                    //System.out.println("Timeout for SeqNum " + seqNum + " Packet SeqNum " + packet.getMessages()[0].getSeqNum());
                    sendPacket(packet.getMessages(), packet.getNbMessages(), packet.getReceiver(), false);
                    startTimer(seqNum); // Restart the timer

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

    @Override
    public String toString() {
        return "Sender{" +
                "id=" + getId() +
                ", ip='" + getIp() + '\'' +
                ", port=" + getPort() +
                '}';
    }
}
