package cs451.Milestone1;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;

import cs451.Host;


public class Sender {
    private Host host;

    public Sender(Host host) {
        this.host = host;
    }

    public Host host() {
        return host;
    }

    public void send(Message message, Receiver receiver) {
        try {
            DatagramSocket socket = new DatagramSocket();
            byte[] buffer = message.toString().getBytes();
            InetAddress receiverAddress = InetAddress.getByName(receiver.host().getIp());
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, receiverAddress, receiver.host().getPort());
            System.out.println("Sending message to " + receiver.host().getIp() + ":" + receiver.host().getPort());
            socket.send(packet);
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}