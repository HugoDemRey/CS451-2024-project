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
    public static final int MAXIMUM_PACKET_SITE_BYTES = 64 * 1024;
    public static final int MAX_MESSAGES_PER_PACKET = 8;
    public static final String ACK = "ACK";
    public static final int MAX_RETRIES = 5; // Maximum number of retries
    public static final int STANDARD_WINDOW_SIZE = 5000;
    public static final int MAXIMUM_WINDOW_SIZE = 30000;
    public static final int STANDARD_TIMEOUT = 700;
    public static final int MAXIMUM_TIMEOUT = 5000;
}
