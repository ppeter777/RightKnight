package dev.rightknight.engine;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class StockfishOutputParser {

    /** expectedCandidates is min(requested MultiPV, legal root moves). */
    public List<EngineCandidate> parse(String engineOut, int expectedCandidates) {
        if (expectedCandidates < 0) {
            throw new IllegalArgumentException("Negative candidate count");
        }
        if (expectedCandidates == 0) {
            return List.of();
        }

        List<EngineCandidate> latestComplete = List.of();
        List<EngineCandidate> current = new ArrayList<>();
        for (String rawLine : engineOut.split("\\R")) {
            String line = rawLine.strip();
            if (!line.startsWith("info ") || !line.contains(" pv ")
                    || !line.contains(" score ") || !line.contains(" depth ")) {
                continue;
            }
            EngineCandidate candidate = parseLine(line);
            if (candidate.getRank() == 1) {
                current.clear();
            }
            if (candidate.getRank() != current.size() + 1
                    || candidate.getRank() > expectedCandidates
                    || (candidate.getEvalCp() == null) == (candidate.getMateIn() == null)
                    || candidate.getBestMove() == null
                    || (!current.isEmpty() && candidate.getDepth() != current.getFirst().getDepth())
                    || current.stream().anyMatch(c -> c.getBestMove().equals(candidate.getBestMove()))) {
                current.clear();
                continue;
            }
            current.add(candidate);
            if (current.size() == expectedCandidates) {
                latestComplete = List.copyOf(current);
            }
        }
        if (latestComplete.isEmpty()) {
            throw new IllegalStateException("No complete MultiPV set of " + expectedCandidates + " candidates");
        }
        return latestComplete;
    }

    private EngineCandidate parseLine(String line) {
        String[] tokens = line.split("\\s+");
        EngineCandidate candidate = new EngineCandidate();
        candidate.setRank(1); // UCI may omit multipv for a single variation.
        for (int i = 0; i < tokens.length; i++) {

            switch (tokens[i]) {

                case "depth" -> candidate.setDepth(Integer.parseInt(tokens[++i]));

                case "seldepth" -> candidate.setSelDepth(Integer.parseInt(tokens[++i]));

                case "multipv" -> candidate.setRank(Integer.parseInt(tokens[++i]));

                case "cp" -> candidate.setEvalCp(Integer.parseInt(tokens[++i]));

                case "mate" -> candidate.setMateIn(Integer.parseInt(tokens[++i]));

                case "lowerbound" -> candidate.setScoreBound(ScoreBound.LOWER);

                case "upperbound" -> candidate.setScoreBound(ScoreBound.UPPER);

                case "nodes" -> candidate.setNodes(Long.parseLong(tokens[++i]));

                case "nps" -> candidate.setNps(Long.parseLong(tokens[++i]));

                case "hashfull" -> candidate.setHashfull(Integer.parseInt(tokens[++i]));

                case "time" -> candidate.setTimeMs(Long.parseLong(tokens[++i]));

                case "pv" -> {
                    // После PV до конца строки идут только ходы варианта.
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
