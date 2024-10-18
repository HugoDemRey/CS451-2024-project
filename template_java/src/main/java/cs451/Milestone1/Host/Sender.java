package cs451.Milestone1.Host;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import cs451.Host;
import cs451.Milestone1.Message;
import cs451.Milestone1.MessageReceiverPair;

import static cs451.Constants.*;

public class Sender extends ActiveHost {
    private DatagramSocket socket;
    private final BlockingQueue<MessageReceiverPair> messageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2); // One for sending, one for ACKs

    // Sliding window variables
    private static AtomicInteger WINDOW_SIZE = new AtomicInteger(5000); // Example window size
    private static AtomicInteger TIMEOUT = new AtomicInteger(700); // Example timeout in milliseconds
    private final AtomicInteger nextSeqNum = new AtomicInteger(1); // The next sequence number to be used
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
                while (window.size() >= WINDOW_SIZE.get()) {
                    synchronized (window) {
                        window.wait();
                    }
                }

                // Assign sequence number and send the packet
                int seqNum = nextSeqNum.getAndIncrement();
                message.setSeqNum(seqNum);
                window.put(seqNum, pair);
                sendPacket(message, receiver, true);

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

    private AtomicInteger nbDecreaseInARow = new AtomicInteger(0);    


    private void adaptSlidingWindow(boolean isAck) {
        if (isAck && nbAcks.incrementAndGet() >= windowIncreaseChangeThreshold) {
            nbAcks.set(0);

            WINDOW_SIZE.set(WINDOW_SIZE.get() * 2);
            TIMEOUT.set(700);
            System.out.println("Window size increased to " + WINDOW_SIZE.get() + " Timeout " + TIMEOUT.get());

        } else if (!isAck && nbUnacks.incrementAndGet() >= windowDecreaseChangeThreshold) {
            nbUnacks.set(0);
            WINDOW_SIZE.set(Math.max((int) (WINDOW_SIZE.get() / 1.5), 5000));
            TIMEOUT.set(TIMEOUT.get() + 50);
            System.out.println("Window size decreased to " + WINDOW_SIZE.get() + " Timeout " + TIMEOUT.get());
        }

    }


    /**
     * Sends a packet containing the message to the specified receiver.
     *
     * @param message  The message to send.
     * @param receiver The intended receiver.
     */
    private void sendPacket(Message message, Host receiver, boolean isFirstTime) {
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
            if (isFirstTime) {
                write("b " + message.getContent());
                System.out.println("↪ | p" + this.getId() + " → p" + receiver.getId() + " : seq n." + message.getContent());
            } else {
                System.out.println("⟳ | p" + this.getId() + " → p" + receiver.getId() + " : seq n." + message.getContent());
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


                //System.out.println("Received ACK for Sender " + ackSenderId + " SeqNum " + ackSeqNum);

                // Check if the ACK corresponds to a message in the window
                MessageReceiverPair pair = window.get(ackSeqNum);
                if (pair != null && pair.getMessage().getSenderId() == ackOriginalSenderId) {
                    // Remove the message from the window
                    window.remove(ackSeqNum);
                    // Cancel the timer
                    ScheduledFuture<?> timer = timers.remove(ackSeqNum);
                    if (timer != null) {
                        timer.cancel(false);
                    }
                    System.out.println("\n✔ | p" + this.getId() + " ← p" + ackSenderId + " : seq n." + ackSeqNum + "\n");

                    // Adapt the sliding window
                    adaptSlidingWindow(true);

                    synchronized (window) {
                        window.notifyAll();
                    }
                } else {
                    //System.out.println("Received ACK for unknown SeqNum " + ackSeqNum + " from Sender " + ackSenderId);
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
                    //System.out.println("Timeout for SeqNum " + seqNum + ", retransmitting...");
                    sendPacket(pair.getMessage(), pair.getReceiver(), false);
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
