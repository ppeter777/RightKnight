package dev.rightknight.service;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.model.attack.AttackSnapshot.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.*;
import static org.junit.jupiter.api.Assertions.*;

class AttackGraphCalculatorTest {
    private final AttackGraphCalculator calculator = new AttackGraphCalculator();

    @Test
    void discussedKnightMoveCreatesAttackAndRemovesLastDefender() {
        var result = move("7k/4r3/8/8/R7/2N5/8/7K w - - 0 1", "c3d5");
        assertEquals(5, result.before.pieces().size());
        assertEquals(List.of(ATTACK, DEFENCE), result.before.calculatedKinds());
        assertEquals(List.of(r(DEFENCE, "c3", "a4")), result.before.relations());
        assertEquals(List.of(r(ATTACK, "c3", "e7")), result.delta.addedRelations());
        assertEquals(List.of(r(DEFENCE, "c3", "a4")), result.delta.removedRelations());
        var change = result.delta.pieceChanges().getFirst();
        assertEquals("c3", change.pieceId());
        assertEquals("d5", change.after().square());
        assertEquals(PieceType.KNIGHT, change.after().type());
    }

    @Test
    void retainedAttackFollowsIdentityOfMovingSourceAndTarget() {
        var sourceMoves = move("r6k/8/8/8/8/8/8/R6K w - - 0 1", "a1a2");
        var targetMoves = move("r6k/8/8/8/8/8/8/R6K b - - 0 1", "a8a7");
        for (var result : List.of(sourceMoves, targetMoves)) {
            assertTrue(result.before.relations().contains(r(ATTACK, "a1", "a8")));
            assertTrue(result.delta.addedRelations().isEmpty());
            assertTrue(result.delta.removedRelations().isEmpty());
        }
    }

    @Test
    void discoveredAttackAndExposureOfOwnPieceAreBothRecorded() {
        var opened = move("r6k/8/8/8/8/8/B7/R6K w - - 0 1", "a2b3");
        assertTrue(opened.delta.addedRelations().contains(r(ATTACK, "a1", "a8")));
        assertTrue(opened.delta.addedRelations().contains(r(ATTACK, "a8", "a1")));
        assertTrue(opened.delta.removedRelations().contains(r(ATTACK, "a8", "a2")));
        var blocked = move("r6k/8/8/8/8/1B6/8/R6K w - - 0 1", "b3a2");
        assertTrue(blocked.delta.removedRelations().contains(r(ATTACK, "a1", "a8")));
        assertTrue(blocked.delta.removedRelations().contains(r(ATTACK, "a8", "a1")));
        assertTrue(blocked.delta.addedRelations().contains(r(ATTACK, "a8", "b3")));
    }

    @Test
    void captureRemovesVictimIdentityAndEveryIncidentRelation() {
        var result = move("r6k/8/8/8/8/8/8/R6K w - - 0 1", "a1a8");
        var victim = result.delta.pieceChanges().stream().filter(c -> c.pieceId().equals("a8")).findFirst().orElseThrow();
        assertNull(victim.after());
        assertTrue(result.delta.removedRelations().contains(r(ATTACK, "a1", "a8")));
        assertTrue(result.delta.removedRelations().contains(r(ATTACK, "a8", "a1")));
        assertTrue(result.delta.addedRelations().contains(r(ATTACK, "a1", "h8"))); // check
        assertFalse(result.delta.addedRelations().stream().anyMatch(r -> r.sourceId().equals("a8") || r.targetId().equals("a8")));
    }

    @Test
    void enPassantRemovesPawnOffDestinationAndCanOpenTwoLines() {
        var result = move("7k/8/8/R2pP2r/8/8/8/6K1 w - d6 0 1", "e5d6");
        assertTrue(result.delta.pieceChanges().stream().anyMatch(c -> c.pieceId().equals("d5") && c.after() == null));
        assertTrue(result.delta.addedRelations().contains(r(ATTACK, "a5", "h5")));
        assertTrue(result.delta.addedRelations().contains(r(ATTACK, "h5", "a5")));
    }

    @ParameterizedTest
    @CsvSource({"w, e1g1, h1, f1", "w, e1c1, a1, d1", "b, e8g8, h8, f8", "b, e8c8, a8, d8"})
    void castlingKeepsKingAndRookIds(String side, String uci, String rookId, String rookTo) {
        var result = move("r3k2r/8/8/8/8/8/8/R3K2R " + side + " KQkq - 0 1", uci);
        assertEquals(2, result.delta.pieceChanges().size());
        assertTrue(result.delta.pieceChanges().stream().anyMatch(c -> c.pieceId().equals(rookId) && c.after().square().equals(rookTo)));
        assertTrue(result.delta.pieceChanges().stream().anyMatch(c -> c.pieceId().equals(uci.substring(0, 2)) && c.after().square().equals(uci.substring(2, 4))));
    }

    @ParameterizedTest
    @CsvSource({"q, QUEEN", "r, ROOK", "b, BISHOP", "n, KNIGHT"})
    void capturePromotionKeepsPawnIdAndRemovesCapturedRook(String suffix, PieceType promoted) {
        var result = move("k6r/6P1/8/8/8/8/8/K7 w - - 0 1", "g7h8" + suffix);
        var change = result.delta.pieceChanges().stream().filter(c -> c.pieceId().equals("g7")).findFirst().orElseThrow();
        assertEquals(PieceType.PAWN, change.before().type());
        assertEquals(promoted, change.after().type());
        assertEquals("h8", change.after().square());
        assertTrue(result.delta.pieceChanges().stream().anyMatch(c -> c.pieceId().equals("h8") && c.after() == null));
    }

