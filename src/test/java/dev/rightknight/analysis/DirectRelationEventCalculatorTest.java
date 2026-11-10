package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.PieceType;
import dev.rightknight.analysis.DirectRelationEvents.TargetChange;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.service.AttackGraphCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.rightknight.analysis.DirectRelationEvents.Status.*;
import static dev.rightknight.analysis.DirectRelationEvents.Transition.*;
import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.ATTACK;
import static org.junit.jupiter.api.Assertions.*;

class DirectRelationEventCalculatorTest {
    private final DirectRelationEventCalculator calculator = new DirectRelationEventCalculator();

    @Test
    void openingTheBatteryAddsOnlyTheFrontBishopAsAnAttackerOrDefender() {
        var whiteBishop = line("k7/6q1/5b2/4P3/3B4/1N6/8/7K w - - 0 1", "e5e6").target("d4");
        assertEquals(FIRST_GAINED, whiteBishop.attacks().transition());
        assertEquals(List.of("f6"), whiteBishop.attacks().added().stream().map(p -> p.id()).toList());
        assertEquals(UNCHANGED, whiteBishop.defences().transition());
        assertEquals(1, whiteBishop.defences().afterCount());

        var blackBishop = line("k7/6q1/5b2/4P3/3b4/1N6/8/7K w - - 0 1", "e5e6").target("d4");
        assertEquals(FIRST_GAINED, blackBishop.defences().transition());
        assertEquals(1, blackBishop.defences().afterCount());
        assertEquals("f6", blackBishop.defences().added().getFirst().id());
        assertEquals(1, blackBishop.attacks().afterCount());
    }

    @Test
    void theDefenderLeavesAnAttackedTargetAndCreatesANewAttack() {
        var line = line("7k/4r3/8/8/R6r/2N5/8/6K1 w - - 0 1", "c3d5");
        var rook = line.target("a4");
        assertEquals(LAST_LOST, rook.defences().transition());
        assertEquals("c3", rook.defences().removed().getFirst().state().square());
        assertEquals(1, rook.attacks().afterCount());

        var knight = line.target("e7").attacks().added().getFirst();
        assertEquals("c3", knight.id());
        assertEquals("d5", knight.state().square());
    }

    @Test
    void joiningAnExistingAttackKeepsTheDefenderAndCountsBothAttackers() {
        var knight = line("6k1/8/8/4p3/Q2n4/8/8/4R1K1 w - - 0 1", "e1d1").target("d4");
        assertEquals(CHANGED, knight.attacks().transition());
        assertEquals(1, knight.attacks().beforeCount());
        assertEquals(2, knight.attacks().afterCount());
        assertEquals("d1", knight.attacks().added().getFirst().state().square());
        assertEquals(UNCHANGED, knight.defences().transition());
        assertEquals(1, knight.defences().afterCount());
    }

    @Test
    void equalCountsWithDifferentAttackingSourcesAreAReplacement() {
        var attack = line("k7/8/5b2/4q3/3B4/1N6/8/7K b - - 0 1", "e5e7").target("d4").attacks();
        assertEquals(REPLACED, attack.transition());
        assertEquals(1, attack.beforeCount());
        assertEquals(1, attack.afterCount());
        assertEquals("e5", attack.removed().getFirst().id());
        assertEquals("f6", attack.added().getFirst().id());
    }

    @Test
    void captureAndPromotionReportDifferentIdentitiesOnTheSameSquare() {
        var line = line("k6r/6P1/8/8/8/8/8/K7 w - - 0 1", "g7h8q");
        var victim = line.target("h8");
        assertNull(victim.after());
        assertEquals(TARGET_CAPTURED, victim.attacks().transition());
        assertEquals(PieceType.PAWN, victim.attacks().removed().getFirst().state().type());

        var checker = line.target("a8").attacks().added().getFirst();
        assertEquals("g7", checker.id());
        assertEquals("h8", checker.state().square());
        assertEquals(PieceType.QUEEN, checker.state().type());
    }

    @Test
    void movingOutOfAttackLosesTheLastAttackOnTheSurvivingPiece() {
        var rook = line("r6k/8/8/8/8/8/8/R6K w - - 0 1", "a1b1").target("a1");
        assertEquals(LAST_LOST, rook.attacks().transition());
        assertEquals("a1", rook.before().square());
        assertEquals("b1", rook.after().square());
    }

    @Test
    void aRookCanMoveUnderTheSameAttackWithoutChangingAnyDirectEdges() {
        var events = line("r6k/8/8/8/8/8/8/R6K w - - 0 1", "a1a2").events();
        assertEquals(AVAILABLE, events.status());
        assertTrue(events.targets().isEmpty());
        assertEquals(MovedIntoAttackEvents.Kind.UNDEFENDED, events.movedIntoAttack().changes().getFirst().kind());
        assertEquals("a8", events.movedIntoAttack().changes().getFirst().attackers().getFirst().id());
        assertTrue(events.undefendedPieces().changes().isEmpty());
        assertEquals(AVAILABLE, events.batteries().status());
        assertTrue(events.batteries().changes().isEmpty());
    }

