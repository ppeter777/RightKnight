package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.PieceType;
import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.analysis.SquareControlEvents.Change;
import dev.rightknight.analysis.SquareControlEvents.OccupancyChange;
import dev.rightknight.service.AttackGraphCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.rightknight.analysis.DirectRelationEvents.Transition.*;
import static dev.rightknight.analysis.SquareControlEvents.OccupancyChange.OCCUPIED;
import static dev.rightknight.analysis.SquareControlEvents.OccupancyChange.VACATED;
import static dev.rightknight.analysis.SquareControlEvents.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

class SquareControlEventCalculatorTest {
    @Test
    void rookMoveReplacesAControllerOfTheVacatedSquareAtEqualCounts() {
        var report = report("2r1kb1r/pp1b1ppp/1q2p3/3pPn2/1n1P4/N1B2N2/PP1QBPPP/R4RK1 w k - 7 14", "f1c1");
        assertEquals(List.of("c1", "f1"), occupancyChanges(report));
        var origin = change(report, "f1");
        assertEquals(VACATED, origin.occupancy());
        assertEquals(List.of(OCCUPANCY_CHANGED, WHITE_KING_ZONE), origin.reasons());
        assertEquals("f1", origin.before().id());
        assertNull(origin.after());
        assertEquals(3, origin.white().beforeCount());
        assertEquals(3, origin.white().afterCount());
        assertEquals(DirectRelationEvents.Transition.REPLACED, origin.white().transition());
        assertEquals(List.of("a1"), ids(origin.white().removed()));
        assertEquals(List.of("f1"), ids(origin.white().added()));
        assertEquals("c1", origin.white().added().getFirst().state().square());
        assertEquals(UNCHANGED, origin.black().transition());

        var destination = change(report, "c1");
        assertEquals(OCCUPIED, destination.occupancy());
        assertNull(destination.before());
        assertEquals(origin.before().id(), destination.after().id());
        assertEquals(List.of("f1"), ids(destination.white().removed()));
    }

    @Test
    void capturingRookChangesTheOccupantAndBothSidesControlOfItsOrigin() {
        var report = report("4k3/8/8/3r4/8/8/8/3R3K w - - 0 1", "d1d5");
        var destination = change(report, "d5");
        assertEquals(SquareControlEvents.OccupancyChange.REPLACED, destination.occupancy());
        assertEquals("d5", destination.before().id());
        assertEquals("d1", destination.after().id());
        var origin = change(report, "d1");
        assertEquals(FIRST_GAINED, origin.white().transition());
        assertEquals(LAST_LOST, origin.black().transition());
        assertEquals("d5", origin.white().added().getFirst().state().square());
        assertEquals("d5", origin.black().removed().getFirst().id());
    }

    @Test
    void flankPawnPushOutsideTheSelectedSquaresHasAnAvailableEmptyReport() {
        assertTrue(squares(report("7k/8/8/8/8/8/P7/K7 w - - 0 1", "a2a4")).isEmpty());
    }

    @Test
    void capturePromotionComparesDifferentOccupantsOnOneSquare() {
        var report = report("k6r/6P1/8/8/8/8/8/K7 w - - 0 1", "g7h8q");
        var capture = change(report, "h8");
        assertEquals(SquareControlEvents.OccupancyChange.REPLACED, capture.occupancy());
        assertEquals("h8", capture.before().id());
        assertEquals("g7", capture.after().id());
        assertEquals(PieceType.QUEEN, capture.after().state().type());
        assertEquals(LAST_LOST, capture.white().transition());
        assertEquals(PieceType.PAWN, capture.white().removed().getFirst().state().type());
        // The captured rook also disappears as a controller of the king's (unchanged) square a8.
        assertFalse(squares(report).contains("a8"));
    }

    @Test
    void enPassantIncludesTheVictimsSquareOutsideTheMoveEndpoints() {
        var report = report("7k/8/8/R2pP2r/8/8/8/6K1 w - d6 0 1", "e5d6");
        assertEquals(List.of("d5", "d6", "e5"), occupancyChanges(report));
        var victim = change(report, "d5");
        assertEquals(VACATED, victim.occupancy());
        assertEquals("d5", victim.before().id());
        assertNull(victim.after());
        assertEquals(List.of("h5"), ids(victim.black().added()));
        assertEquals(UNCHANGED, victim.white().transition());
    }

    @Test
    void castlingChecksTheRooksSquaresAndIgnoresRetainedControllerIds() {
        var report = report("4k3/8/8/8/8/8/8/4K2R w K - 0 1", "e1g1");
        assertEquals(List.of("f1", "h1"), occupancyChanges(report));
        var oldRing = change(report, "e2");
        var newRing = change(report, "h2");
        assertEquals(List.of(WHITE_KING_ZONE), oldRing.reasons());
        assertEquals(List.of(WHITE_KING_ZONE), newRing.reasons());
        assertEquals(List.of("e1"), ids(oldRing.white().removed()));
        assertEquals(List.of("e1"), ids(newRing.white().added()));
        assertEquals("g1", newRing.white().added().getFirst().state().square());
        var arrival = change(report, "f1");
        assertEquals("h1", arrival.after().id());
        assertEquals(List.of("h1"), ids(arrival.white().removed()));
        assertEquals(2, arrival.white().beforeCount());
        assertEquals(1, arrival.white().afterCount());
        assertEquals(List.of("e1"), ids(change(report, "h1").white().added()));
    }

