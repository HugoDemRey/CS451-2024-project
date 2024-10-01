package cs451;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import cs451.Milestone1.Sender;
import cs451.Milestone1.Message;
import cs451.Milestone1.Receiver;

public class Main {

    private static void handleSignal() {
        //immediately stop network packet processing
        System.out.println("Immediately stopping network packet processing.");

        //write/flush output file if necessary
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
        int nbMessagesPerSender = 10;
        int receiverId = 1;

        
        List<Host> hosts = parser.hosts();
        Receiver receiver = new Receiver(hosts.get(receiverId));
        List<Sender> senders = new ArrayList<>();
        for (int i = 0; i < hosts.size(); i++) {

            Host host = hosts.get(i);

            if (receiverId == i) {
                receiver = new Receiver(host);
            } else {
                senders.add(new Sender(host));
            }

            System.out.println(host.getId());
            System.out.println("Human-readable IP: " + host.getIp());
            System.out.println("Human-readable Port: " + host.getPort());
            System.out.println();
        }

        System.out.println("Reveivers:");
        System.out.println(receiver.host().getId() + " " + receiver.host().getIp() + " " + receiver.host().getPort());
        System.out.println();
        System.out.println("Senders: (" + senders.size() + ")");
        System.out.println(senders);


        System.out.println();

        System.out.println("Path to output:");
        System.out.println("===============");
        System.out.println(parser.output() + "\n");

        System.out.println("Path to config:");
        System.out.println("===============");
        System.out.println(parser.config() + "\n");

        System.out.println("Doing some initialization\n");

        System.out.println("Broadcasting and delivering messages...\n");

        

        // After a process finishes broadcasting,
        // it waits forever for the delivery of messages.
        while (true) {
            // Sleep for 1 hour
            Thread.sleep(60 * 60 * 1000);
        }
    }
}
