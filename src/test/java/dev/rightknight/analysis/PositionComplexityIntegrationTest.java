package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.Board;
import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.GameMoveEntity;
import dev.rightknight.service.MoveContextCalculator;
import dev.rightknight.service.PositionMetricsCalculator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static dev.rightknight.analysis.PositionFeature.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real chesslib metrics -> context -> classification path, without Stockfish. */
class PositionComplexityIntegrationTest {
    private final PositionMetricsCalculator metricsCalculator = new PositionMetricsCalculator();
    private final MoveContextCalculator contextCalculator = new MoveContextCalculator();
    private final PositionComplexityCalculator calculator = new PositionComplexityCalculator();
    private static final String INITIAL = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    void initialPositionHasNoPatterns() {
        var result = classify(current(INITIAL, 1), null);
        assertTrue(result.complete());
        assertEquals(Set.of(), result.features());
    }

    @Test
    void checkWithOnlyOneKingMoveHasTwoOverlappingPatterns() {
        var result = classify(current("r7/8/8/8/8/2k5/8/K7 w - - 0 1", 1), null);
        assertEquals(Set.of(IN_CHECK, SINGLE_LEGAL_MOVE), result.features());
        assertEquals(1.0, result.complexityFactor());
    }

    @Test
    void realGameHasTwoDifferentLegalRecaptures() {
        var result = after("2r2rk1/pp1bbppp/1q2p2n/3pP3/1n1P2P1/2B2N1P/PPNQBP2/R1R3K1 b - - 0 17", "b4c2");
        assertEquals(Set.of(PREVIOUS_MOVE_WAS_CAPTURE, RECAPTURE_AVAILABLE,
                MULTIPLE_RECAPTURES_AVAILABLE), result.features());
    }

    @Test
    void realGameHasOneLegalRecapture() {
        var result = after("2r2rk1/pp2bppp/1q2p2n/1b1pP3/3P2P1/2B2N1P/PPRQBP2/2R3K1 b - - 2 19", "b5e2");
        assertEquals(Set.of(PREVIOUS_MOVE_WAS_CAPTURE, RECAPTURE_AVAILABLE), result.features());
    }

    @Test
    void capturingAfterAQuietMoveIsNotARecapture() {
        var result = after("2R3k1/pp2bnpp/3qpr2/B2p4/3P2P1/1N5P/PP2QP2/6K1 b - - 2 27", "f7d8");
        assertFalse(result.features().contains(PREVIOUS_MOVE_WAS_CAPTURE));
        assertFalse(result.features().contains(RECAPTURE_AVAILABLE));
    }

    @Test
    void recaptureTargetsPawnAfterItsEnPassantCapture() {
        var result = after("7k/2b5/8/3pP3/8/8/8/K7 w - d6 0 1", "e5d6");
        assertEquals(Set.of(PREVIOUS_MOVE_WAS_CAPTURE, RECAPTURE_AVAILABLE), result.features());
    }

    @Test
    void enPassantAfterQuietPawnPushIsNotARecapture() {
        var result = after("7k/3p4/8/4PN2/8/8/8/K7 b - - 0 1", "d7d5");
        assertEquals(Set.of(), result.features());
    }

    @Test
    void pinnedRookCannotSupplyARecapturePattern() {
        var result = after("4r1k1/8/8/8/8/2p5/3NR3/2b1K3 b - - 0 1", "c1d2");
        assertTrue(result.features().contains(PREVIOUS_MOVE_WAS_CAPTURE));
        assertFalse(result.features().contains(RECAPTURE_AVAILABLE));
        assertFalse(result.features().contains(MULTIPLE_RECAPTURES_AVAILABLE));
    }

    @Test
    void fourPromotionChoicesCountAsMultipleLegalMoves() {
        var result = after("r6N/6P1/k7/8/8/8/8/K7 b - - 0 1", "a8h8");
        assertTrue(result.features().contains(MULTIPLE_RECAPTURES_AVAILABLE));
        assertTrue(result.features().contains(RECAPTURE_AVAILABLE));
    }

    @Test
    void laterPositionWithoutHistoryKeepsRecapturesUnknown() {
        var result = classify(current(INITIAL, 3), null);
        assertFalse(result.complete());
        assertEquals(Set.of(PREVIOUS_MOVE_WAS_CAPTURE, RECAPTURE_AVAILABLE,
                MULTIPLE_RECAPTURES_AVAILABLE), result.unknownFeatures());
    }

    private PositionComplexity after(String fen, String uci) {
        Board board = new Board();
        board.loadFromFen(fen);
        var move = board.legalMoves().stream().filter(m -> m.toString().equals(uci)).findFirst().orElseThrow();
        int ply = (Integer.parseInt(fen.split(" ")[5]) - 1) * 2
                + (fen.split(" ")[1].equals("w") ? 1 : 2);
        var previous = current(fen, ply);
        previous.setUci(uci);
        assertTrue(board.doMove(move));
        previous.setFenAfter(board.getFen());
        return classify(current(previous.getFenAfter(), ply + 1), previous);
    }

    private PositionComplexity classify(GameMoveEntity current, GameMoveEntity previous) {
        var metrics = metricsCalculator.calculate(current.getFenBefore());
        var context = contextCalculator.calculate(current, previous);
        var analysis = new GameMoveAnalysisEntity();
        analysis.setInCheck(metrics.inCheck());
        analysis.setLegalMovesCount(metrics.legalMovesCount());
        analysis.setPreviousMoveCapture(context.previousMoveCapture());
        analysis.setRecaptureMovesCount(context.recaptureMovesCount());
        return calculator.calculate(analysis);
    }

    private GameMoveEntity current(String fen, int ply) {
        var move = new GameMoveEntity();
        move.setFenBefore(fen);
        move.setPly(ply);
        return move;
    }
}
