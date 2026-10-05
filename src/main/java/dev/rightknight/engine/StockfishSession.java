package dev.rightknight.engine;

import com.github.bhlangonijr.chesslib.Board;

import java.time.Duration;
import java.util.List;

public class StockfishSession implements AutoCloseable {

    private static final Duration READY_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ANALYSIS_TIMEOUT = Duration.ofSeconds(60);

    private final StockfishEngine engine;
    private final StockfishOutputParser parser;
    private final EngineAnalysisSettings settings;

    public StockfishSession(
            String path,
            StockfishOutputParser parser,
            EngineAnalysisSettings settings) {

        this.parser = parser;
        this.settings = settings;
        this.engine = new StockfishEngine();

        engine.startEngine(path);
        engine.initialize();

        engine.sendCommand(
                "setoption name Threads value " + settings.threads()
        );
        engine.sendCommand(
                "setoption name MultiPV value " + settings.multiPv()
        );

        engine.sendCommand("isready");
        engine.getOutput("readyok", READY_TIMEOUT);

    }

    public String getEngineName() {
        return engine.getEngineName();
    }

    @Override
    public void close() {
        engine.stopEngine();
    }

    public List<EngineCandidate> analyze(String fen) {

        Board board = new Board();
        board.loadFromFen(fen);
        int expectedCandidates = Math.min(settings.multiPv(), board.legalMoves().size());

        engine.sendCommand("position fen " + fen);
        engine.sendCommand("go depth " + settings.depth());

        String engineOutput =
                engine.getOutput("bestmove", ANALYSIS_TIMEOUT);

        return parser.parse(
                engineOutput,
                expectedCandidates
        );
    }
}