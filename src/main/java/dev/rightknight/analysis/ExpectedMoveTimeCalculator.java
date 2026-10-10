package dev.rightknight.analysis;

import org.springframework.stereotype.Component;

/** Experimental budget reference for sudden-death and Fischer controls, not a move-quality verdict. */
@Component
public class ExpectedMoveTimeCalculator {

    public static final String MODEL_VERSION = "budget-v0.1";
    private static final int EXPECTED_MOVES = 40;
    private static final int MIN_REMAINING_MOVES = 12;
    private static final int OPENING_END_MOVE = 12;
    private static final double OPENING_START_FACTOR = 0.4;
    private static final double MAX_CLOCK_FRACTION = 0.25;

    /** Retains the original integral division for compatibility with existing callers. */
    public long calculateBaseExpectedTimeMs(
            long initialTimeMs, long incrementMs, int expectedMoves) {
        validateControl(initialTimeMs, incrementMs);
        if (expectedMoves <= 0) {
            throw new IllegalArgumentException("expectedMoves must be positive");
        }
        return Math.addExact(initialTimeMs / expectedMoves, incrementMs);
    }

    /**
     * ply is one-based, for a game starting from the standard initial position.
     * clockBeforeMs belongs to the side to move and excludes the increment for this move.
     * Missing/zero clocks produce an unavailable estimate, never a fabricated baseline.
     * Complexity stays neutral until independently validated position features are connected.
     */
    public Estimate calculateExpectedTimeMs(
            long initialTimeMs, long incrementMs, int ply, Long clockBeforeMs) {
        validateControl(initialTimeMs, incrementMs);
        if (ply <= 0) {
            throw new IllegalArgumentException("ply must be one-based and positive");
        }
        if (clockBeforeMs != null && clockBeforeMs < 0) {
            throw new IllegalArgumentException("clockBeforeMs must not be negative");
        }
        int moveNumber = (ply - 1) / 2 + 1;
        int remainingMoves = Math.max(MIN_REMAINING_MOVES, EXPECTED_MOVES - moveNumber + 1);
        double base = initialTimeMs / (double) EXPECTED_MOVES + incrementMs;
        double progress = Math.min(1.0, (moveNumber - 1.0) / (OPENING_END_MOVE - 1.0));
        double stage = OPENING_START_FACTOR + (1.0 - OPENING_START_FACTOR) * progress;
        double complexity = 1.0;
        if (clockBeforeMs == null || clockBeforeMs == 0) {
            return new Estimate(MODEL_VERSION,
                    clockBeforeMs == null ? Status.MISSING_CLOCK : Status.NO_TIME,
                    null, base, moveNumber, remainingMoves, stage, complexity,
                    null, null, null, false);
        }
        double budget = clockBeforeMs / (double) remainingMoves + incrementMs;
        double pressure = Math.min(1.0, budget / base);
        double uncapped = base * stage * complexity * pressure;
        double cap = clockBeforeMs * MAX_CLOCK_FRACTION;
        return new Estimate(MODEL_VERSION, Status.AVAILABLE, Math.min(uncapped, cap),
                base, moveNumber, remainingMoves, stage, complexity, budget, pressure,
                cap, uncapped > cap);
    }

    private static void validateControl(long initialTimeMs, long incrementMs) {
        if (initialTimeMs <= 0 || incrementMs < 0) {
            throw new IllegalArgumentException("initialTimeMs must be positive; incrementMs non-negative");
        }
    }

    public enum Status { AVAILABLE, MISSING_CLOCK, NO_TIME }

    public record Estimate(
            String modelVersion,
            Status status,
            Double expectedMoveTimeMs,
            double baseExpectedMoveTimeMs,
            int moveNumber,
            int remainingMoves,
            double stageFactor,
            double complexityFactor,
            Double budgetTimeMs,
            Double pressureFactor,
            Double clockCapMs,
            boolean clockCapApplied) {

        /** A provisional resolution gate, not a statistical confidence interval. */
        public boolean isComparisonReliable(long moveTimeMs, long clockResolutionMs) {
            if (moveTimeMs < 0 || clockResolutionMs <= 0) {
                throw new IllegalArgumentException("Invalid move time or clock resolution");
            }
            return status == Status.AVAILABLE
                    && expectedMoveTimeMs >= 2.0 * clockResolutionMs
                    && moveTimeMs >= 2.0 * clockResolutionMs;
        }
    }
}
