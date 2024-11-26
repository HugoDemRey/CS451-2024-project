package cs451.Milestone1.Host;

import cs451.Host;
import cs451.Milestone1.OutputWriter;

/**
 * An active hosts only possesses an OutputWriter to write the logs in the output file. 
 * The basic Host class only contains data about a host. It is considered "inactive".
 */
public class ActiveHost extends Host {
    private OutputWriter outputWriter;

    public boolean populate(HostParams hostParams, String outputFilePath) {
        boolean result = super.populate(hostParams);
        outputWriter = new OutputWriter(outputFilePath);
        initOutputWriter();
        return result;
    }


    private void initOutputWriter() {
        try {
            outputWriter.init(false);
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

    public void debug(String line) {
        try {
            outputWriter.debug( line);
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
