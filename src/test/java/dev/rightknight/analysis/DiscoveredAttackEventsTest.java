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
        assertTrue(attack.attackingBatteries().after().isEmpty());
        assertEquals(List.of(), attack.defensiveBatteries().after());
        assertEquals(0, attack.defensiveBatteries().afterSupportCount());
        assertTrue(line.report().valuableAttacks().added().isEmpty());
        var mapper = new ObjectMapper();
        var json = mapper.valueToTree(attacks.getFirst());
        assertEquals(2, json.at("/attacks/afterCount").intValue());
        assertEquals(1, json.at("/defences/beforeCount").intValue());
        assertEquals(attack, mapper.treeToValue(json, DiscoveredAttackEvents.Attack.class));
    }

    @Test
    void countsRearDefendersOnEveryRayIncludingUnchangedBatteries() throws Exception {
        // Nc4 uncovers Ra4 against d4. Two black batteries defend d4 from the north and northeast.
        String fen = "7k/3q2q1/3r1b2/3r4/R1Np4/8/8/7K w - - 0 1";
        var report = play(fen, "c4e3").report();
        var attack = report.discoveredAttacks().added().stream().filter(a -> a.target().id().equals("d4"))
                .findFirst().orElseThrow();
        assertFalse(report.batteries().changes().stream().anyMatch(c -> c.square().equals("d4")));
        assertEquals(List.of("d5", "f6"), ids(attack.defences().after()));
        var support = attack.defensiveBatteries();
        assertEquals(support.before(), support.after());
        assertEquals(List.of("N", "NE"), support.after().stream().map(DiscoveredAttackEvents.BatteryRay::direction).toList());
        assertEquals(List.of("d5", "d6", "d7"), ids(support.after().getFirst().pieces()));
        assertEquals(List.of("f6", "g7"), ids(support.after().getLast().pieces()));
        assertEquals(3, support.afterSupportCount());
        var mapper = new ObjectMapper();
        var json = mapper.valueToTree(attack);
        assertEquals(3, json.at("/defensiveBatteries/afterSupportCount").intValue());
        assertEquals(attack, mapper.treeToValue(json, DiscoveredAttackEvents.Attack.class));

        // Nxd6 opens the same attack while breaking the northern battery.
        var capture = play(fen, "c4d6").report().discoveredAttacks().added().stream()
                .filter(a -> a.target().id().equals("d4")).findFirst().orElseThrow();
        assertEquals(3, capture.defensiveBatteries().beforeSupportCount());
        assertEquals(1, capture.defensiveBatteries().afterSupportCount());
        assertEquals(support.before().getLast(), capture.defensiveBatteries().after().getFirst());
        assertEquals(2, capture.defences().afterCount());
    }

    @Test
    void includesBothTheNewAttackingBatteryAndAnExistingAttackingRay() throws Exception {
        var report = play("k7/4r3/4r3/8/1RRNp3/8/2B5/1Q5K w - - 0 1", "d4f5").report();
        var attack = report.discoveredAttacks().added().stream()
                .filter(a -> a.target().id().equals("e4")).findFirst().orElseThrow();
        var support = attack.attackingBatteries();
        assertEquals(List.of("c2"), ids(attack.attacks().before()));
        assertEquals(List.of("c2", "c4"), ids(attack.attacks().after()));
        assertEquals(1, support.beforeSupportCount());
        assertEquals(2, support.afterSupportCount());
        assertEquals(List.of("SW", "W"), support.after().stream().map(DiscoveredAttackEvents.BatteryRay::direction).toList());
        assertEquals(List.of("c2", "b1"), ids(support.after().getFirst().pieces()));
        assertEquals(List.of(support.after().getFirst()), support.before());
        assertEquals(List.of("c4", "b4"), ids(support.after().getLast().pieces()));
        assertEquals(List.of("e6", "e7"), ids(attack.defensiveBatteries().after().getFirst().pieces()));

        var mapper = new ObjectMapper();
        var json = mapper.valueToTree(attack);
        assertEquals(2, json.at("/attackingBatteries/afterSupportCount").intValue());
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
        var report = play("8/8/8/RR1Pp2k/8/8/8/4K3 w - e6 0 1", "d5e6").report();
        var attack = report.discoveredAttacks().added().getFirst();
        assertTrue(attack.discoveredCheck());
        assertNull(attack.defensiveBatteries());
        assertEquals("b5", attack.attacker().id());
        assertTrue(attack.attackingBatteries().before().isEmpty());
        assertEquals(List.of("b5", "a5"), ids(attack.attackingBatteries().after().getFirst().pieces()));
        assertEquals("h5", attack.target().id());
        assertEquals("d5", attack.mover().id());
        assertEquals(List.of("d5", "e5"), ids(attack.blockers()));
    }

    @Test
    void moveCanUncoverAnEnemySlider() {
        var attack = play("r6k/r7/B7/8/P7/8/8/7K w - - 0 1", "a6b5")
                .report().discoveredAttacks().added().getFirst();
        assertEquals("a7", attack.attacker().id());
        assertEquals(List.of("a7", "a8"), ids(attack.attackingBatteries().after().getFirst().pieces()));
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
        assertNull(attackOnly.added().getFirst().defensiveBatteries());
        assertNull(attackOnly.added().getFirst().attackingBatteries());
        assertEquals(MISSING_DATA, historical(line, DEFENCE).status());
        assertEquals(MISSING_DATA, calculator.calculate("d4d5", line.root(), null).discoveredAttacks().status());
        assertEquals(INCOMPATIBLE_DATA, calculator.calculate("h1g1", line.root(), line.delta()).discoveredAttacks().status());
    }

    @Test
    void v2KeepsKnownDirectDefendersAndUnknownBatterySupport() {
        var line = play("7k/1p6/p7/8/2B5/N7/8/Q6K w - - 0 1", "a3c2");
        var root = line.root();
        var delta = line.delta();
        var v2 = new AttackSnapshot(2, root.calculatedKinds(), root.pieces(), root.relations(), root.squareControl(), null);
        var v2Delta = new AttackDelta(2, delta.moveUci(), delta.pieceChanges(), delta.addedRelations(),
                delta.removedRelations(), delta.addedControls(), delta.removedControls(), null, null);
        var attack = calculator.calculate(delta.moveUci(), v2, v2Delta).discoveredAttacks().added().getFirst();
        assertEquals(List.of("b7"), ids(attack.defences().after()));
        assertNull(attack.defensiveBatteries());
        assertNull(attack.attackingBatteries());
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
