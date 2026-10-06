package dev.rightknight.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionMetricsCalculatorTest {
    private final PositionMetricsCalculator calculator = new PositionMetricsCalculator();

    @Test
    void detectsCheckBeforeTryingLegalMoves() {
        var metrics = calculator.calculate("2R3k1/pp2bnpp/3qpr2/B2p4/3P2P1/1N5P/PP2QP2/6K1 b - - 2 27");
        assertTrue(metrics.inCheck());
        assertEquals(4, metrics.legalMovesCount());
        assertFalse(calculator.calculate("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1").inCheck());
    }

    @Test
    void enPassantTargetDoesNotMakeKnightMoveACapture() {
        var metrics = calculator.calculate("7k/8/8/3pPN2/8/8/8/K7 w - d6 0 2");
        assertEquals(1, metrics.captureMovesCount()); // e5xd6 e.p.; Nf5-d6 is quiet.
    }

    @Test
    void checkmateAndStalemateHaveDifferentCheckFlags() {
        var mate = calculator.calculate("7k/6Q1/5K2/8/8/8/8/8 b - - 1 1");
        var stalemate = calculator.calculate("7k/5Q2/5K2/8/8/8/8/8 b - - 1 1");
        assertEquals(0, mate.legalMovesCount());
        assertEquals(0, stalemate.legalMovesCount());
        assertTrue(mate.inCheck());
        assertFalse(stalemate.inCheck());
    }
}
