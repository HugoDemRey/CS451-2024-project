package cs451.Milestone2;

import cs451.Milestone1.OutputWriter;

/**
 * The ApplicationDelivery class is responsible for the delivery of messages to an output file.
 * It simulates the delivery of messages to the application layer.
 * 
 * @implNote This class uses the OutputWriter class to write the logs in the output file. 
 * It has been created to avoid the commuinication layet to handle exceptions and to keep the code clean.
 */
public class ApplicationLayer {
    OutputWriter outputWriter;

    public ApplicationLayer(String outputFilePath, boolean debugMode) {
        outputWriter = new OutputWriter(outputFilePath);
        initOutputWriter(debugMode);
    }

    /**
     * Initializes the writer by creating or clearing the file and opening the BufferedWriter
     * @param debugMode If true, a debug file is created
     */
    private void initOutputWriter(boolean debugMode) {
        try {
            outputWriter.init(debugMode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void write(String line) {
        try {
            outputWriter.addData(line);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deliver(int hostId, String content) {
        write("d " + hostId + " " + content + "\n");
    }

    public void broadcast(String content) {
        write("b " + content + "\n");
    }

    public void debug(String line) {
        try {
            outputWriter.debug(line);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void flushOutput() {
        try {
            outputWriter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
