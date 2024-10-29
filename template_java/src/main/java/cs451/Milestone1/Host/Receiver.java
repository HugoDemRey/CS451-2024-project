package cs451.Milestone1.Host;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import cs451.Milestone1.Message;

import static cs451.Constants.*;

public class Receiver extends ActiveHost {
    // Map to keep track of delivered sequence numbers per sender
    private final Map<Integer, Set<Integer>> deliveredSeqNums = new ConcurrentHashMap<>();
    private final Message[] messagesReceived = new Message[MAX_MESSAGES_PER_PACKET];

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
            byte[] buffer = new byte[MAX_PACKET_SIZE_BYTES];
            //System.out.println("Host " + getId() + " is listening on " + getIp() + "/" + getPort());

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                
                // Deserialize message
                ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                int seqNb = byteBuffer.getInt();
                int nbMessages = byteBuffer.getInt();

                byte[][] contentBytes = new byte[nbMessages][];
                String[] content = new String[nbMessages];
                for (int i = 0; i < nbMessages; i++) {
                    int contentSizeBytes = byteBuffer.getInt();
                    contentBytes[i] = new byte[contentSizeBytes];
                    byteBuffer.get(contentBytes[i]);
                    content[i] = new String(contentBytes[i], StandardCharsets.UTF_8);
                }
                int senderId = byteBuffer.getInt();
                
                // Initialize the set for the sender if not present
                deliveredSeqNums.computeIfAbsent(senderId, k -> new ConcurrentSkipListSet<>());
                
                // Check if the message has already been delivered
                Set<Integer> senderDelivered = deliveredSeqNums.get(senderId);
                if (!senderDelivered.contains(seqNb)) {
                    // Deliver the message
                    StringBuilder toWriteBuilder = new StringBuilder();

                    for (int i = 0; i < nbMessages; i++) {
                        toWriteBuilder.append("d " + senderId + " " + content[i] + "\n");
                    }

                    // String toWrite = receivedMessages++ + "";
                    write(toWriteBuilder.toString());
                    //System.out.println("↩ | p" + this.getId() + " ← p" + senderId + " : seq n." + seqNb + " | content=" + content);
                    // Mark the sequence number as delivered
                    senderDelivered.add(seqNb);
                } else {
                    // Duplicate message received; do not deliver again
                    //System.out.println("⚠ | p" + this.getId() + " ← p" + senderId + " : seq n." + seqNb + " | content=" + content + " (duplicate)");
                }

                // Send individual ACK regardless of duplication
                sendAck(socket, packet.getAddress(), packet.getPort(), senderId, seqNb);
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
    private void sendAck(DatagramSocket socket, InetAddress address, int port, int originalSenderId, int ackNum) {
        try {
            // Create an ACK containing [OriginalSenderId (4)] [senderId (4)] + [ackNum (4)]]
            ByteBuffer ackBuffer = ByteBuffer.allocate(3 * Integer.BYTES);
            ackBuffer.putInt(this.getId());
            ackBuffer.putInt(originalSenderId);
            ackBuffer.putInt(ackNum);
            byte[] ackData = ackBuffer.array();
            DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, address, port);
            socket.send(ackPacket);
            //System.out.println("↪ | p" + this.getId() + " → p" + originalSenderId + " : seq n." + ackNum + "\n");
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
