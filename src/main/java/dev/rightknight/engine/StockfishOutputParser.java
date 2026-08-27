package dev.rightknight.engine;


import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.IntStream;

@Component
public class StockfishOutputParser {

    public static List<EngineCandidate> parse(String engineOut, int multiPv) {

        List<EngineCandidate> output = new ArrayList<>();

        String [] lines = engineOut.split("\n");

        List<String> linesList = Arrays.asList(lines);

        Map<Integer, String> lastLines = new HashMap<>();

        for (int i = linesList.size() - 1; i >= 0; i--) {

            String line = linesList.get(i);

            if (!line.startsWith("info")) {
                continue;
            }

            int multiPvLine = extractMultiPv(line);
            output.add(parseLine(line));

            if (multiPvLine < 0) {
                continue;
            }

            lastLines.putIfAbsent(multiPvLine, line);

            if (lastLines.size() == multiPv) {
                break;
            }
        }

        return output;
    }

    public static int extractMultiPv(String line) {
        String[] tokens = line.split("\\s+");
        return IntStream.range(0, tokens.length - 1)
                .filter(i -> tokens[i].equals("multipv"))
                .mapToObj(i -> tokens[i + 1])
                .mapToInt(Integer::parseInt)
                .findFirst()
                .orElse(-1);
    }


    public static EngineCandidate parseLine(String line) {
        String[] tokens = line.split("\\s+");
        EngineCandidate candidate = new EngineCandidate();
        for (int i = 0; i < tokens.length; i++) {

            switch (tokens[i]) {

                case "depth" ->
                        candidate.setDepth(Integer.parseInt(tokens[++i]));

                case "seldepth" ->
                        candidate.setSelDepth(Integer.parseInt(tokens[++i]));

                case "multipv" ->
                        candidate.setRank(Integer.parseInt(tokens[++i]));

                case "cp" ->
                        candidate.setEvalCp(Integer.parseInt(tokens[++i]));

                case "mate" ->
                        candidate.setMateIn(Integer.parseInt(tokens[++i]));

                case "nodes" ->
                        candidate.setNodes(Integer.parseInt(tokens[++i]));

                case "nps" ->
                        candidate.setNps(Integer.parseInt(tokens[++i]));

                case "hashfull" ->
                        candidate.setHashfull(Integer.parseInt(tokens[++i]));

                case "time" ->
                        candidate.setTimeMs(Integer.parseInt(tokens[++i]));

                case "pv" -> {
                    candidate.setPv(
                            String.join(" ",
                                    Arrays.copyOfRange(tokens, i + 1, tokens.length)));
                    return candidate;
                }
            }
        }
        return candidate;
    }
}
