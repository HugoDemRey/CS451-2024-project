package cs451;

import java.io.FileWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;

import cs451.Milestone1.OutputWriter;

public class Host {

    private static final String IP_START_REGEX = "/";

    private int id;
    private String ip;
    private int port = -1;
    private OutputWriter outputWriter;

    public boolean populate(String idString, String ipString, String portString) {
        try {
            id = Integer.parseInt(idString);

            outputWriter = new OutputWriter("./src/main/java/cs451/Milestone1/output", "output_" + id + ".out");

            String ipTest = InetAddress.getByName(ipString).toString();
            if (ipTest.startsWith(IP_START_REGEX)) {
                ip = ipTest.substring(1);
            } else {
                ip = InetAddress.getByName(ipTest.split(IP_START_REGEX)[0]).getHostAddress();
            }

            port = Integer.parseInt(portString);
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

    public int getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public void initOutputWriter() {
        try {
            outputWriter.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void write(String line) {
        outputWriter.addLine(line);
    }

    public void flushOutput() {
        try {
            outputWriter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
