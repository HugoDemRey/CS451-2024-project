package cs451.Milestone1;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;

import cs451.Host;


public class Sender extends Host{
    private Host host;

    @Override
    public boolean populate(String idString, String ipString, String portString) {
        boolean result = super.populate(idString, ipString, portString);
        return result;
    }

    public Host host() {
        return host;
    }

    public void send(Message message, Host receiver) {
        try {
            DatagramSocket socket = new DatagramSocket();
            byte[] buffer = message.toString().getBytes();

            InetAddress receiverAddress = InetAddress.getByName(receiver.getIp());
            int receiverPort = receiver.getPort();


            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, receiverAddress, receiverPort);
            System.out.println("Sending message to " + receiverAddress + "/" + receiverPort);
            socket.send(packet);
            write("b " + message);
            flushOutput();
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