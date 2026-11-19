package dev.rightknight.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.Board;
import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.service.AttackGraphCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.rightknight.analysis.DirectRelationEvents.Status.*;
import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.*;
import static org.junit.jupiter.api.Assertions.*;

class ForkEventsTest {
    private final DirectRelationEventCalculator calculator = new DirectRelationEventCalculator();

    @Test
    void tripleForkIncludesTheKingAndDefenceBehindTheFrontRook() throws Exception {
        var events = play("3r2k1/1r1r3q/8/8/4N3/8/8/K7 w - - 0 1", "e4f6");
        var fork = onlyFork(events, "e4");
        assertEquals("f6", fork.attacker().state().square());
        assertEquals(List.of("d7", "g8", "h7"), ids(fork));
        assertTrue(fork.targets().stream().allMatch(ForkEvents.Target::newAttack));
        var rook = fork.targets().getFirst();
        assertEquals(Integer.valueOf(5), rook.materialValue());
        assertTrue(rook.defenders().stream().anyMatch(p -> p.id().equals("d8")));
        var king = fork.targets().get(1);
        assertNull(king.materialValue());
        assertNull(king.defenders());
        assertNull(king.defensiveBatteries());
        var queen = fork.targets().getLast();
        assertEquals(Integer.valueOf(9), queen.materialValue());
        assertEquals(List.of("d7", "g8"), queen.defenders().stream().map(Participant::id).toList());
        assertEquals(List.of("d7", "b7"), queen.defensiveBatteries().getFirst().pieces().stream()
                .map(Participant::id).toList());
        var mapper = new ObjectMapper();
        assertEquals(events, mapper.readValue(mapper.writeValueAsString(events), ForkEvents.class));
    }

    @Test
    void oneKnightCanAttackSevenTargetsIncludingDefendedPawns() {
        var events = play("k7/8/3p1p2/2p3p1/8/2p3p1/3p1N2/7K w - - 0 1", "f2e4");
        var fork = onlyFork(events, "f2");
        assertEquals(List.of("c3", "c5", "d2", "d6", "f6", "g3", "g5"), ids(fork));
        assertTrue(fork.targets().stream().allMatch(t -> t.materialValue() == 1 && t.newAttack()));
    }

    @Test
    void enemyMoveCanAddOneTargetToAnExistingFork() {
        String fen = "k7/3qr2r/5N2/8/8/8/8/K7 b - - 0 1";
        var events = play(fen, "e7e8");
        var fork = onlyFork(events, "f6");
        assertEquals(List.of("d7", "e7", "h7"), ids(fork));
        assertEquals(List.of(false, true, false), fork.targets().stream().map(ForkEvents.Target::newAttack).toList());
        assertEquals("e8", fork.targets().get(1).piece().state().square());
        assertTrue(play(fen, "e7e6").added().stream().noneMatch(f -> f.attacker().id().equals("f6")));
    }

    @Test
    void captureRemovesAnOldTargetAndRearSupportIsNotAnotherAttacker() {
        var events = play("k7/8/8/2p1r3/8/2R5/2R5/7K w - - 0 1", "c3c5");
        assertTrue(events.added().stream().noneMatch(f -> List.of("c3", "c2").contains(f.attacker().id())));
    }

    @Test
    void historicalAttackOnlyGraphCanReportAForkWithoutDefenceData() {
        var board = new Board();
        board.loadFromFen("k7/8/8/2r1b3/8/3P4/8/7K w - - 0 1");
        var graph = new AttackGraphCalculator();
        var root = graph.snapshot(board);
        var move = board.legalMoves().stream().filter(m -> m.toString().equals("d3d4")).findFirst().orElseThrow();
        assertTrue(board.doMove(move));
        var delta = graph.delta(root, move, board);
        var old = new AttackSnapshot(1, List.of(ATTACK), root.pieces(),
                root.relations().stream().filter(e -> e.kind() == ATTACK).toList(), null, null);
        var oldDelta = new AttackDelta(1, "d3d4", delta.pieceChanges(),
                delta.addedRelations().stream().filter(e -> e.kind() == ATTACK).toList(),
                delta.removedRelations().stream().filter(e -> e.kind() == ATTACK).toList(), null, null, null, null);
        var fork = onlyFork(calculator.calculate("d3d4", old, oldDelta).forks(), "d3");
        assertEquals(List.of("c5", "e5"), ids(fork));
        assertNull(fork.targets().getFirst().defenders());
        assertNull(fork.targets().getFirst().defensiveBatteries());
        var withoutAttacks = new AttackSnapshot(1, List.of(DEFENCE), root.pieces(), List.of(), null, null);
        assertEquals(MISSING_DATA, calculator.calculate("d3d4", withoutAttacks, oldDelta).forks().status());
        assertEquals(INCOMPATIBLE_DATA, calculator.calculate("d3d4", root, oldDelta).forks().status());
    }

    private ForkEvents play(String fen, String uci) {
        var board = new Board();
        board.loadFromFen(fen);
        var graph = new AttackGraphCalculator();
        var root = graph.snapshot(board);
        var move = board.legalMoves().stream().filter(m -> m.toString().equals(uci)).findFirst().orElseThrow();
        assertTrue(board.doMove(move));
        return calculator.calculate(uci, root, graph.delta(root, move, board)).forks();
    }

    private static ForkEvents.Fork onlyFork(ForkEvents events, String source) {
        assertEquals(AVAILABLE, events.status());
        var forks = events.added().stream().filter(f -> f.attacker().id().equals(source)).toList();
        assertEquals(1, forks.size());
        return forks.getFirst();
    }

    private static List<String> ids(ForkEvents.Fork fork) {
        return fork.targets().stream().map(t -> t.piece().id()).toList();
    }
}
