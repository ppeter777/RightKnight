package dev.rightknight.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.Board;
import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.model.attack.AttackSnapshot.RelationKind;
import dev.rightknight.service.AttackGraphCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.rightknight.analysis.DirectRelationEvents.Status.*;
import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.*;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveredAttackEventsTest {
    private final DirectRelationEventCalculator calculator = new DirectRelationEventCalculator();

    @Test
    void queenJoinsAnAttackOnADefendedPawn() throws Exception {
        var line = play("7k/1p6/p7/8/2B5/N7/8/Q6K w - - 0 1", "a3c2");
        var attacks = line.report().discoveredAttacks().added();
        assertEquals(1, attacks.size());
        var attack = attacks.getFirst();
        assertEquals("a3", attack.mover().id());
        assertEquals(List.of("a3"), ids(attack.blockers()));
        assertEquals("a1", attack.attacker().id());
        assertEquals("a6", attack.target().id());
        assertFalse(attack.discoveredCheck());
        assertEquals(List.of("c4"), ids(attack.attacks().before()));
        assertEquals(List.of("a1", "c4"), ids(attack.attacks().after()));
        assertEquals(List.of("b7"), ids(attack.defences().before()));
        assertEquals(List.of("b7"), ids(attack.defences().after()));
        assertTrue(line.report().valuableAttacks().added().isEmpty());
        var mapper = new ObjectMapper();
        var json = mapper.valueToTree(attacks.getFirst());
        assertEquals(2, json.at("/attacks/afterCount").intValue());
        assertEquals(1, json.at("/defences/beforeCount").intValue());
        assertEquals(attack, mapper.treeToValue(json, DiscoveredAttackEvents.Attack.class));
    }

    @Test
    void reportsEachOpenedRayAndCoexistsWithValuableAttacks() {
        // The pawn leaves both the a4-f4 rank and the b2-f6 diagonal.
        var line = play("7k/8/5r2/8/R2P1r2/8/1B6/7K w - - 0 1", "d4d5");
        var attacks = line.report().discoveredAttacks().added();
        assertEquals(List.of("a4", "f4", "f6"), attacks.stream().map(a -> a.target().id()).toList());
        assertEquals(List.of("f4", "a4", "b2"), attacks.stream().map(a -> a.attacker().id()).toList());
        assertEquals(0, attacks.getFirst().attacks().beforeCount());
        assertEquals(List.of("f6"), line.report().valuableAttacks().added().stream().map(a -> a.target().id()).toList());
    }

    @Test
    void anEnPassantCaptureCanRemoveTwoBlockersAndDiscoverCheck() {
        var report = play("8/8/8/R2Pp2k/8/8/8/4K3 w - e6 0 1", "d5e6").report();
        var attack = report.discoveredAttacks().added().getFirst();
        assertTrue(attack.discoveredCheck());
        assertEquals("a5", attack.attacker().id());
        assertEquals("h5", attack.target().id());
        assertEquals("d5", attack.mover().id());
        assertEquals(List.of("d5", "e5"), ids(attack.blockers()));
    }

    @Test
    void moveCanUncoverAnEnemySlider() {
        var attack = play("r6k/8/B7/8/P7/8/8/7K w - - 0 1", "a6b5")
                .report().discoveredAttacks().added().getFirst();
        assertEquals("a8", attack.attacker().id());
        assertEquals("a4", attack.target().id());
        assertEquals("a6", attack.mover().id());
        assertTrue(attack.defences().before().isEmpty());
        assertEquals(List.of("a6"), ids(attack.defences().after()));
        assertEquals("b5", attack.defences().after().getFirst().state().square());
    }

    @Test
    void excludesMovedEndpointsRetainedAttacksAndStillBlockedLines() {
        assertTrue(play("7k/8/8/8/3r4/8/8/R6K w - - 0 1", "a1a4")
                .report().discoveredAttacks().added().isEmpty());
        assertTrue(play("7k/8/8/8/r7/8/5R2/7K w - - 0 1", "f2f4")
                .report().discoveredAttacks().added().isEmpty());
        assertTrue(play("7k/8/3r4/8/3P4/8/8/3R3K w - - 0 1", "d4d5")
                .report().discoveredAttacks().added().isEmpty());
        assertTrue(play("7k/8/8/8/r7/8/8/R6K w - - 0 1", "h1g1")
                .report().discoveredAttacks().added().isEmpty());
    }

    @Test
    void attackOnlyV1PreservesDiscoveryWithUnknownDefences() {
        var line = play("7k/8/5p2/8/3P4/8/1B6/7K w - - 0 1", "d4d5");
        var attackOnly = historical(line, ATTACK);
        assertEquals(AVAILABLE, attackOnly.status());
        assertEquals(1, attackOnly.added().size());
        assertNull(attackOnly.added().getFirst().defences());
        assertEquals(MISSING_DATA, historical(line, DEFENCE).status());
        assertEquals(MISSING_DATA, calculator.calculate("d4d5", line.root(), null).discoveredAttacks().status());
        assertEquals(INCOMPATIBLE_DATA, calculator.calculate("h1g1", line.root(), line.delta()).discoveredAttacks().status());
    }

    private DiscoveredAttackEvents historical(Line line, RelationKind kind) {
        var root = new AttackSnapshot(1, List.of(kind), line.root().pieces(),
                line.root().relations().stream().filter(r -> r.kind() == kind).toList(), null, null);
        var delta = line.delta();
        var oldDelta = new AttackDelta(1, delta.moveUci(), delta.pieceChanges(),
                delta.addedRelations().stream().filter(r -> r.kind() == kind).toList(),
                delta.removedRelations().stream().filter(r -> r.kind() == kind).toList(), null, null, null, null);
        return calculator.calculate(delta.moveUci(), root, oldDelta).discoveredAttacks();
    }

    private static List<String> ids(List<Participant> pieces) {
        return pieces.stream().map(Participant::id).toList();
    }

    private record Line(AttackSnapshot root, AttackDelta delta, DirectRelationEvents report) {}

    private Line play(String fen, String uci) {
        var board = new Board();
        board.loadFromFen(fen);
        var graph = new AttackGraphCalculator();
        var root = graph.snapshot(board);
        var move = board.legalMoves().stream().filter(m -> m.toString().equals(uci)).findFirst().orElseThrow();
        assertTrue(board.doMove(move));
        var delta = graph.delta(root, move, board);
        return new Line(root, delta, calculator.calculate(uci, root, delta));
    }
}
