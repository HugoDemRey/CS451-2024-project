package cs451.Milestone1.Host;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import cs451.Constants;
import cs451.Host;
import cs451.Milestone1.Message;

import static cs451.Constants.*;

public class Sender extends ActiveHost {
    private Host host;
    private DatagramSocket socket;
    private InetAddress receiverAddress;
    private int receiverPort;

    // Sliding window variables
    private static final int WINDOW_SIZE = 5;
    private int base = 0;
    private int nextSeqNum = 0;
    private Map<Integer, Message> window = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private Map<Integer, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    @Override
    public boolean populate(String idString, String ipString, String portString, String outputFilePath) {
        boolean result = super.populate(idString, ipString, portString, outputFilePath);
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT);
            // Initialize receiverAddress and receiverPort appropriately
            // For example, set them via additional parameters or configuration
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return result;
    }

    public Host host() {
        return host;
    }

    public void sendSlidingWindow(List<Message> messages, Host receiver) {
        try {
            receiverAddress = InetAddress.getByName(receiver.getIp());
            receiverPort = receiver.getPort();

            // Start a thread to listen for ACKs
            new Thread(this::listenForAcks).start();

            // Iterate through the messages and send them according to window size
            for (Message message : messages) {
                synchronized (this) {
                    while (nextSeqNum >= base + WINDOW_SIZE) {
                        // Wait until there is space in the window
                        wait();
                    }

                    // Assign sequence number and send the packet
                    message.setSeqNum(nextSeqNum);
                    sendPacket(message);
                    window.put(nextSeqNum, message);

                    // Start timer for the packet
                    startTimer(nextSeqNum);

                    nextSeqNum++;
                }
            }

            // Wait until all packets are acknowledged
            synchronized (this) {
                while (base < nextSeqNum) {
                    wait();
                }
            }

            // Shutdown scheduler
            scheduler.shutdown();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }

    private void sendPacket(Message message) throws Exception {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4 + message.getContent().getBytes(StandardCharsets.UTF_8).length + 4);
        byteBuffer.putInt(message.getSeqNum()); // Sequence number
        byteBuffer.put(message.getContent().getBytes(StandardCharsets.UTF_8));
        byteBuffer.putInt(message.getSenderId());

        byte[] packetData = byteBuffer.array();
        DatagramPacket packet = new DatagramPacket(packetData, packetData.length, receiverAddress, receiverPort);
        socket.send(packet);
        write("b " + message.getContent());
        System.out.println("Sent message with SeqNum " + message.getSeqNum());
    }

    private void listenForAcks() {
        try {
            byte[] ackData = new byte[4]; // Assuming ACK contains only the sequence number
            DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);

            while (true) {
                socket.receive(ackPacket);
                ByteBuffer byteBuffer = ByteBuffer.wrap(ackPacket.getData(), 0, ackPacket.getLength());
                int ackSeqNum = byteBuffer.getInt();
                System.out.println("Received ACK for SeqNum " + ackSeqNum);

                synchronized (this) {
                    if (ackSeqNum >= base) {
                        // Slide the window
                        for (int seq = base; seq <= ackSeqNum; seq++) {
                            window.remove(seq);
                            // Cancel the timer
                            ScheduledFuture<?> timer = timers.get(seq);
                            if (timer != null) {
                                timer.cancel(false);
                                timers.remove(seq);
                            }
                        }
                        base = ackSeqNum + 1;
                        notifyAll(); // Notify sender to send more packets
                    }
                }

                if (base == nextSeqNum) {
                    break; // All packets are acknowledged
                }
            }
        } catch (SocketTimeoutException e) {
            // Handle timeout if necessary
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startTimer(int seqNum) {
        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            try {
                synchronized (Sender.this) {
                    if (window.containsKey(seqNum)) {
                        System.out.println("Timeout for SeqNum " + seqNum + ", retransmitting...");
                        sendPacket(window.get(seqNum));
                        startTimer(seqNum); // Restart the timer
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, TIMEOUT, TimeUnit.MILLISECONDS);

        timers.put(seqNum, timer);
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
