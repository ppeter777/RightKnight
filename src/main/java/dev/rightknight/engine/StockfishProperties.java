package dev.rightknight.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stockfish")
public record StockfishProperties(

        String path,
        int threads,
        int hash,
        int defaultDepth,
        int defaultMultiPv

) {
}
