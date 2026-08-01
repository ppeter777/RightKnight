package dev.rightknight.sandbox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class StockfishEngine {
    private Process engineProcess;
    private BufferedReader processReader;
    private OutputStreamWriter processWriter;

    // Start the Stockfish process
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

    // Send a plain text command to Stockfish
    public void sendCommand(String command) {
        try {
            processWriter.write(command + "\n");
            processWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Read the output until Stockfish stops or gives expected output
    public String getOutput(int timeoutMillis) {
        StringBuilder output = new StringBuilder();
        try {
            long startTime = System.currentTimeMillis();
            while ((System.currentTimeMillis() - startTime) < timeoutMillis) {
                if (processReader.ready()) {
                    String line = processReader.readLine();
                    if (line == null) break;
                    output.append(line).append("\n");
                } else {
                    Thread.sleep(10); // Don't hog CPU resources
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output.toString();
    }

    // Properly terminate the native process
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

    public EngineInfo getEngineInfo() {
        // Отправляем стандартную команду инициализации UCI
        sendCommand("uci");

        String name = "Unknown Engine";
        String author = "Unknown Author";

        try {
            String line;
            // Читаем поток построчно, пока процесс активен
            while ((line = processReader.readLine()) != null) {
                // Строка с именем обычно выглядит так: "id name Stockfish 16"
                if (line.startsWith("id name ")) {
                    name = line.replace("id name ", "").trim();
                }
                // Строка с автором: "id author Tord Romstad, Marco Costalba, ..."
                else if (line.startsWith("id author ")) {
                    author = line.replace("id author ", "").trim();
                }
                // Как только движок вывел все свои данные, он пишет "uciok"
                else if (line.equals("uciok")) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new EngineInfo(name, author);
    }

}
