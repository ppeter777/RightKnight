package dev.rightknight.engine;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@Service
public class StockfishEngine {
    private Process engineProcess;
    private BufferedReader processReader;
    private OutputStreamWriter processWriter;

    public boolean startEngine(String pathToBinary) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(pathToBinary);
            processBuilder.redirectErrorStream(true);

            engineProcess = processBuilder.start();

            processReader = new BufferedReader(
                    new InputStreamReader(engineProcess.getInputStream())
            );
            processWriter = new OutputStreamWriter(engineProcess.getOutputStream());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void sendCommand(String command) {
        if (engineProcess == null || !engineProcess.isAlive()) {
            throw new IllegalStateException("Stockfish process is not running");
        }

        try {
            processWriter.write(command + "\n");
            processWriter.flush();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to send command to Stockfish: " + command,
                    e
            );
        }
    }

    public String getOutput(String expectedMarker, Duration timeout) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            var future = executor.submit(() -> {
                StringBuilder output = new StringBuilder();
                String line;

                while ((line = processReader.readLine()) != null) {
                    output.append(line).append("\n");

                    if (line.equals(expectedMarker)
                            || line.startsWith(expectedMarker)) {
                        return output.toString();
                    }
                }

                throw new IllegalStateException(
                        "Stockfish output closed before marker: " + expectedMarker
                );
            });

            try {
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);

            } catch (TimeoutException e) {
                future.cancel(true);

                if (engineProcess != null && engineProcess.isAlive()) {
                    engineProcess.destroyForcibly();
                }

                throw new IllegalStateException(
                        "Timeout waiting for Stockfish marker '"
                                + expectedMarker + "' after " + timeout,
                        e
                );

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                if (engineProcess != null && engineProcess.isAlive()) {
                    engineProcess.destroyForcibly();
                }

                throw new IllegalStateException(
                        "Interrupted while waiting for Stockfish output",
                        e
                );

            } catch (ExecutionException e) {
                throw new IllegalStateException(
                        "Error while reading Stockfish output",
                        e.getCause()
                );
            }
        }
    }

    public void stopEngine() {
        if (engineProcess == null) {
            return;
        }

        try {
            if (engineProcess.isAlive()) {
                try {
                    sendCommand("quit");
                } catch (RuntimeException ignored) {
                }

                if (!engineProcess.waitFor(1, TimeUnit.SECONDS)) {
                    engineProcess.destroy();

                    if (!engineProcess.waitFor(1, TimeUnit.SECONDS)) {
                        engineProcess.destroyForcibly();
                    }
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            if (engineProcess.isAlive()) {
                engineProcess.destroyForcibly();
            }

        } finally {
            closeQuietly(processReader);
            closeQuietly(processWriter);
        }
    }

    private void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }

        try {
            resource.close();
        } catch (Exception ignored) {
        }
    }
}