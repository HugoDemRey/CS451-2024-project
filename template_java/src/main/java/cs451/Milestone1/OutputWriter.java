package cs451.Milestone1;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class OutputWriter {
    private StringBuilder buffer;
    private BufferedWriter writer;
    private String outputFilePath;
    private final int MAX_BUFFER_SIZE;

    public OutputWriter(String outputFilePath, int maxBufferSize) {
        this.outputFilePath = outputFilePath;
        this.MAX_BUFFER_SIZE = maxBufferSize;
        this.buffer = new StringBuilder();
    }

    /**
     * Initializes the writer by creating or clearing the file and opening the BufferedWriter
     */
    public void init() throws IOException {
        Files.deleteIfExists(Paths.get(outputFilePath));
        Files.createFile(Paths.get(outputFilePath));
        writer = Files.newBufferedWriter(Paths.get(outputFilePath), StandardOpenOption.APPEND);
    }

    /**
     * Adds a line to the buffer and writes to file if buffer size is exceeded
     */
    public void addLine(String line) throws IOException {
        buffer.append(line).append(System.lineSeparator());
        if (buffer.length() >= MAX_BUFFER_SIZE) {
            writeToFile();
        }
    }
    
    /**
     * Forces any remaining buffer content to be written to file
     */
    public void flush() throws IOException {
        if (buffer.length() > 0) {
            writeToFile();
        }
    }

    /**
     * Closes the BufferedWriter
     */
    public void close() throws IOException {
        flush();
        if (writer != null) {
            writer.close();
        }
    }

    /**
     * Writes the buffer to the file and clears the buffer
     */
    private void writeToFile() throws IOException {
        writer.write(buffer.toString());
        buffer.setLength(0); // Clear buffer
    }
}
