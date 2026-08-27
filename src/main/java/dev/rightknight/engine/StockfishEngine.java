package dev.rightknight.engine;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;


@Service
public class StockfishEngine {
    private Process engineProcess;
    private BufferedReader processReader;
    private OutputStreamWriter processWriter;

    public boolean startEngine(String pathToBinary) {
        try {
            engineProcess = new ProcessBuilder(pathToBinary).start();
            processReader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
            processWriter = new OutputStreamWriter(engineProcess.getOutputStream());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void sendCommand(String command) {
        try {
            processWriter.write(command + "\n");
            processWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getOutput(String expectedMarker) {
        StringBuilder output = new StringBuilder();
        List<String> lines = new ArrayList<>();
        try {
            String line;
            while ((line = processReader.readLine()) != null) {
                output.append(line).append("\n");
                lines.add(line);
                if (line.equals(expectedMarker) || line.startsWith(expectedMarker)) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return output.toString();
    }




    public void stopEngine() {
        try {
            sendCommand("quit");
            if (processReader != null) processReader.close();
            if (processWriter != null) processWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (engineProcess != null) engineProcess.destroy();
        }
    }
}