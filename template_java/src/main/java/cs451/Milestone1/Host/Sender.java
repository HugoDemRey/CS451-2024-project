package cs451.Milestone1.Host;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import cs451.Host;
import cs451.Milestone1.Message;


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

            int seqNb = message.getSeqNumber();
            int senderId = message.getSenderId();

            // Convert seqNb and senderId to bytes and concatenate with the message bytes
            ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 4);
            byteBuffer.putInt(seqNb);
            byteBuffer.putInt(senderId);

            byte[] packetData = byteBuffer.array();
            DatagramPacket packet = new DatagramPacket(packetData, packetData.length, receiverAddress, receiverPort);
            System.out.println("Sending message to " + receiverAddress + "/" + receiverPort);
            socket.send(packet);
            write("b " + message.getSeqNumber());
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