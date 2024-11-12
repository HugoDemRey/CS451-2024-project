package cs451;

import java.io.FileWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;

import cs451.Milestone1.OutputWriter;
import cs451.Milestone1.Host.HostParams;

public class Host {

    private static final String IP_START_REGEX = "/";

    private int id;
    private String ip;
    private int port = -1;

    public boolean populate(HostParams hostParams) {
        try {
            id = Integer.parseInt(hostParams.id());
            String ipTest = InetAddress.getByName(hostParams.ip()).toString();
            if (ipTest.startsWith(IP_START_REGEX)) {
                ip = ipTest.substring(1);
            } else {
                ip = InetAddress.getByName(ipTest.split(IP_START_REGEX)[0]).getHostAddress();
            }

            port = Integer.parseInt(hostParams.port());
            if (port <= 0) {
                System.err.println("Port in the hosts file must be a positive number!");
                return false;
            }
        } catch (NumberFormatException e) {
            if (port == -1) {
                System.err.println("Id in the hosts file must be a number!");
            } else {
                System.err.println("Port in the hosts file must be a number!");
            }
            return false;
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }


        return true;
    }

    public int id() {
        return id;
    }

    public String ip() {
        return ip;
    }

    public int port() {
        return port;
    }


    @Override
    public String toString() {
        return "Host{" +
                "id=" + id +
                ", ip='" + ip + '\'' +
                ", port=" + port +
                '}';
    }

}
