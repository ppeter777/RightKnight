package dev.rightknight.service;

import dev.rightknight.engine.EngineCandidate;
import dev.rightknight.engine.StockfishOutputParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StockfishOutputParserTest {

    @Mock
    private StockfishService stockfishService;

    @Mock
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
        List<EngineCandidate> parserOutput = stockfishOutputParser.parse(output, 5);
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
}
