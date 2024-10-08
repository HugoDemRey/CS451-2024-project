package cs451.Milestone1.Host;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cs451.Milestone1.Message;

import static cs451.Constants.*;

public class Receiver extends ActiveHost {
    // Map to keep track of expected sequence numbers per sender if in-order delivery is desired
    // For out-of-order delivery, this can be omitted or used for tracking purposes
    private final Map<Integer, Integer> expectedSeqNums = new ConcurrentHashMap<>();

    // To store out-of-order messages if needed for future in-order delivery
    // private final Map<Integer, Map<Integer, Message>> receivedMessages = new ConcurrentHashMap<>();

    @Override
    public boolean populate(String idString, String ipString, String portString, String outputFilePath) {
        boolean result = super.populate(idString, ipString, portString, outputFilePath);
        return result;
    }

    /**
     * Starts listening for incoming messages and sends ACKs accordingly.
     */
    public void listenWithSlidingWindow() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(getPort());
            byte[] buffer = new byte[1024];
            System.out.println("Host " + getId() + " is listening on " + getIp() + "/" + getPort());

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Deserialize message
                ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                int seqNb = byteBuffer.getInt();
                int contentSizeBytes = byteBuffer.getInt();
                byte[] contentBytes = new byte[contentSizeBytes];
                byteBuffer.get(contentBytes);
                String content = new String(contentBytes, StandardCharsets.UTF_8);
                int senderId = byteBuffer.getInt();

                String toWrite = "d " + senderId + " " + content;
                System.out.println("Received message SeqNum " + seqNb + " from Sender " + senderId);

                // Deliver the message immediately (out-of-order)
                write(toWrite);
                System.out.println("Delivered message SeqNum " + seqNb + " from Sender " + senderId);

                // Send individual ACK
                sendAck(socket, packet.getAddress(), packet.getPort(), senderId, seqNb);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("Receiver socket closed");
            }
        }
    }

    /**
     * Sends an ACK for a given sender and sequence number.
     *
     * @param socket   The DatagramSocket to send the ACK.
     * @param address  The address of the sender.
     * @param port     The port of the sender.
     * @param senderId The ID of the sender.
     * @param ackNum   The sequence number being acknowledged.
     */
    private void sendAck(DatagramSocket socket, InetAddress address, int port, int senderId, int ackNum) {
        try {
            // Create an ACK containing [senderId (4)] + [ackNum (4)]
            ByteBuffer ackBuffer = ByteBuffer.allocate(8);
            ackBuffer.putInt(senderId);
            ackBuffer.putInt(ackNum);
            byte[] ackData = ackBuffer.array();
            DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, address, port);
            socket.send(ackPacket);
            System.out.println("Sent ACK for Sender " + senderId + " SeqNum " + ackNum);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "Receiver{" +
                "id=" + getId() +
                ", ip='" + getIp() + '\'' +
                ", port=" + getPort() +
                '}';
    }
}
