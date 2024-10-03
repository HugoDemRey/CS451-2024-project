package cs451.Milestone1;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import cs451.Host;

public class Receiver extends Host {

    @Override
    public boolean populate(String idString, String ipString, String portString) {
        boolean result = super.populate(idString, ipString, portString);
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

                InetAddress senderAddress = packet.getAddress();
                int senderPort = packet.getPort();

                String message = new String(packet.getData(), 0, packet.getLength());
                write("d " + senderAddress + " " + message);
                flushOutput();
                System.out.println("Received message from " + senderAddress + "/" + senderPort + ": " + message);

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

    @Override
    public String toString() {
        return "Receiver{" +
                "id=" + getId() +
                ", ip='" + getIp() + '\'' +
                ", port=" + getPort() +
                '}';
    }
}