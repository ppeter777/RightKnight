package dev.rightknight.analysis;

import org.springframework.stereotype.Component;

@Component
public class ExpectedMoveTimeCalculator {

    public long calculateBaseExpectedTimeMs(
            long initialTimeMs,
            long incrementMs,
            int expectedMoves) {

        return initialTimeMs / expectedMoves + incrementMs;
    }
}