    @Test
    void knightCapturesOnASquareAttackedByABishopWithNoDefenders() {
        var events = line("7k/6b1/8/8/3p4/5N2/8/7K w - - 0 1", "f3d4").events();
        assertEquals(1, events.movedIntoAttack().changes().size());
        var landing = events.movedIntoAttack().changes().getFirst();
        assertEquals(MovedIntoAttackEvents.Kind.UNDEFENDED, landing.kind());
        assertEquals("f3", landing.target().before().square());
        assertEquals("d4", landing.target().after().square());
        assertEquals("g7", landing.attackers().getFirst().id());
        assertTrue(landing.defenders().isEmpty());
    }

    @Test
    void knightMovesUnderTwoHeavyPieceAttacksWithOneQueenDefender() {
        var report = line("3r2k1/8/8/8/q7/5N2/8/3Q2K1 w - - 0 1", "f3d4").events();
        assertEquals(AVAILABLE, report.movedIntoAttack().status());
        assertEquals(1, report.movedIntoAttack().changes().size());
        var landing = report.movedIntoAttack().changes().getFirst();
        assertEquals(MovedIntoAttackEvents.Kind.OUTNUMBERED, landing.kind());
        assertEquals(List.of("a4", "d8"), landing.attackers().stream().map(p -> p.id()).toList());
        assertEquals(List.of("d1"), landing.defenders().stream().map(p -> p.id()).toList());
        assertTrue(report.undefendedPieces().changes().stream().noneMatch(c -> c.target().id().equals("f3")));
        assertTrue(line("6k1/8/8/8/q7/5N2/8/3Q2K1 w - - 0 1", "f3d4")
                .events().movedIntoAttack().changes().isEmpty());
    }

    @Test
    void movedPawnCanLandUnderAttackAndAStationaryTargetIsExcluded() {
        var line = line("7k/6b1/8/8/8/3P4/8/7K w - - 0 1", "d3d4");
        var pawn = line.events().movedIntoAttack().changes().getFirst();
        assertEquals(PieceType.PAWN, pawn.target().after().type());
        assertEquals(List.of(UndefendedPieceEvents.Reason.FIRST_ATTACK),
                line.events().undefendedPieces().changes().getFirst().reasons());
        var openedLine = line("r6k/8/8/8/P7/2N5/8/7K w - - 0 1", "c3d5");
        assertTrue(openedLine.events().movedIntoAttack().changes().isEmpty());
    }

    @Test
    void partialV1RootAndUnavailableRecordsRemainDistinguishable() {
        var line = line("k7/8/5b2/4P3/3B4/1N6/8/7K w - - 0 1", "e5e6");
        var root = new AttackSnapshot(1, List.of(ATTACK), line.root.pieces(),
                line.root.relations().stream().filter(r -> r.kind() == ATTACK).toList(), null, null);
        var delta = new AttackDelta(1, "e5e6", line.delta.pieceChanges(),
                line.delta.addedRelations().stream().filter(r -> r.kind() == ATTACK).toList(),
                line.delta.removedRelations().stream().filter(r -> r.kind() == ATTACK).toList(), null, null, null, null);
        var events = calculator.calculate("e5e6", root, delta);
        assertEquals(AVAILABLE, events.status());
        assertEquals(List.of(ATTACK), events.calculatedKinds());
        assertEquals(MISSING_DATA, events.movedIntoAttack().status());
        assertEquals(INCOMPATIBLE_DATA, calculator.calculate("h1g1", root, delta).movedIntoAttack().status());
        assertEquals(MISSING_DATA, events.undefendedPieces().status());
        assertEquals(MISSING_DATA, calculator.calculate("e5e6", root, null).undefendedPieces().status());
        assertEquals(INCOMPATIBLE_DATA, calculator.calculate("h1g1", root, delta).undefendedPieces().status());
        assertEquals(MISSING_DATA, events.batteries().status());
        assertEquals(MISSING_DATA, events.squareControl().status());
        assertEquals(INCOMPATIBLE_DATA, calculator.calculate("h1g1", root, delta).squareControl().status());
        assertEquals(INCOMPATIBLE_DATA, calculator.calculate("h1g1", root, delta).batteries().status());
        var bishop = events.targets().stream().filter(t -> t.id().equals("d4")).findFirst().orElseThrow();
        assertNull(bishop.defences());
        assertEquals(FIRST_GAINED, bishop.attacks().transition());
        assertEquals(MISSING_DATA, calculator.calculate("e5e6", root, null).status());
        assertEquals(MISSING_DATA, calculator.calculate("e5e6", null, delta).status());
        assertEquals(INCOMPATIBLE_DATA, calculator.calculate("e5e6", root, line.delta).status());
        assertEquals(INCOMPATIBLE_DATA, calculator.calculate("h1g1", root, delta).status());
    }

    private record Line(AttackSnapshot root, AttackDelta delta, DirectRelationEvents events) {
        TargetChange target(String id) {
            return events.targets().stream().filter(target -> target.id().equals(id)).findFirst().orElseThrow();
        }
    }

    private Line line(String fen, String uci) {
        Board board = new Board();
        board.loadFromFen(fen);
        var graph = new AttackGraphCalculator();
        var root = graph.snapshot(board);
        var move = board.legalMoves().stream().filter(m -> m.toString().equals(uci)).findFirst().orElseThrow();
        assertTrue(board.doMove(move));
        var delta = graph.delta(root, move, board);
        return new Line(root, delta, calculator.calculate(uci, root, delta));
    }
}
