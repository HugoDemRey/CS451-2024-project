package cs451;

public class Constants {
    public static final int ARG_LIMIT_CONFIG = 7;

    /* indexes for id */
    public static final int ID_KEY = 0;
    public static final int ID_VALUE = 1;

    /* indexes for hosts */
    public static final int HOSTS_KEY = 2;
    public static final int HOSTS_VALUE = 3;

    /* indexes for output */
    public static final int OUTPUT_KEY = 4;
    public static final int OUTPUT_VALUE = 5;

    /* indexes for config */
    public static final int CONFIG_VALUE = 6;

    /* PerfectLinks */
    public static final int MAX_PACKET_SIZE_BYTES = 65535; // Maximum UPD packet size in bytes with headers (Ipv4 and Ipv6 - UDP)
    public static final int MAX_PAYLOAD_SIZE = 65507;      // Maximum payload size in bytes (Ipv4 - UDP headers)

    public static final int MAX_MESSAGES_PER_PACKET = 8;   // Maximum number of messages that can be sent in a single packet. Restained by the project specifications.
    
    public static final char PACKET_TYPE_ACK = 'A';        // The type of the packet is an ACK.
    public static final char PACKET_TYPE_CONTENT = 'M';    // The type of the packet is a message.
    
    public static final int STANDARD_WINDOW_SIZE = 750; // The initial window size in PerfectLinks.
    public static final int MIN_WINDOW_SIZE = 250;      // The minimum window size in PerfectLinks.
    public static final int MAX_WINDOW_SIZE = 25000;    // The maximum window size in PerfectLinks.
    
    public static final double GROWING_FACTOR = 1.4;   // The factor to grow the window size when an ACK is received. It is used in PerfectLinks.
    public static final double SHRINKING_FACTOR = 0.6; // The factor to shrink the window size when an ACK is not received before current timeout. It is used in PerfectLinks.
    
    public static final int STANDARD_TIMEOUT = 750; // The initial timeout given to a packet in PerfectLinks. Given in milliseconds.
    
    /* URB */
    public static final int MAX_PENDING_SIZE = 6000;      // Maximum number of pending messages before URB broadcasting again.
    public static final int MIN_PENDING_SIZE = 500;      // Minimum number of pending messages before URB broadcasting again.
    public static final int MAX_PENDING_SLEEP_TIME = 100; // The time to wait until rechecking the pending size in URB. Given in milliseconds
    
    public static final int PENDING_CHECK_INTERVAL = 500; // The interval between each pending queue processing in URB. Given in milliseconds
    
    /* LATTICE AGREEMENT */
    public static final int DECISION_CHECK_INTERVAL = 500;
    public static final int CHECK_NEW_PROPOSITION_INTERVAL = 500;
    public static final int MAX_EPOCH_SIMULTANEOUSLY = 200;

    /* OutputWriter */
    public static final int MAX_BUFFER_SIZE = 1000; // The maximum size of the buffer in OutputWriter before flushing to the file.
}
