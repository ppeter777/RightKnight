package dev.rightknight.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import dev.rightknight.model.GameMoveEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoveContextCalculatorTest {
    private final MoveContextCalculator calculator = new MoveContextCalculator();
    private static final String INITIAL = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    void firstMoveHasNoRecapturesButMissingLaterHistoryIsUnknown() {
        GameMoveEntity first = current(INITIAL, 1);
        var context = calculator.calculate(first, null);
        assertEquals(false, context.previousMoveCapture());
        assertEquals(0, context.recaptureMovesCount());
        assertEquals(new CandidateMoveFeatures(false, false), context.legalMoveFeatures().get("e2e4"));
        var unknown = calculator.calculate(current(INITIAL, 3), null);
        assertNull(unknown.previousMoveCapture());
        assertNull(unknown.recaptureMovesCount());
        assertNull(unknown.legalMoveFeatures().get("e2e4").recapture());
    }

    @Test
    void realGameReplyToBxe2IsARecapture() {
        var context = after("2r2rk1/pp2bppp/1q2p2n/1b1pP3/3P2P1/2B2N1P/PPRQBP2/2R3K1 b - - 2 19", "b5e2");
        assertEquals(true, context.previousMoveCapture());
        assertEquals(new CandidateMoveFeatures(true, true), context.legalMoveFeatures().get("d2e2"));
        assertEquals(1, context.recaptureMovesCount());
        assertEquals(new CandidateMoveFeatures(false, false), context.legalMoveFeatures().get("c3a5"));
    }

    @Test
    void countsBothLegalRecapturesRegardlessOfMultiPv() {
        var context = after("2r2rk1/pp1bbppp/1q2p2n/3pP3/1n1P2P1/2B2N1P/PPNQBP2/R1R3K1 b - - 0 17", "b4c2");
        assertEquals(2, context.recaptureMovesCount());
        assertEquals(true, context.legalMoveFeatures().get("c1c2").recapture());
        assertEquals(true, context.legalMoveFeatures().get("d2c2").recapture());
    }

    @Test
    void capturingKnightAfterQuietNd8IsNotARecapture() {
        var context = after("2R3k1/pp2bnpp/3qpr2/B2p4/3P2P1/1N5P/PP2QP2/6K1 b - - 2 27", "f7d8");
        assertEquals(false, context.previousMoveCapture());
        assertEquals(0, context.recaptureMovesCount());
        assertEquals(new CandidateMoveFeatures(true, false), context.legalMoveFeatures().get("a5d8"));
    }

    @Test
    void enPassantIsCaptureButKnightMovingToEnPassantSquareIsNot() {
        var context = after("7k/3p4/8/4PN2/8/8/8/K7 b - - 0 1", "d7d5");
        assertEquals(new CandidateMoveFeatures(true, false), context.legalMoveFeatures().get("e5d6"));
        assertEquals(new CandidateMoveFeatures(false, false), context.legalMoveFeatures().get("f5d6"));
        assertEquals(0, context.recaptureMovesCount());
    }

    @Test
    void canRecaptureThePawnThatJustCapturedEnPassant() {
        var context = after("7k/2b5/8/3pP3/8/8/8/K7 w - d6 0 1", "e5d6");
        assertEquals(true, context.previousMoveCapture());
        assertEquals(1, context.recaptureMovesCount());
        assertEquals(new CandidateMoveFeatures(true, true), context.legalMoveFeatures().get("c7d6"));
    }

    @Test
    void handlesPreviousCapturePromotionAndAllFourRecapturePromotions() {
        var promoted = after("k6r/6Pr/8/8/8/8/8/K7 w - - 0 1", "g7h8q");
        assertEquals(new CandidateMoveFeatures(true, true), promoted.legalMoveFeatures().get("h7h8"));
        var replies = after("r6N/6P1/k7/8/8/8/8/K7 b - - 0 1", "a8h8");
        assertEquals(4, replies.recaptureMovesCount());
        for (String promotion : new String[]{"q", "r", "b", "n"}) {
            assertEquals(new CandidateMoveFeatures(true, true), replies.legalMoveFeatures().get("g7h8" + promotion));
        }
    }

    @Test
    void illegalRecaptureByPinnedRookIsExcluded() {
        var context = after("4r1k1/8/8/8/8/2p5/3NR3/2b1K3 b - - 0 1", "c1d2");
        assertEquals(true, context.previousMoveCapture());
        assertEquals(0, context.recaptureMovesCount());
        assertFalse(context.legalMoveFeatures().containsKey("e2d2"));
    }

    @Test
    void castlingIsNotACapture() {
        var context = calculator.calculate(current("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", 1), null);
        assertEquals(new CandidateMoveFeatures(false, false), context.legalMoveFeatures().get("e1g1"));
        assertEquals(new CandidateMoveFeatures(false, false), context.legalMoveFeatures().get("e1c1"));
    }

    @Test
    void mismatchedHistoryIsRejected() {
        GameMoveEntity previous = previous(INITIAL, "e2e4");
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(current(INITIAL, 2), previous));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(current(previous.getFenAfter(), 3), previous));
    }

    private MoveContext after(String fen, String uci) {
        GameMoveEntity previous = previous(fen, uci);
        return calculator.calculate(current(previous.getFenAfter(), 2), previous);
    }

    private GameMoveEntity previous(String fen, String uci) {
        Board board = new Board();
        board.loadFromFen(fen);
        Move legal = board.legalMoves().stream().filter(m -> m.toString().equals(uci)).findFirst().orElseThrow();
        GameMoveEntity result = current(fen, 1);
        result.setUci(uci);
        board.doMove(legal);
        result.setFenAfter(board.getFen());
        return result;
    }

    private GameMoveEntity current(String fen, int ply) {
        GameMoveEntity result = new GameMoveEntity();
        result.setFenBefore(fen);
        result.setPly(ply);
        return result;
    }
}
