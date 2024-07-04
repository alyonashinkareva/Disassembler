package disassembler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FileOutput {
    private final BufferedWriter input;
    private final String fileName;
    public FileOutput(String fileName) throws IOException {
        input = new BufferedWriter(new FileWriter(fileName, StandardCharsets.UTF_8));
        this.fileName = fileName;
    }
    public void writeToFIle(String str) throws IOException {
        input.write(str);
    }
    public void newLine() throws IOException {
        input.newLine();
    }

    public String retFileName() {
        return fileName;
    }
    public void close() throws IOException {
        input.close();
    }
}
