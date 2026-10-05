package dev.rightknight.service;

import dev.rightknight.engine.EngineCandidate;
import dev.rightknight.engine.StockfishOutputParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import dev.rightknight.engine.ScoreBound;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StockfishOutputParserTest {

    private StockfishOutputParser stockfishOutputParser;

    @BeforeEach
    void setUp() {
        stockfishOutputParser = new StockfishOutputParser();
    }

    @Test
    void shouldParseMultiPvOutput() throws IOException {
        String output = Files.readString(
                Path.of("src/test/resources/stockfish/multipv5.txt"));
        List<EngineCandidate> result = stockfishOutputParser.parse(output, 5);
        assertEquals(5, result.size());
        assertEquals(1, result.getFirst().getRank());
        assertEquals(-268, result.getFirst().getEvalCp());
        assertEquals("e2f4", result.getFirst().getBestMove());
    }

    @Test
    void shouldParseShortMultiPv() throws IOException {
        String output = Files.readString(
                Path.of("src/test/resources/stockfish/mate_multipv3.txt"));
        List<EngineCandidate> parserOutput = stockfishOutputParser.parse(output, 3);
        assertEquals(3, parserOutput.size());
        assertEquals(-3, parserOutput.getFirst().getMateIn());
        assertEquals("g5h6", parserOutput.getFirst().getBestMove());
    }

    @Test
    void mate() throws IOException {
        String output = Files.readString(
                Path.of("src/test/resources/stockfish/mate.txt"));
        List<EngineCandidate> parserOutput = stockfishOutputParser.parse(output, 5);
        assertEquals(5, parserOutput.size());
        assertNull(parserOutput.getFirst().getEvalCp());
        assertEquals(4, parserOutput.getFirst().getMateIn());
        assertEquals("f1g2", parserOutput.getFirst().getBestMove());
    }

    @Test
    void incompleteNewDepthKeepsPreviousCompleteSet() {
        var result = stockfishOutputParser.parse("""
                info depth 20 multipv 1 score cp 80 pv e2e4 e7e5
                info depth 20 multipv 2 score cp 30 pv d2d4 d7d5
                info depth 21 multipv 1 score cp 100 pv d2d4 d7d5
                info depth 20 multipv 2 score cp 30 pv e2e4 e7e5
                bestmove d2d4
                """, 2);
        assertEquals(20, result.getFirst().getDepth());
        assertEquals("e2e4", result.getFirst().getBestMove());
        assertEquals("d2d4", result.getLast().getBestMove());
    }

    @Test
    void incompleteOrDuplicateRootSetIsRejected() {
        assertThrows(IllegalStateException.class, () -> stockfishOutputParser.parse("""
                info depth 20 multipv 1 score cp 80 pv e2e4
                bestmove e2e4
                """, 2));
        assertThrows(IllegalStateException.class, () -> stockfishOutputParser.parse("""
                info depth 20 multipv 1 score cp 80 pv e2e4
                info depth 20 multipv 2 score cp 30 pv e2e4
                """, 2));
    }

    @Test
    void preservesBoundsMateAndLongCounters() {
        var result = stockfishOutputParser.parse("""
                info depth 20 multipv 1 score mate 3 lowerbound nodes 3000000000 nps 4000000000 time 5000000000 pv e7e8q
                info depth 20 multipv 2 score cp -80 upperbound pv e7e8n
                """, 2);
        assertEquals(ScoreBound.LOWER, result.getFirst().getScoreBound());
        assertEquals(3, result.getFirst().getMateIn());
        assertNull(result.getFirst().getEvalCp());
        assertEquals(3000000000L, result.getFirst().getNodes());
        assertEquals(4000000000L, result.getFirst().getNps());
        assertEquals(5000000000L, result.getFirst().getTimeMs());
        assertEquals(ScoreBound.UPPER, result.getLast().getScoreBound());
        assertEquals(-80, result.getLast().getEvalCp());
    }

    @Test
    void singleVariationMayOmitMultiPvAndTerminalPositionHasNoCandidates() {
        var result = stockfishOutputParser.parse("info depth 10 score cp 12 pv e2e4", 1);
        assertEquals(1, result.getFirst().getRank());
        assertEquals(ScoreBound.EXACT, result.getFirst().getScoreBound());
        assertEquals(List.of(), stockfishOutputParser.parse("bestmove (none)", 0));
    }
}
