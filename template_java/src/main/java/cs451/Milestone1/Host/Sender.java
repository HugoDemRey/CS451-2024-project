package cs451.Milestone1.Host;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import cs451.Constants;
import cs451.Host;
import cs451.Milestone1.Message;

import static cs451.Constants.*;

public class Sender extends ActiveHost {
    private DatagramSocket socket;
    private final BlockingQueue<MessageReceiverPair> messageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2); // One for sending, one for ACKs

    // Sliding window variables
    private static final int WINDOW_SIZE = 5; // Example window size
    private final AtomicInteger nextSeqNum = new AtomicInteger(0); // The next sequence number to be used
    private final Map<Integer, MessageReceiverPair> window = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<Integer, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    public Sender() {
        // Initialize any necessary components
    }

    @Override
    public boolean populate(String idString, String ipString, String portString, String outputFilePath) {
        boolean result = super.populate(idString, ipString, portString, outputFilePath);
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT);
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
                MessageReceiverPair pair = messageQueue.take(); // Blocks if queue is empty
                Host receiver = pair.getReceiver();
                Message message = pair.getMessage();

                // Ensure window size
                while (window.size() >= WINDOW_SIZE) {
                    synchronized (window) {
                        window.wait();
                    }
                }

                // Assign sequence number and send the packet
                int seqNum = nextSeqNum.getAndIncrement();
                message.setSeqNum(seqNum);
                window.put(seqNum, pair);
                sendPacket(message, receiver);

                // Start timer for the packet
                startTimer(seqNum);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Sender queue processing interrupted: " + e.getMessage());
        }
    }

    /**
     * Sends a packet containing the message to the specified receiver.
     *
     * @param message  The message to send.
     * @param receiver The intended receiver.
     */
    private void sendPacket(Message message, Host receiver) {
        try {
            InetAddress receiverAddress = InetAddress.getByName(receiver.getIp());
            int receiverPort = receiver.getPort();

            byte[] contentBytes = message.getContent().getBytes(StandardCharsets.UTF_8);
            int contentSizeBytes = contentBytes.length;

            // Allocate bytes: [seqNum (4)] + [content size (4)] + [content] + [senderId (4)]
            ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 4 + contentSizeBytes + 4);
            byteBuffer.putInt(message.getSeqNum());
            byteBuffer.putInt(contentSizeBytes);
            byteBuffer.put(contentBytes);
            byteBuffer.putInt(message.getSenderId());

            byte[] packetData = byteBuffer.array();
            DatagramPacket packet = new DatagramPacket(packetData, packetData.length, receiverAddress, receiverPort);
            socket.send(packet);
            write("b " + message.getContent());
            System.out.println("Sent message with SeqNum " + message.getSeqNum() + " to " + receiver.getIp() + "/" + receiverPort);
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
                byte[] ackData = new byte[8]; // [senderId (4)] + [ackSeqNum (4)]
                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);
                socket.receive(ackPacket);

                ByteBuffer byteBuffer = ByteBuffer.wrap(ackPacket.getData(), 0, ackPacket.getLength());
                int ackSenderId = byteBuffer.getInt();
                int ackSeqNum = byteBuffer.getInt();

                System.out.println("Received ACK for Sender " + ackSenderId + " SeqNum " + ackSeqNum);

                // Check if the ACK corresponds to a message in the window
                MessageReceiverPair pair = window.get(ackSeqNum);
                if (pair != null && pair.getMessage().getSenderId() == ackSenderId) {
                    // Remove the message from the window
                    window.remove(ackSeqNum);
                    // Cancel the timer
                    ScheduledFuture<?> timer = timers.remove(ackSeqNum);
                    if (timer != null) {
                        timer.cancel(false);
                    }
                    // Notify any waiting threads that window has space
                    synchronized (window) {
                        window.notifyAll();
                    }
                    System.out.println("ACK processed for SeqNum " + ackSeqNum);
                } else {
                    System.out.println("Received ACK for unknown SeqNum " + ackSeqNum + " from Sender " + ackSenderId);
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
                MessageReceiverPair pair = window.get(seqNum);
                if (pair != null) {
                    System.out.println("Timeout for SeqNum " + seqNum + ", retransmitting...");
                    sendPacket(pair.getMessage(), pair.getReceiver());
                    startTimer(seqNum); // Restart the timer
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, TIMEOUT, TimeUnit.MILLISECONDS);

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
            System.out.println("Sender shutdown complete.");
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
