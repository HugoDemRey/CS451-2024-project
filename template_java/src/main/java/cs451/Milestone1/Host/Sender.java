package cs451.Milestone1.Host;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import cs451.Constants;
import cs451.Host;
import cs451.Milestone1.Message;

import static cs451.Constants.*;


public class Sender extends ActiveHost {
    private Host host;

    @Override
    public boolean populate(String idString, String ipString, String portString, String outputFilePath) {
        boolean result = super.populate(idString, ipString, portString, outputFilePath);
        return result;
    }

    public Host host() {
        return host;
    }

    public void send(Message message, Host receiver) {
        try {
            DatagramSocket socket = new DatagramSocket();

            InetAddress receiverAddress = InetAddress.getByName(receiver.getIp());
            int receiverPort = receiver.getPort();

            int seqNb = Integer.parseInt(message.getContent());
            int senderId = message.getSenderId();

            // Convert seqNb and senderId to bytes and concatenate with the message bytes
            ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 4);
            byteBuffer.putInt(seqNb);
            byteBuffer.putInt(senderId);

            byte[] packetData = byteBuffer.array();
            DatagramPacket packet = new DatagramPacket(packetData, packetData.length, receiverAddress, receiverPort);
            System.out.println("Sending message to " + receiverAddress + "/" + receiverPort);
            socket.send(packet);
            write("b " + message.getContent());
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendWithPerfectLinks(Message message, Host receiver){
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT);

            InetAddress address = InetAddress.getByName(receiver.getIp());

            String content = message.getContent();
            int senderId = message.getSenderId();

            // Computing the size to allocate for the packet
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            // TODO: Ask if the CHAR_SIZE = 1 for UTF_8
            int contentSizeBytes = content.length();

            // Computing the size of the packet
            int allocatedBytes = Integer.BYTES + contentSizeBytes + Integer.BYTES;

            // Allocating the packet
            ByteBuffer byteBuffer = ByteBuffer.allocate(allocatedBytes);

            // We store the length of the content in the first 4 bytes, 
            // to allow multiple messages to be sent in one packet
            byteBuffer.putInt(contentSizeBytes);
            byteBuffer.put(contentBytes);
            byteBuffer.putInt(senderId);

            byte[] packetData = byteBuffer.array();
            DatagramPacket sendPacket = new DatagramPacket(packetData, packetData.length, address, receiver.getPort());

            int attempts = MAX_RETRIES;
            boolean acknowledged = false;

            while (attempts > 0 && !acknowledged) {
                socket.send(sendPacket);

                try {
                    byte[] ackData = new byte[STANDARD_MESSAGE_SIZE_BYTES];
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);
                    socket.receive(ackPacket);

                    String ackMessage = new String(ackPacket.getData(), 0, ackPacket.getLength());
                    if (ackMessage.equals(ACK)) {
                        acknowledged = true;
                        System.out.println("Message " + message + " acknowledged by " + receiver.getIp() + "/" + receiver.getPort());
                        write("b " + content);
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout reached, " + --attempts + " attempts left");
                }
            }

            socket.close();
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