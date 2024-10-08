package cs451.Milestone1.Host;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.net.InetAddress;

import cs451.Milestone1.Message;

import static cs451.Constants.*;

public class Receiver extends ActiveHost {
    private int expectedSeqNum = 0;

    @Override
    public boolean populate(String idString, String ipString, String portString, String outputFilePath) {
        boolean result = super.populate(idString, ipString, portString, outputFilePath);
        return result;
    }

    public void listenWithSlidingWindow() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(getPort());
            byte[] buffer = new byte[1024];
            System.out.println("Host " + getId() + " is listening on " + getIp() + "/" + getPort());

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                int seqNb = byteBuffer.getInt();
                int senderId = byteBuffer.getInt();
                byte[] dataBytes = new byte[packet.getLength() - 8];
                byteBuffer.get(dataBytes);
                String content = new String(dataBytes, StandardCharsets.UTF_8);

                if (seqNb == expectedSeqNum) {
                    // Deliver the data
                    write("d " + senderId + " " + content);
                    System.out.println("Delivered message SeqNum " + seqNb);
                    expectedSeqNum++;

                    // Send cumulative ACK
                    sendAck(socket, packet.getAddress(), packet.getPort(), seqNb);
                } else {
                    // Duplicate or out-of-order packet, resend ACK for last in-order
                    sendAck(socket, packet.getAddress(), packet.getPort(), expectedSeqNum - 1);
                    System.out.println("Received out-of-order SeqNum " + seqNb + ", expected " + expectedSeqNum);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("Socket closed");
            }
        }
    }

    private void sendAck(DatagramSocket socket, InetAddress address, int port, int ackNum) throws Exception {
        ByteBuffer ackBuffer = ByteBuffer.allocate(4);
        ackBuffer.putInt(ackNum);
        byte[] ackData = ackBuffer.array();
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, address, port);
        socket.send(ackPacket);
        System.out.println("Sent ACK for SeqNum " + ackNum);
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
