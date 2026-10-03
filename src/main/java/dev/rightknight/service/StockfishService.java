package dev.rightknight.service;

import dev.rightknight.engine.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class StockfishService   {
    private final StockfishProperties stockfishProperties;
    private final StockfishOutputParser parser;

    public StockfishService(StockfishProperties stockfishProperties, StockfishOutputParser stockfishOutputParser) {
        this.stockfishProperties = stockfishProperties;
        this.parser = stockfishOutputParser;
    }

    public StockfishSession openSession(
            EngineAnalysisSettings settings) {

        return new StockfishSession(
                stockfishProperties.path(),
                parser,
                settings
        );
    }

    public EngineAnalysisSettings defaultSettings() {
        return new EngineAnalysisSettings(
                stockfishProperties.defaultDepth(),
                stockfishProperties.defaultMultiPv(),
                stockfishProperties.threads()
        );
    }

    public List<EngineCandidate> analyze(String fen) {

        try (StockfishSession session =
                     openSession(defaultSettings())) {

            return session.analyze(fen);
        }
    }
}
