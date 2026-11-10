package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.PieceType;
import dev.rightknight.model.GameMoveAnalysisCandidateEntity;
import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.service.AttackGraphCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.rightknight.analysis.CandidateMoveCategory.NEW_ATTACK_ON_MORE_VALUABLE_PIECE;
import static dev.rightknight.analysis.DirectRelationEvents.Status.*;
import static dev.rightknight.analysis.ValuableAttackEvents.Reason.*;
import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.*;
import static org.junit.jupiter.api.Assertions.*;

class ValuableAttackEventsTest {
    @Test
    void attackingADefendedKnightAndItsRetreatAreSeparateEvents() {
        var board = board("6k1/6p1/5n2/8/4P3/8/8/7K w - - 0 1");
        var advance = play(board, "e4e5");
        var threat = advance.events().added().getFirst();
        assertEquals("e4", threat.attacker().id());
        assertEquals("e5", threat.attacker().state().square());
        assertEquals("f6", threat.target().id());
        assertEquals(ATTACKER_MOVED, threat.reason());
        assertTrue(advance.report.undefendedPieces().changes().stream().noneMatch(c -> c.target().id().equals("f6")));
        var retreat = play(board, "f6d7").events();
        assertTrue(retreat.added().isEmpty());
        assertEquals(TARGET_MOVED, retreat.removed().getFirst().reason());
    }

    @Test
    void discoveredAttackClassifiesBothPlayedAndCandidateEvenWithNoMovingPieceTargets() {
        var line = play(board("7k/6p1/5r2/8/3P4/8/1B6/7K w - - 0 1"), "d4d5");
        var attack = line.events().added().getFirst();
        assertEquals("d4", line.events().mover().id());
        assertEquals("b2", attack.attacker().id());
        assertEquals("f6", attack.target().id());
        assertEquals(LINE_OPENED, attack.reason());

        var played = new GameMoveAnalysisEntity();
        played.setPlayedNewAttackTargets(List.of());
        played.setPlayedAttackDelta(line.delta);
        played.setAttackSnapshotBefore(line.root);
        var candidate = new GameMoveAnalysisCandidateEntity();
        candidate.setNewAttackTargets(List.of());
        candidate.setAttackDelta(line.delta);
        candidate.setMoveUci("d4d5");
        var classifier = new MoveFeatureClassifier();
        assertTrue(classifier.classifyPlayed(played, null).categories().contains(NEW_ATTACK_ON_MORE_VALUABLE_PIECE));
        assertTrue(classifier.classify(candidate, null, null, line.root).categories().contains(NEW_ATTACK_ON_MORE_VALUABLE_PIECE));
        candidate.setMoveUci("h1g1");
        assertTrue(classifier.classify(candidate, null, null, line.root).unknownCategories().contains(NEW_ATTACK_ON_MORE_VALUABLE_PIECE));
    }

    @Test
    void opponentAttackAppearsInReportButDoesNotMakeTheMoveActive() {
        var events = play(board("7k/8/8/6p1/8/8/5R2/7K w - - 0 1"), "f2f4").events();
        assertEquals("g5", events.added().getFirst().attacker().id());
        assertEquals(TARGET_MOVED, events.added().getFirst().reason());
        assertEquals(false, events.attackByMovingSide());
    }

    @Test
    void theMoverAndAStationaryPieceCanBothAddAnAttack() {
        var events = play(board("7k/6p1/4nr2/8/3P4/8/1B6/7K w - - 0 1"), "d4d5").events();
        assertEquals(List.of("e6", "f6"), events.added().stream().map(a -> a.target().id()).toList());
        assertEquals(List.of(ATTACKER_MOVED, LINE_OPENED), events.added().stream()
                .map(ValuableAttackEvents.Attack::reason).toList());
    }

    @Test
    void comparesStablePairsAndUsesPromotedType() {
        var retained = play(board("q6k/8/8/8/8/8/8/R5K1 w - - 0 1"), "a1a2").events();
        assertTrue(retained.added().isEmpty());
        assertTrue(retained.removed().isEmpty());
        String fen = "2r4k/P7/8/8/8/8/8/7K w - - 0 1";
        assertTrue(play(board(fen), "a7a8q").events().added().stream()
                .noneMatch(a -> a.attacker().id().equals("a7")));
        var knight = play(board("7k/P1r5/8/8/8/8/8/7K w - - 0 1"), "a7a8n").events().added().getFirst();
        assertEquals(PieceType.KNIGHT, knight.attacker().state().type());
        assertEquals("c7", knight.target().id());
    }

    @Test
    void checksAndEqualValueAttacksAreExcluded() {
        var events = play(board("5k2/8/7n/8/8/8/8/B6K w - - 0 1"), "a1g7").events();
        assertTrue(events.added().isEmpty());
        assertFalse(ValuableAttackEvents.isMoreValuable(PieceType.BISHOP, PieceType.KNIGHT));
        assertTrue(ValuableAttackEvents.isMoreValuable(PieceType.ROOK, PieceType.QUEEN));
    }

    @Test
    void historicalAttackOnlyGraphSufficesAndMissingLayerRemainsUnknown() {
        var line = play(board("7k/6p1/5r2/8/3P4/8/1B6/7K w - - 0 1"), "d4d5");
        var delta = new AttackDelta(1, "d4d5", line.delta.pieceChanges(),
                line.delta.addedRelations().stream().filter(r -> r.kind() == ATTACK).toList(),
                line.delta.removedRelations().stream().filter(r -> r.kind() == ATTACK).toList(), null, null, null, null);
        var root = new AttackSnapshot(1, List.of(ATTACK), line.root.pieces(),
                line.root.relations().stream().filter(r -> r.kind() == ATTACK).toList(), null, null);
        assertEquals(line.events(), new DirectRelationEventCalculator().calculate("d4d5", root, delta).valuableAttacks());
        var defencesOnly = new AttackSnapshot(1, List.of(DEFENCE), root.pieces(), List.of(), null, null);
        var emptyDelta = new AttackDelta(1, "d4d5", delta.pieceChanges(), List.of(), List.of(), null, null, null, null);
        assertEquals(MISSING_DATA, ValuableAttackEvents.calculate("d4d5", defencesOnly, emptyDelta).status());
        assertEquals(MISSING_DATA, ValuableAttackEvents.calculate("d4d5", root, null).status());
        assertEquals(INCOMPATIBLE_DATA, ValuableAttackEvents.calculate("d4d5", root, line.delta).status());
    }

    private record Line(AttackSnapshot root, AttackDelta delta, DirectRelationEvents report) {
        ValuableAttackEvents events() { return report.valuableAttacks(); }
    }

    private static Board board(String fen) {
        var board = new Board();
        board.loadFromFen(fen);
        return board;
    }

    private static Line play(Board board, String uci) {
        var graph = new AttackGraphCalculator();
        var root = graph.snapshot(board);
        var move = board.legalMoves().stream().filter(m -> m.toString().equals(uci)).findFirst().orElseThrow();
        assertTrue(board.doMove(move));
        var delta = graph.delta(root, move, board);
        var report = new DirectRelationEventCalculator().calculate(uci, root, delta);
        assertEquals(AVAILABLE, report.valuableAttacks().status());
        return new Line(root, delta, report);
    }
}
