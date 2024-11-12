package cs451.Milestone1.Host;

/**
 * This class is used to store the parameters of a host, which are the id, the ip and the port.
 */
public class HostParams {

    private final String idString;
    private final String ipString;
    private final String portString;

    public HostParams(String idString, String ipString, String portString) {
        this.idString = idString;
        this.ipString = ipString;
        this.portString = portString;
    }


    public String id() {
        return idString;
    }

    public String ip() {
        return ipString;
    }

    public String port() {
        return portString;
    }

}
