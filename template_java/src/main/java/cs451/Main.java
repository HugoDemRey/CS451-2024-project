package cs451;

import java.util.ArrayList;
import java.util.List;

import cs451.Milestone1.Message;
import cs451.Milestone1.Host.ActiveHost;
import cs451.Milestone1.Host.HostParams;
import cs451.Milestone1.Host.Receiver;
import cs451.Milestone1.Host.Sender;
import cs451.Milestone2.FIFO;
import cs451.Milestone2.URB;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Main {

    static FIFO me;

    private static void handleSignal() {
        //immediately stop network packet processing
        System.out.println("Immediately stopping network packet processing.");

        //write/flush output file if necessary
        System.out.println("Writing output.");
        if (me != null) {
            me.flushOutput();
        }
        System.out.println("Output Written.");

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
        int nbMessagesPerSender = -1;
        int myId = parser.myId();
        // Read the configuration file to get nbMessagesPerSender
        try (BufferedReader br = new BufferedReader(new FileReader(parser.config()))) {
            String line = br.readLine();
            nbMessagesPerSender = Integer.parseInt(line);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // We only keep a list of Hosts and not Senders or Receivers, our current host does not need to access functions, we just need data.
        
        List<Host> hosts = parser.hosts();
        for (int i = 0; i < hosts.size(); i++) {

            Host host = hosts.get(i);
            int hostId = host.id();

            // Initializing me as a sender or receiver
            if (hostId == myId) {
                me = new FIFO(parser.output(), new HostParams(host.id() + "", host.ip(), host.port() + ""), hosts);
            }

            System.out.println(host.id());
            System.out.println("Human-readable IP: " + host.ip());
            System.out.println("Human-readable Port: " + host.port());
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

        System.out.println("Broadcasting and delivering messages...\n");

        for (int i = 0; i < nbMessagesPerSender; i++) {
            String content = (i+1) + "";
            // String content = java.util.UUID.randomUUID().toString().substring(0, 5); // should work with any string
            ((FIFO) me).FIFOBroadcast(new Message(myId, content));
        }

        // After a process finishes broadcasting,
        // it waits forever for the delivery of messages.
        while (true) {
            // Sleep for 1 hour
            Thread.sleep(60 * 60 * 1000);
        }
    }
}
