package dev.rightknight.service;

import dev.rightknight.engine.EngineCandidate;
import dev.rightknight.engine.StockfishEngine;
import dev.rightknight.engine.StockfishOutputParser;
import dev.rightknight.engine.StockfishProperties;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockfishService   {
    private final StockfishProperties stockfishProperties;
    private final StockfishOutputParser parser;

    public StockfishService(StockfishProperties stockfishProperties, StockfishOutputParser stockfishOutputParser) {
        this.stockfishProperties = stockfishProperties;
        this.parser = stockfishOutputParser;
    }

    public List<EngineCandidate> analyze(String fen) {

        StockfishEngine stockfish = new StockfishEngine();
        try {
            stockfish.startEngine(stockfishProperties.path());
            stockfish.sendCommand("setoption name Threads value " + stockfishProperties.threads());
            stockfish.sendCommand("setoption name MultiPV value " + stockfishProperties.defaultMultiPv());
            stockfish.sendCommand("isready");
            stockfish.getOutput("readyok");
            stockfish.sendCommand("position fen " + fen);
            stockfish.sendCommand("go depth " + stockfishProperties.defaultDepth());

            String engineOutput = stockfish.getOutput("bestmove");

            return parser.parse(engineOutput, stockfishProperties.defaultMultiPv());
        } finally {
            stockfish.stopEngine();
        }
    }
}
