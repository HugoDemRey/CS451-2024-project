package cs451.Milestone1;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import cs451.Host;

public class Receiver {
    private Host host;

    public Receiver(Host host) {
        this.host = host;
    }

    public Host host() {
        return host;
    }

    public void listen() {
        try {
            DatagramSocket socket = new DatagramSocket(host.getPort());
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            System.out.println("Listening on port " + host.getPort());
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Received: " + message);
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}