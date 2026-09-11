package dev.rightknight.service;

import dev.rightknight.engine.EngineCandidate;
import dev.rightknight.engine.StockfishEngine;
import dev.rightknight.engine.StockfishOutputParser;
import dev.rightknight.engine.StockfishProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class StockfishService   {
    private final StockfishProperties stockfishProperties;
    private final StockfishOutputParser parser;
    private static final Duration UCI_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ANALYSIS_TIMEOUT = Duration.ofSeconds(90);

    public StockfishService(StockfishProperties stockfishProperties, StockfishOutputParser stockfishOutputParser) {
        this.stockfishProperties = stockfishProperties;
        this.parser = stockfishOutputParser;
    }

    public List<EngineCandidate> analyze(String fen) {

        StockfishEngine stockfish = new StockfishEngine();

        try {
            stockfish.startEngine(stockfishProperties.path());

            stockfish.sendCommand("uci");
            stockfish.getOutput("uciok", UCI_TIMEOUT);

            stockfish.sendCommand(
                    "setoption name Threads value "
                            + stockfishProperties.threads()
            );

            stockfish.sendCommand(
                    "setoption name MultiPV value "
                            + stockfishProperties.defaultMultiPv()
            );

            stockfish.sendCommand("isready");
            stockfish.getOutput("readyok", UCI_TIMEOUT);

            stockfish.sendCommand("position fen " + fen);
            stockfish.sendCommand(
                    "go depth " + stockfishProperties.defaultDepth()
            );

            String engineOutput =
                    stockfish.getOutput("bestmove", ANALYSIS_TIMEOUT);

            return parser.parse(
                    engineOutput,
                    stockfishProperties.defaultMultiPv()
            );

        } finally {
            stockfish.stopEngine();
        }
    }
}
