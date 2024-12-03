package cs451.Milestone1;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import static cs451.Constants.MAX_BUFFER_SIZE;

public class OutputWriter {
    private final StringBuilder buffer;
    private final String outputFilePath;
    private final Object lock = new Object(); // Lock object for synchronization

    public OutputWriter(String outputFilePath) {
        this.outputFilePath = outputFilePath;
        this.buffer = new StringBuilder();
    }

    /**
     * Initializes the writer by creating or clearing the file and opening the BufferedWriter
     */
    public void init(boolean debugMode) throws IOException {
        Files.deleteIfExists(Paths.get(outputFilePath));
        Files.createFile(Paths.get(outputFilePath));
        if (debugMode) {
            Files.deleteIfExists(Paths.get(outputFilePath + ".debug"));
            Files.createFile(Paths.get(outputFilePath + ".debug"));
        }
        
    }

    /**
     * Adds a line to the buffer and writes to file if buffer size is exceeded
     * Thread-safe implementation using synchronization
     */
    public void addData(String data) {

        synchronized (lock) {
            try {
                buffer.append(data);
                if (buffer.length() >= MAX_BUFFER_SIZE) {
                    writeToFile();
                }
            } catch (IOException e) {
                System.err.println("Error writing to file");
                e.printStackTrace();
            }
        }
    }

    /**
     * Writes debug information to a separate debug file
     * Thread-safe implementation using synchronization
     */
    public void debug(String data) {
        synchronized (lock) {
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFilePath + ".debug"), StandardOpenOption.APPEND)) {
                writer.write(data);
                writer.close();
            } catch (IOException e) {
                System.err.println("Error writing to debug file");
                e.printStackTrace();
            }
        }
    }

    /**
     * Forces any remaining buffer content to be written to file
     * Thread-safe implementation using synchronization
     */
    public void flush() throws IOException {
        synchronized (lock) {
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
    }

    /**
     * Closes the BufferedWriter
     */
    public void close() throws IOException {
        flush();
    }

    /**
     * Writes the buffer to the file and clears the buffer
     * This method is called within synchronized blocks to ensure thread safety
     */
    private void writeToFile() throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFilePath), StandardOpenOption.APPEND)) {
            writer.write(buffer.toString());
            writer.close();
        }
        buffer.setLength(0);
    }
}
