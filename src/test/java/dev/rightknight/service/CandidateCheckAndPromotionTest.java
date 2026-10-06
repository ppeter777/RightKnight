package dev.rightknight.service;

import dev.rightknight.model.GameMoveEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CandidateCheckAndPromotionTest {
    private final MoveContextCalculator calculator = new MoveContextCalculator();

    @Test
    void checkingAndMatingMovesAreDistinctFromStalemate() {
        var features = features("7k/8/5KQ1/8/8/8/8/8 w - - 0 1");
        assertTrue(features.get("g6g7").givesCheck()); // Qg7#
        assertFalse(features.get("g6f7").givesCheck()); // Qf7 stalemates.
        assertFalse(features.get("g6g7").promotion());
        assertNull(features.get("g6g7").recapture()); // History not needed for check.
    }

    @Test
    void discoveredCheckUsesResultingBoard() {
        var features = features("4k3/8/8/8/8/8/4B3/K3R3 w - - 0 1");
        assertTrue(features.get("e2f3").givesCheck());
        assertFalse(features.get("e1d1").givesCheck());
    }

    @Test
    void enPassantCanOpenRookLine() {
        var features = features("8/8/8/R2pP2k/8/8/8/K7 w - d6 0 1");
        assertTrue(features.get("e5d6").capture());
        assertTrue(features.get("e5d6").givesCheck());
        assertFalse(features.get("e5d6").promotion());
    }

    @Test
    void castlingCanGiveCheckWithRook() {
        var features = features("5k2/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertTrue(features.get("e1g1").givesCheck());
        assertFalse(features.get("e1c1").givesCheck());
        assertFalse(features.get("e1g1").capture());
    }

    @Test
    void quietPromotionsDependOnPromotedPiece() {
        var features = features("7k/4P3/8/8/8/8/8/K7 w - - 0 1");
        for (String piece : new String[]{"q", "r", "b", "n"}) {
            var feature = features.get("e7e8" + piece);
            assertTrue(feature.promotion());
            assertFalse(feature.capture());
            assertEquals(piece.equals("q") || piece.equals("r"), feature.givesCheck());
        }
    }

    @Test
    void knightUnderpromotionCanBeTheOnlyCheckingPromotion() {
        var features = features("8/4P3/5k2/8/8/8/8/K7 w - - 0 1");
        for (String piece : new String[]{"q", "r", "b", "n"}) {
            var feature = features.get("e7e8" + piece);
            assertTrue(feature.promotion());
            assertEquals(piece.equals("n"), feature.givesCheck());
        }
    }

    @Test
    void capturePromotionCombinesAllThreeFlags() {
        var features = features("k6r/6P1/8/8/8/8/8/K7 w - - 0 1");
        for (String piece : new String[]{"q", "r", "b", "n"}) {
            var feature = features.get("g7h8" + piece);
            assertTrue(feature.capture());
            assertTrue(feature.promotion());
            assertEquals(piece.equals("q") || piece.equals("r"), feature.givesCheck());
        }
    }

    private Map<String, CandidateMoveFeatures> features(String fen) {
        GameMoveEntity move = new GameMoveEntity();
        move.setPly(2); // Deliberately unavailable history.
        move.setFenBefore(fen);
        return calculator.calculate(move, null).legalMoveFeatures();
    }
}
