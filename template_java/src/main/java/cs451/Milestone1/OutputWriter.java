package cs451.Milestone1;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import static cs451.Constants.MAX_BUFFER_SIZE;

public class OutputWriter {
    private StringBuilder buffer;
    private String outputFilePath;

    public OutputWriter(String outputFilePath) {
        this.outputFilePath = outputFilePath;
        this.buffer = new StringBuilder();
    }

    /**
     * Initializes the writer by creating or clearing the file and opening the BufferedWriter
     */
    public void init() throws IOException {
        Files.deleteIfExists(Paths.get(outputFilePath));
        Files.createFile(Paths.get(outputFilePath));
    }

    /**
     * Adds a line to the buffer and writes to file if buffer size is exceeded
     */
    public void addData(String data){

        try {
            buffer.append(data);
            if (buffer.length() >= MAX_BUFFER_SIZE) {
                writeToFile();
            }
        } catch (IOException e) {
            System.out.println("Error writing to file");
            e.printStackTrace();
        }

        
    }
    
    /**
     * Forces any remaining buffer content to be written to file
     */
    public void flush() throws IOException {
        System.out.println("Flushing Buffer");
        System.out.println("    Buffer Length: " + buffer.length());
        System.out.println("    Buffer Content:");
        for (String line : buffer.toString().split("\n")) {
            System.out.println("        " + line);
        }
        if (buffer.length() > 0) {
            writeToFile();
        }
    }

    /**
     * Closes the BufferedWriter
     */
    public void close() throws IOException {
        flush();
    }

    /**
     * Writes the buffer to the file and clears the buffer
     */
    private void writeToFile() throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFilePath), StandardOpenOption.APPEND);
        writer.write(buffer.toString());
        writer.close();
        buffer.setLength(0); // Clear buffer
    }
}
