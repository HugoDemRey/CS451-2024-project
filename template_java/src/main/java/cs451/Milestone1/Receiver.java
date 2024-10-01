package cs451.Milestone1;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import cs451.Host;

public class Receiver extends Host {

    @Override
    public boolean populate(String idString, String ipString, String portString) {
        boolean result = super.populate(idString, ipString, portString);
        return result;
    }

    public void listen() {
        try {
            DatagramSocket socket = new DatagramSocket(getPort());
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            System.out.println("Host " + getId() + " is listening on " + getIp() + "/" + getPort());
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Received: " + message);
            socket.close();
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