package dev.rightknight.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;
import dev.rightknight.model.AttackTarget;
import dev.rightknight.model.GameMoveEntity;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NewAttackFeaturesTest {
    private final MoveContextCalculator calculator = new MoveContextCalculator();

    @Test
    void realPositionRecognizesBishopAndPawnAttacksAndAlternativeCapture() {
        var moves = context("r1bqkb1r/pp3ppp/n3p3/3pPn2/3p4/N1P2N2/PP2BPPP/R1BQ1RK1 w kq - 0 10");
        assertEquals(List.of(new AttackTarget("d8", Piece.BLACK_QUEEN)), moves.legalMoveFeatures().get("c1g5").newAttackTargets());
        assertEquals(List.of(new AttackTarget("f5", Piece.BLACK_KNIGHT)), moves.legalMoveFeatures().get("g2g4").newAttackTargets());
        assertTrue(moves.legalMoveFeatures().get("e2a6").capture());
        assertTrue(moves.legalMoveFeatures().get("e2b5").givesCheck());
    }

    @Test
    void retainedAttackIsNotNewAndDiscoveredAttackByAnotherPieceIsExcluded() {
        var retained = context("r6k/8/8/8/8/8/8/R6K w - - 0 1");
        assertEquals(List.of(), retained.legalMoveFeatures().get("a1a2").newAttackTargets());
        var discovered = context("r6k/8/8/8/8/8/B7/R6K w - - 0 1");
        assertEquals(List.of(), discovered.legalMoveFeatures().get("a2b3").newAttackTargets());
    }

    @Test
    void blockersAndPawnDirectionsAreRespectedAndKingsAreExcluded() {
        Board board = new Board();
        board.loadFromFen("7k/8/2n1r3/3P4/2q1b3/8/8/7K w - - 0 1");
        assertEquals(List.of(new AttackTarget("c6", Piece.BLACK_KNIGHT), new AttackTarget("e6", Piece.BLACK_ROOK)),
                DirectAttackDetector.targets(board, Square.D5));
        board.loadFromFen("7k/8/2N1R3/3p4/2Q1B3/8/8/7K b - - 0 1");
        assertEquals(List.of(new AttackTarget("c4", Piece.WHITE_QUEEN), new AttackTarget("e4", Piece.WHITE_BISHOP)),
                DirectAttackDetector.targets(board, Square.D5));
        board.loadFromFen("q6k/p7/8/8/8/8/8/R6K w - - 0 1");
        assertEquals(List.of(new AttackTarget("a7", Piece.BLACK_PAWN)), DirectAttackDetector.targets(board, Square.A1));
        board.loadFromFen("k7/8/8/8/8/8/8/R6K w - - 0 1");
        assertEquals(List.of(), DirectAttackDetector.targets(board, Square.A1));
    }

    @Test
    void pinnedPieceStillHasGeometricAttacks() {
        Board board = new Board();
        board.loadFromFen("4r2k/8/8/8/3q4/8/4N3/4K3 w - - 0 1");
        assertEquals(List.of(new AttackTarget("d4", Piece.BLACK_QUEEN)), DirectAttackDetector.targets(board, Square.E2));
    }

    @Test
    void promotionUsesPromotedPieceAndDoesNotLeakBoardStateBetweenCandidates() {
        var moves = context("k6r/4P3/8/8/8/8/8/K7 w - - 0 1").legalMoveFeatures();
        assertEquals(List.of(new AttackTarget("h8", Piece.BLACK_ROOK)), moves.get("e7e8q").newAttackTargets());
        assertEquals(List.of(new AttackTarget("h8", Piece.BLACK_ROOK)), moves.get("e7e8r").newAttackTargets());
        assertEquals(List.of(), moves.get("e7e8b").newAttackTargets());
        assertEquals(List.of(), moves.get("e7e8n").newAttackTargets());
    }

    private MoveContext context(String fen) {
        GameMoveEntity move = new GameMoveEntity();
        move.setFenBefore(fen);
        move.setPly(1);
        return calculator.calculate(move, null);
    }
}
