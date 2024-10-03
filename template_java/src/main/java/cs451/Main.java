package cs451;

import java.util.ArrayList;
import java.util.List;

import cs451.Milestone1.Message;
import cs451.Milestone1.Host.ActiveHost;
import cs451.Milestone1.Host.Receiver;
import cs451.Milestone1.Host.Sender;

public class Main {

    static ActiveHost me;

    private static void handleSignal() {
        //immediately stop network packet processing
        System.out.println("Immediately stopping network packet processing.");

        //write/flush output file if necessary
        me.flushOutput();
        System.out.println("Writing output.");
    }

    private static void initSignalHandlers() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                handleSignal();
            }
        });
    }

    public static void main(String[] args) throws InterruptedException {
        Parser parser = new Parser(args);
        parser.parse();

        initSignalHandlers();

        // example
        long pid = ProcessHandle.current().pid();
        System.out.println("My PID: " + pid + "\n");
        System.out.println("From a new terminal type `kill -SIGINT " + pid + "` or `kill -SIGTERM " + pid + "` to stop processing packets\n");

        System.out.println("My ID: " + parser.myId() + "\n");
        System.out.println("List of resolved hosts is:");
        System.out.println("==========================");

        
        // Implement the logic here from config file
        int nbMessagesPerSender = 100;
        int receiverId = 1;
        int myId = parser.myId();

        // We only keep a list of Hosts and not Senders or Receivers, our current host does not need to access functions, we just need data.
        List<Host> receivers = new ArrayList<>();
        List<Host> senders = new ArrayList<>();

        
        List<Host> hosts = parser.hosts();
        for (int i = 0; i < hosts.size(); i++) {

            Host host = hosts.get(i);
            int hostId = host.getId();

            // Initializing me as a sender or receiver
            if (hostId == myId) {
                if (hostId == receiverId) {
                    me = new Receiver();
                    me.populate(host.getId() + "", host.getIp(), host.getPort() + "", parser.output());
                } else {
                    me = new Sender();
                    me.populate(host.getId() + "", host.getIp(), host.getPort() + "", parser.output());
                }
                continue;
            }
                
            // Initializing receivers and senders
            if (hostId == receiverId) receivers.add(host);
            else senders.add(host);

            System.out.println(host.getId());
            System.out.println("Human-readable IP: " + host.getIp());
            System.out.println("Human-readable Port: " + host.getPort());
            System.out.println();
        }

        System.out.println();

        System.out.println("Path to output:");
        System.out.println("===============");
        System.out.println(parser.output() + "\n");

        System.out.println("Path to config:");
        System.out.println("===============");
        System.out.println(parser.config() + "\n");

        System.out.println("Doing some initialization\n");
        System.out.println("me = " + me);
        System.out.println("receivers = " + receivers);
        System.out.println("senders = " + senders);

        System.out.println("Broadcasting and delivering messages...\n");


        String myRole = me instanceof Sender ? "Sender" : "Receiver";

        switch (myRole) {
            case "Sender":
                for (int i = 0; i < nbMessagesPerSender; i++) {
                    String content = i + "";
                    System.out.println("Sending message: " + content);
                    ((Sender) me).sendWithPerfectLinks(new Message(myId, content), receivers.get(0));
                }
                break;
            case "Receiver":
                ((Receiver) me).listenWithPerfectLinks();
                break;
        
            default:
                break;
        }

        // After a process finishes broadcasting,
        // it waits forever for the delivery of messages.
        while (true) {
            // Sleep for 1 hour
            Thread.sleep(60 * 60 * 1000);
        }
    }
}
