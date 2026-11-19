package dev.rightknight.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.bhlangonijr.chesslib.Board;
import dev.rightknight.analysis.CombinedAttackEvents.Combination;
import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.service.AttackGraphCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.ATTACK;
import static org.junit.jupiter.api.Assertions.*;

class CombinedAttackEventsTest {
    private final DirectRelationEventCalculator calculator = new DirectRelationEventCalculator();

    @Test
    void directForkAndDiscoveredAttackFormOneEvent() throws Exception {
        var event = only(play("6k1/3qb3/8/8/4N3/8/8/K3R3 w - - 0 1", "e4f6"));
        assertEquals("e4", event.mover().id());
        assertEquals("f6", event.mover().state().square());
        assertEquals(List.of("d7", "g8"), directIds(event));
        assertTrue(event.directTargets().stream().allMatch(CombinedAttackEvents.DirectTarget::newAttack));
        assertEquals(List.of("e1"), event.discoveredAttacks().stream().map(a -> a.attacker().id()).toList());
        assertEquals("e7", event.discoveredAttacks().getFirst().target().id());
        assertEquals(3, event.distinctTargetCount());
        var mapper = new ObjectMapper();
        assertEquals(event, mapper.readValue(mapper.writeValueAsBytes(event), Combination.class));
    }

    @Test
    void sharedTargetsCountOnceIncludingDoubleCheck() {
        var overlap = only(play("4r1k1/3q4/8/8/4N3/8/8/K3R3 w - - 0 1", "e4f6"));
        assertEquals(List.of("d7", "e8", "g8"), directIds(overlap));
        assertEquals("e8", overlap.discoveredAttacks().getFirst().target().id());
        assertEquals(3, overlap.distinctTargetCount());

        var check = only(play("4k3/8/8/8/4N3/8/8/K3R3 w - - 0 1", "e4f6"));
        assertEquals(List.of("e8"), directIds(check));
        assertEquals("e8", check.discoveredAttacks().getFirst().target().id());
        assertEquals(1, check.distinctTargetCount());
    }

    @Test
    void includesRetainedDirectTargetAndBothNewlyOpenedLines() {
        var event = only(play("k7/4bp2/6n1/8/4R2r/8/2B5/K3R3 w - - 0 1", "e4f4"));
        assertEquals(List.of("f7", "h4"), directIds(event));
        assertEquals(List.of(true, false), event.directTargets().stream().map(t -> t.newAttack()).toList());
        assertEquals(List.of("e7", "g6"), event.discoveredAttacks().stream().map(a -> a.target().id()).toList());
        assertEquals(4, event.distinctTargetCount());
    }

    @Test
    void componentsMustBeNewAndBelongToTheSameSide() {
        var retainedOnly = play("k7/4b3/6n1/8/4R2r/8/2B5/K3R3 w - - 0 1", "e4f4");
        assertEquals(2, retainedOnly.discoveredAttacks().added().size());
        assertTrue(retainedOnly.combinedAttacks().added().isEmpty());
        var enemyLine = play("6k1/4P3/8/8/4N3/8/K7/4r3 w - - 0 1", "e4f6");
        assertFalse(enemyLine.discoveredAttacks().added().isEmpty());
        assertTrue(enemyLine.combinedAttacks().added().isEmpty());
        var forkOnly = play("6k1/3q4/8/8/4N3/8/8/K7 w - - 0 1", "e4f6");
        assertFalse(forkOnly.forks().added().isEmpty());
        assertTrue(forkOnly.combinedAttacks().added().isEmpty());
    }

    @Test
    void legacyAttackEdgesSufficeAndOldReportsHaveAnUnknownCombinedSection() throws Exception {
        var line = line("6k1/3qb3/8/8/4N3/8/8/K3R3 w - - 0 1", "e4f6");
        var root = new AttackSnapshot(1, List.of(ATTACK), line.root().pieces(),
                line.root().relations().stream().filter(e -> e.kind() == ATTACK).toList(), null, null);
        var delta = new AttackDelta(1, "e4f6", line.delta().pieceChanges(),
                line.delta().addedRelations().stream().filter(e -> e.kind() == ATTACK).toList(),
                line.delta().removedRelations().stream().filter(e -> e.kind() == ATTACK).toList(), null, null, null, null);
        var report = calculator.calculate("e4f6", root, delta);
        assertEquals(3, only(report).distinctTargetCount());
        assertEquals(Status.INCOMPATIBLE_DATA, calculator.calculate("a1b1", root, delta).combinedAttacks().status());
        var mapper = new ObjectMapper();
        ObjectNode oldJson = mapper.valueToTree(report);
        oldJson.remove("combinedAttacks");
        assertEquals(Status.MISSING_DATA, mapper.treeToValue(oldJson, DirectRelationEvents.class).combinedAttacks().status());
    }

    private static Combination only(DirectRelationEvents report) {
        assertEquals(Status.AVAILABLE, report.combinedAttacks().status());
        assertEquals(1, report.combinedAttacks().added().size());
        return report.combinedAttacks().added().getFirst();
    }

    private static List<String> directIds(Combination event) {
        return event.directTargets().stream().map(t -> t.target().id()).toList();
    }

    private DirectRelationEvents play(String fen, String uci) {
        var line = line(fen, uci);
        return calculator.calculate(uci, line.root(), line.delta());
    }

    private record Line(AttackSnapshot root, AttackDelta delta) {}

    private static Line line(String fen, String uci) {
        var board = new Board();
        board.loadFromFen(fen);
        var graph = new AttackGraphCalculator();
        var root = graph.snapshot(board);
        var move = board.legalMoves().stream().filter(m -> m.toString().equals(uci)).findFirst().orElseThrow();
        assertTrue(board.doMove(move));
        return new Line(root, graph.delta(root, move, board));
    }
}
