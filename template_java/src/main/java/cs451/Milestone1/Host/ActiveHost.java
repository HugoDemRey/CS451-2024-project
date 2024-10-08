package cs451.Milestone1.Host;

import cs451.Host;
import cs451.Milestone1.OutputWriter;

/**
 * An active hosts only possesses an OutputWriter to write the logs in the output file, which is common to the Receiver and Sender Classes. 
 * The basic Host class only contains data about a host. It is considered "inactive".
 */
public class ActiveHost extends Host {
    private OutputWriter outputWriter;

    public boolean populate(String idString, String ipString, String portString, String outputFilePath) {
        boolean result = super.populate(idString, ipString, portString);
        outputWriter = new OutputWriter(outputFilePath);
        initOutputWriter();
        return result;
    }


    private void initOutputWriter() {
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


    
}
