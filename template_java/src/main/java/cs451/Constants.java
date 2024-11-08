package cs451;

public class Constants {
    public static final int ARG_LIMIT_CONFIG = 7;

    // indexes for id
    public static final int ID_KEY = 0;
    public static final int ID_VALUE = 1;

    // indexes for hosts
    public static final int HOSTS_KEY = 2;
    public static final int HOSTS_VALUE = 3;

    // indexes for output
    public static final int OUTPUT_KEY = 4;
    public static final int OUTPUT_VALUE = 5;

    // indexes for config
    public static final int CONFIG_VALUE = 6;

    // indexes for PerfectLinks
    public static final int MAX_PACKET_SIZE_BYTES = 65535; // Maximum UPD packet size in bytes with headers (Ipv4 and Ipv6 - UDP)
    public static final int MAX_PAYLOAD_SIZE = 65507; // Maximum payload size in bytes (Ipv4 - UDP headers)
    public static final int MAX_MESSAGES_PER_PACKET = 8;
    public static final String ACK = "ACK";

    public static final int STANDARD_WINDOW_SIZE = 750;
    public static final int MIN_WINDOW_SIZE = 250;
    public static final int MAX_WINDOW_SIZE = 25000;

    public static final double GROWING_FACTOR = 1.4;
    public static final double SHRINKING_FACTOR = 0.6;

    public static final int STANDARD_TIMEOUT = 700;
    
    public static final int MAX_BUFFER_SIZE = 1000;
}
