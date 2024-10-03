package cs451.Milestone1;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;


public class OutputWriter {
    private List<String> buffer;
    private String parentDir;
    private String fileName;
    private String filePath;
    private final int MAX_BUFFER_SIZE = 100;
    private int bufferCount = 0;

    public OutputWriter(String parentDir, String fileName) {
        this.parentDir = parentDir;
        this.fileName = fileName;
        this.filePath = parentDir + "/" + fileName;
        this.buffer = new ArrayList<>();
    }

    /**
     * Adds a line to the buffer
     * @param line the line to be added to the buffer
     */
    public void addLine(String line) {
        buffer.add(line);
        bufferCount++;
        try {
            checkBuffer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Flushes the buffer to the file (forces the buffer to be written to the file)
     * @throws IOException
     */
    public void flush() throws IOException {
        writeToFile();
    }

    public void init() throws IOException {
        try {
            createOrClearFile(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void createOrClearFile(String filePath) throws IOException {
        Files.deleteIfExists(Paths.get(filePath));
        Files.createFile(Paths.get(filePath));
    }

    private void checkBuffer() throws IOException {
        if (bufferCount >= MAX_BUFFER_SIZE) {
            writeToFile();
            bufferCount = 0;
        }
    }

    private void writeToFile() throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath), StandardOpenOption.APPEND)) {
            for (String line : buffer) {
                writer.write(line);
                writer.newLine();
            }
        }
        buffer.clear();
    }
}