    @Test
    void anOccupancyChangeAloneIsInsufficientAndBlackOnlyChangesAreReported() {
        var report = report("4r2k/8/8/4P3/8/8/8/K7 w - - 0 1", "e5e6");
        assertEquals(List.of("e5"), occupancyChanges(report));
        assertEquals(UNCHANGED, change(report, "e5").white().transition());
        assertEquals(LAST_LOST, change(report, "e5").black().transition());
        assertEquals(List.of("e8"), ids(change(report, "e5").black().removed()));
    }

    @Test
    void retainedCentralOccupantIsReportedAndAnUnselectedEmptySquareIsOmitted() {
        var report = report("k7/8/5b2/4P3/3B4/1N6/8/7K w - - 0 1", "e5e6");
        assertTrue(report.targets().stream().anyMatch(t -> t.id().equals("d4") && t.attacks().transition() == FIRST_GAINED));
        var center = change(report, "d4");
        assertEquals(List.of(CENTER), center.reasons());
        assertEquals(OccupancyChange.UNCHANGED, center.occupancy());
        assertEquals("d4", center.before().id());
        assertEquals(center.before(), center.after());
        assertEquals(List.of("f6"), ids(center.black().added()));
        assertFalse(squares(report).contains("f7")); // Newly controlled by the pawn, empty throughout.
    }

    @Test
    void initialE4GainsControlOfTheEmptyCentralSquareD5() {
        var report = report("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "e2e4");
        var d5 = change(report, "d5");
        assertEquals(List.of(CENTER), d5.reasons());
        assertEquals(OccupancyChange.UNCHANGED, d5.occupancy());
        assertNull(d5.before());
        assertNull(d5.after());
        assertEquals(FIRST_GAINED, d5.white().transition());
        assertEquals(List.of("e2"), ids(d5.white().added()));
        assertEquals("e4", d5.white().added().getFirst().state().square());
        assertEquals(UNCHANGED, d5.black().transition());
        assertFalse(squares(report).contains("f5"));
        assertFalse(squares(report).contains("e4")); // A selected square with unchanged control.
    }

    @Test
    void aSingleChangeCanBelongToTheCenterAndBothKingZones() {
        var report = report("8/8/8/4k3/8/2K5/8/3R4 w - - 0 1", "d1d4");
        var d4 = change(report, "d4");
        assertEquals(1, squares(report).stream().filter("d4"::equals).count());
        assertEquals(List.of(OCCUPANCY_CHANGED, CENTER, WHITE_KING_ZONE, BLACK_KING_ZONE), d4.reasons());
        assertEquals(OCCUPIED, d4.occupancy());
        assertEquals(2, d4.white().beforeCount());
        assertEquals(1, d4.white().afterCount());
        assertEquals(UNCHANGED, d4.black().transition());
    }

    @Test
    void pawnGainsControlOfEmptySquareBesideTheOpposingKing() {
        var report = report("7k/8/8/7P/8/8/8/K7 w - - 0 1", "h5h6");
        assertEquals(List.of("g7"), squares(report));
        var g7 = change(report, "g7");
        assertEquals(List.of(BLACK_KING_ZONE), g7.reasons());
        assertEquals(OccupancyChange.UNCHANGED, g7.occupancy());
        assertEquals(FIRST_GAINED, g7.white().transition());
        assertEquals(UNCHANGED, g7.black().transition());
    }

    @Test
    void kingMoveAtBoardEdgeUsesBothRingsAndSkipsTheirUnchangedOverlap() {
        var report = report("k7/8/8/8/8/8/8/7K b - - 0 1", "a8b8");
        assertEquals(List.of("a8", "b8", "c7", "c8"), squares(report));
        assertEquals(List.of(OCCUPANCY_CHANGED, BLACK_KING_ZONE), change(report, "b8").reasons()); // Old ring.
        assertEquals(List.of(BLACK_KING_ZONE), change(report, "c7").reasons()); // New ring.
        assertEquals(FIRST_GAINED, change(report, "c7").black().transition());
        assertEquals(LAST_LOST, change(report, "b8").black().transition());
    }

    private static List<String> occupancyChanges(DirectRelationEvents report) {
        return report.squareControl().changes().stream().filter(c -> c.occupancy() != OccupancyChange.UNCHANGED)
                .map(Change::square).toList();
    }

    private static List<String> ids(List<Participant> pieces) {
        return pieces.stream().map(Participant::id).toList();
    }

    private static Change change(DirectRelationEvents report, String square) {
        return report.squareControl().changes().stream().filter(c -> c.square().equals(square)).findFirst().orElseThrow();
    }

    private static List<String> squares(DirectRelationEvents report) {
        return report.squareControl().changes().stream().map(Change::square).toList();
    }

    private static DirectRelationEvents report(String fen, String uci) {
        Board board = new Board();
        board.loadFromFen(fen);
        var graph = new AttackGraphCalculator();
        var snapshot = graph.snapshot(board);
        var move = board.legalMoves().stream().filter(m -> m.toString().equals(uci)).findFirst().orElseThrow();
        assertTrue(board.doMove(move));
        var result = new DirectRelationEventCalculator().calculate(uci, snapshot, graph.delta(snapshot, move, board));
        assertEquals(Status.AVAILABLE, result.squareControl().status());
        return result;
    }
}
