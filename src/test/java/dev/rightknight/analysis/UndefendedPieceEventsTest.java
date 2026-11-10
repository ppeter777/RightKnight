package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.PieceType;
import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.analysis.UndefendedPieceEvents.Change;
import dev.rightknight.service.AttackGraphCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.rightknight.analysis.UndefendedPieceEvents.Reason.*;
import static dev.rightknight.analysis.UndefendedPieceEvents.Transition.*;
import static org.junit.jupiter.api.Assertions.*;

class UndefendedPieceEventsTest {
    @Test
    void reportsTheAttackAndTheDefensiveReplyOnConsecutivePlies() {
        var board = board("1r5k/8/8/8/N7/8/8/1B5K b - - 0 1");
        var threat = target(play(board, "b8a8"), "a4");
        assertEquals(EXPOSED, threat.transition());
        assertEquals(List.of(FIRST_ATTACK), threat.reasons());
        var attacker = threat.attackers().getFirst();
        assertEquals("b8", attacker.id());
        assertEquals("a8", attacker.state().square());

        var reply = target(play(board, "b1c2"), "a4");
        assertEquals(ANSWERED, reply.transition());
        assertEquals(List.of(DEFENDER_GAINED), reply.reasons());
        assertEquals(1, reply.target().attacks().afterCount());
        assertEquals("c2", reply.target().defences().added().getFirst().state().square());
    }

    @Test
    void reportsExposingOnesOwnRookByMovingItsOnlyDefender() {
        var rook = target(play(board("7k/4r3/8/8/R6r/2N5/8/6K1 w - - 0 1"), "c3d5"), "a4");
        assertEquals(EXPOSED, rook.transition());
        assertEquals(List.of(LAST_DEFENDER_LOST), rook.reasons());
        assertEquals("h4", rook.attackers().getFirst().id());
    }

    @Test
    void distinguishesEscapeInterpositionCaptureAndOpponentWithdrawal() {
        var escape = target(play(board("r6k/8/8/8/N7/8/8/7K w - - 0 1"), "a4b6"), "a4");
        assertEquals(ANSWERED, escape.transition());
        assertEquals(List.of(TARGET_MOVED), escape.reasons());
        assertEquals("b6", escape.target().after().square());

        var block = target(play(board("r6k/8/8/8/8/1B6/8/N6K w - - 0 1"), "b3a4"), "a1");
        assertEquals(ANSWERED, block.transition());
        assertEquals(List.of(LINE_BLOCKED), block.reasons());

        var capture = target(play(board("r6k/1P6/8/8/N7/8/8/7K w - - 0 1"), "b7a8q"), "a4");
        assertEquals(ANSWERED, capture.transition());
        assertEquals(List.of(DEFENDER_GAINED, ATTACKER_CAPTURED), capture.reasons());

        var withdrawal = target(play(board("r6k/8/8/8/N7/8/8/7K b - - 0 1"), "a8b8"), "a4");
        assertEquals(WITHDRAWN, withdrawal.transition());
        assertEquals(List.of(ATTACKER_MOVED), withdrawal.reasons());
    }

    @Test
    void filtersCapturedTargetsPawnsKingsAndPersistentThreats() {
        assertTrue(play(board("r6k/8/8/8/N7/8/8/7K b - - 0 1"), "a8a4").changes().isEmpty());
        assertTrue(play(board("1r5k/8/8/8/P7/8/8/7K b - - 0 1"), "b8a8").changes().isEmpty());
        assertTrue(play(board("1r5k/8/8/8/K7/8/8/8 b - - 0 1"), "b8a8").changes().isEmpty());
        var ongoing = play(board("rr5k/8/8/8/N7/8/8/7K b - - 0 1"), "b8b4");
        assertTrue(ongoing.changes().stream().noneMatch(c -> c.target().id().equals("a4")));
    }

    @Test
    void promotedKnightCanBeExposedWithUnchangedIncomingRelations() {
        var event = target(play(board("8/Pk6/8/8/8/8/8/7K w - - 0 1"), "a7a8n"), "a7");
        assertEquals(EXPOSED, event.transition());
        assertEquals(List.of(PROMOTION), event.reasons());
        assertEquals(PieceType.KNIGHT, event.target().after().type());
        assertEquals("b7", event.attackers().getFirst().id());
    }

    private static Change target(UndefendedPieceEvents events, String id) {
        return events.changes().stream().filter(c -> c.target().id().equals(id)).findFirst().orElseThrow();
    }

    private static Board board(String fen) {
        Board board = new Board();
        board.loadFromFen(fen);
        return board;
    }

    private static UndefendedPieceEvents play(Board board, String uci) {
        var graph = new AttackGraphCalculator();
        var root = graph.snapshot(board);
        var move = board.legalMoves().stream().filter(m -> m.toString().equals(uci)).findFirst().orElseThrow();
        assertTrue(board.doMove(move));
        var events = new DirectRelationEventCalculator().calculate(uci, root, graph.delta(root, move, board));
        assertEquals(Status.AVAILABLE, events.undefendedPieces().status());
        return events.undefendedPieces();
    }
}
