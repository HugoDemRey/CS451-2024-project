package cs451.Milestone1.ReliableNetwork;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import cs451.Milestone1.Message;


public class PerfectLinks {


    public static final int STANDARD_MESSAGE_SIZE_BYTES = 1024;
    public static final String ACK = "ACK";
    private static final int TIMEOUT = 1000; // Timeout in milliseconds
    private static final int MAX_RETRIES = 5; // Maximum number of retries

    private static boolean tryToSend(String destIp, int portNb, Message message) {
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT);

            InetAddress address = InetAddress.getByName(destIp);

            int seqNb = message.getSeqNumber();
            int senderId = message.getSenderId();

            ByteBuffer byteBuffer = ByteBuffer.allocate(2*Integer.BYTES);
            byteBuffer.putInt(seqNb);
            byteBuffer.putInt(senderId);

            byte[] packetData = byteBuffer.array();
            DatagramPacket sendPacket = new DatagramPacket(packetData, packetData.length, address, portNb);

            int attempts = 0;
            boolean acknowledged = false;

            while (attempts < MAX_RETRIES && !acknowledged) {
                socket.send(sendPacket);

                try {
                    byte[] ackData = new byte[STANDARD_MESSAGE_SIZE_BYTES];
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);
                    socket.receive(ackPacket);

                    String ackMessage = new String(ackPacket.getData(), 0, ackPacket.getLength());
                    if (ackMessage.equals(ACK)) {
                        acknowledged = true;
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout reached, " + (MAX_RETRIES - attempts) + " attempts left");
                    attempts++;
                }
            }

            socket.close();
            return acknowledged;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean send(String destIp, int portNb, Message message) {
        return tryToSend(destIp, portNb, message);
    }

    public static Message receive(int portNb) {
        DatagramSocket socket = null;
        Message message = null;
        try {
            socket = new DatagramSocket(portNb);

            while (true) {
                byte[] receiveData = new byte[STANDARD_MESSAGE_SIZE_BYTES];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);

                String receivedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
                System.out.println("Received: " + receivedMessage);

                // Send acknowledgment
                String ackMessage = ACK;
                byte[] ackData = ackMessage.getBytes();
                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(), receivePacket.getPort());
                socket.send(ackPacket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            socket.close();
        }
        return message;
    }
}