    @Test
    void blackEnPassantAndQuietPromotionKeepIdentity() {
        var ep = move("7k/8/8/8/3Pp3/8/8/K7 b - d3 0 1", "e4d3");
        assertTrue(ep.delta.pieceChanges().stream().anyMatch(c -> c.pieceId().equals("d4") && c.after() == null));
        var promoted = move("7k/8/8/8/8/8/4p3/K7 b - - 0 1", "e2e1n");
        assertEquals("e2", promoted.delta.pieceChanges().getFirst().pieceId());
        assertEquals(PieceType.KNIGHT, promoted.delta.pieceChanges().getFirst().after().type());
    }

    @Test
    void moveContextSharesOneSnapshotAndRestoresBoardBetweenAllCandidates() {
        String fen = "r1bqkb1r/pp3ppp/n3p3/3pPn2/3p4/N1P2N2/PP2BPPP/R1BQ1RK1 w kq - 0 10";
        var gameMove = new dev.rightknight.model.GameMoveEntity();
        gameMove.setFenBefore(fen);
        gameMove.setPly(19);
        var context = new MoveContextCalculator().calculate(gameMove, null);
        assertEquals(snapshot(fen), context.attackSnapshotBefore());
        context.legalMoveFeatures().forEach((uci, features) -> {
            assertEquals(move(fen, uci).delta, features.attackDelta(), uci);
        });
    }

    @Test
    void mutualDefenceAndPinnedGeometricDefenceArePreserved() {
        var rooks = snapshot("7k/8/8/8/8/8/8/RR5K w - - 0 1");
        assertTrue(rooks.relations().contains(r(DEFENCE, "a1", "b1")));
        assertTrue(rooks.relations().contains(r(DEFENCE, "b1", "a1")));
        var pinned = snapshot("4r2k/8/8/8/3Q4/8/4N3/4K3 w - - 0 1");
        assertTrue(pinned.relations().contains(r(DEFENCE, "e2", "d4")));
        assertTrue(pinned.relations().contains(r(DEFENCE, "e1", "e2")));
        assertFalse(pinned.relations().contains(r(ATTACK, "e8", "e1"))); // knight blocks the rook
    }

    @Test
    void pawnDefenceUsesDiagonalsAndColorNotPawnPushes() {
        var graph = snapshot("7k/8/2n1r3/3p4/3P4/2N1R3/8/7K w - - 0 1");
        assertFalse(graph.relations().contains(r(ATTACK, "d4", "d5")));
        assertFalse(graph.relations().contains(r(ATTACK, "d5", "d4")));
        var white = snapshot("7k/8/8/2N1R3/3P4/8/8/7K w - - 0 1");
        assertTrue(white.relations().contains(r(DEFENCE, "d4", "c5")));
        assertTrue(white.relations().contains(r(DEFENCE, "d4", "e5")));
        var black = snapshot("7k/8/8/3p4/2n1r3/8/8/7K b - - 0 1");
        assertTrue(black.relations().contains(r(DEFENCE, "d5", "c4")));
        assertTrue(black.relations().contains(r(DEFENCE, "d5", "e4")));
    }

    @Test
    void noRelationChangeIsAnEmptyDeltaAndSnapshotsAreImmutable() {
        var result = move("7k/8/8/8/8/8/8/K7 w - - 0 1", "a1a2");
        assertTrue(result.before.relations().isEmpty());
        assertTrue(result.delta.addedRelations().isEmpty());
        assertTrue(result.delta.removedRelations().isEmpty());
        assertEquals(1, result.delta.pieceChanges().size());
        assertThrows(UnsupportedOperationException.class, () -> result.before.pieces().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.delta.pieceChanges().clear());
    }

    private AttackSnapshot snapshot(String fen) {
        Board board = new Board();
        board.loadFromFen(fen);
        return calculator.snapshot(board);
    }

    private Result move(String fen, String uci) {
        Board board = new Board();
        board.loadFromFen(fen);
        var before = calculator.snapshot(board);
        Move move = board.legalMoves().stream().filter(m -> m.toString().equals(uci)).findFirst().orElseThrow();
        assertTrue(board.doMove(move));
        String afterFen = board.getFen();
        var delta = calculator.delta(before, move, board);
        assertEquals(afterFen, board.getFen());
        // Reconstruct from persisted payload and compare to an independently enumerated after-board.
        Map<String, PieceState> pieces = new LinkedHashMap<>(before.pieces());
        delta.pieceChanges().forEach(c -> { if (c.after() == null) pieces.remove(c.pieceId()); else pieces.put(c.pieceId(), c.after()); });
        var expected = calculator.snapshot(board);
        assertEquals(new HashSet<>(expected.pieces().values()), new HashSet<>(pieces.values()));
        assertEquals(expected.pieces().size(), pieces.size());
        Map<String, String> stableIds = new HashMap<>();
        pieces.forEach((id, state) -> stableIds.put(state.square(), id));
        Set<Relation> actual = new HashSet<>(before.relations());
        actual.removeAll(delta.removedRelations());
        actual.addAll(delta.addedRelations());
        Set<Relation> normalizedExpected = new HashSet<>();
        expected.relations().forEach(r -> normalizedExpected.add(new Relation(r.kind(), stableIds.get(r.sourceId()), stableIds.get(r.targetId()))));
        assertEquals(normalizedExpected, actual);
        return new Result(before, delta);
    }

    private Relation r(RelationKind kind, String source, String target) { return new Relation(kind, source, target); }
    private record Result(AttackSnapshot before, AttackDelta delta) {}
}
