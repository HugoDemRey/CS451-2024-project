package cs451.Milestone1.Host;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import cs451.Milestone1.Message;

import static cs451.Constants.*;


public class Receiver extends ActiveHost {

    @Override
    public boolean populate(String idString, String ipString, String portString, String outputFilePath) {
        boolean result = super.populate(idString, ipString, portString, outputFilePath);
        return result;
    }

    public void listen() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(getPort());
            byte[] buffer = new byte[1024];
            System.out.println("Host " + getId() + " is listening on " + getIp() + "/" + getPort());

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData());
                int seqNb = byteBuffer.getInt();
                int senderId = byteBuffer.getInt();
                byte[] dataBytes = new byte[packet.getLength() - 8];
                byteBuffer.get(dataBytes);

                write("d " + senderId + " " + seqNb);
                System.out.println("Received message from " + senderId + "/" + seqNb);

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

    public void listenWithPerfectLinks(){
        DatagramSocket socket = null;
        Set<String> delivered = new HashSet<>();
        try {
            socket = new DatagramSocket(getPort());

            while (true) {
                byte[] receiveData = new byte[STANDARD_MESSAGE_SIZE_BYTES];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);

                
                // Send acknowledgment
                String ackMessage = ACK;
                byte[] ackData = ackMessage.getBytes();
                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(), receivePacket.getPort());
                socket.send(ackPacket);


                // Deserialize message
                ByteBuffer byteBuffer = ByteBuffer.wrap(receivePacket.getData(), 0, receivePacket.getLength());
                
                // Unpack the size in bytes of the content
                int contentSizeBytes = byteBuffer.getInt();

                // Unpack the content
                byte[] contentBytes = new byte[contentSizeBytes];
                byteBuffer.get(contentBytes);
                String content = new String(contentBytes, StandardCharsets.UTF_8);

                // Unpack the senderId
                int senderId = byteBuffer.getInt();

                String toWrite = "d " + senderId + " " + content;
                
                // Check if message has already been delivered
                System.out.println(delivered.contains(toWrite));
                if (delivered.contains(toWrite)) {
                    System.out.println("Already delivered: " + toWrite);
                    continue;
                }

                // Deliver the message if it hasn't been delivered yet
                delivered.add(toWrite);
                write("d " + senderId + " " + content);
                System.out.println("Delivering: " + toWrite);

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            socket.close();
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