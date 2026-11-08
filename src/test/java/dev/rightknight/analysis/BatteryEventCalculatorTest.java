package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import dev.rightknight.analysis.BatteryEvents.Change;
import dev.rightknight.analysis.BatteryEvents.ControlKind;
import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.service.AttackGraphCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static dev.rightknight.analysis.BatteryEvents.Transition.*;
import static org.junit.jupiter.api.Assertions.*;

class BatteryEventCalculatorTest {
    @ParameterizedTest
    @CsvSource({
            "6q1/5b2, 3B4, ATTACK, BISHOP, QUEEN",
            "6b1/5q2, 3B4, ATTACK, QUEEN, BISHOP",
            "6q1/5b2, 3b4, DEFENCE, BISHOP, QUEEN",
            "6b1/5q2, 3b4, DEFENCE, QUEEN, BISHOP"
    })
    void openedDiagonalPreservesPieceOrderAndTargetColor(String sliders, String target, ControlKind kind,
                                                         String front, String rear) {
        var change = onlyAt(events("k7/" + sliders + "/4P3/" + target + "/1N6/8/7K w - - 0 1", "e5e6"), "d4");
        assertEquals(FORMED, change.transition());
        assertEquals(kind, change.after().controlKind());
        assertEquals(Side.BLACK, change.side());
        assertEquals("NE", change.direction());
        assertEquals(BatteryEvents.SquareReason.OCCUPIED_TARGET, change.reason());
        assertTrue(change.before().directControllers().isEmpty());
        assertEquals(List.of("f6"), ids(change.after().directControllers()));
        assertEquals(List.of("f6", "g7"), ids(change.after().battery()));
        assertEquals(List.of(front, rear), change.after().battery().stream().map(p -> p.state().type().name()).toList());
    }

    @Test
    void addingAndRemovingRearSupportLeavesTheDirectAttackUnchanged() {
        var added = onlyAt(events("k5q1/8/5b2/8/3B4/1N6/8/7K b - - 0 1", "g8g7"), "d4");
        assertEquals(FORMED, added.transition());
        assertEquals(added.before().directControllers(), added.after().directControllers());
        assertEquals(List.of("f6"), ids(added.after().directControllers()));
        assertEquals(0, added.before().supportCount());
        assertEquals(1, added.after().supportCount());
        assertEquals("g8", added.after().battery().getLast().id());
        assertEquals("g7", added.after().battery().getLast().state().square());

        var removed = onlyAt(events("k7/6q1/5b2/8/3B4/1N6/8/7K b - - 0 1", "g7g8"), "d4");
        assertEquals(DISSOLVED, removed.transition());
        assertEquals(1, removed.before().supportCount());
        assertEquals(0, removed.after().supportCount());
        assertEquals(List.of("f6"), ids(removed.after().directControllers()));
    }

    @ParameterizedTest
    @CsvSource({"d2e2, d4 d3 d1", "d4e4, d3 d2 d1"})
    void threePiecesCountOnceAndEqualLengthReplacementsRemainVisible(String move, String after) {
        var change = onlyAt(events("7k/8/8/3p4/3R4/3R4/3R4/3Q3K w - - 0 1", move), "d5");
        assertEquals(COMPOSITION_CHANGED, change.transition());
        assertEquals(List.of("d4", "d3", "d2"), ids(change.before().battery()));
        assertEquals(List.of(after.split(" ")), ids(change.after().battery()));
        assertEquals(2, change.before().supportCount());
        assertEquals(2, change.after().supportCount());
    }

    @Test
    void losingTheMiddleRookShortensOneRayAndKeepsTheIndependentKnight() {
        var ray = onlyAt(events("7k/8/8/3p4/3R1N2/3R4/8/3Q3K w - - 0 1", "d3e3"), "d5");
        assertEquals(COMPOSITION_CHANGED, ray.transition());
        assertEquals(List.of("d4", "d3", "d1"), ids(ray.before().battery()));
        assertEquals(List.of("d4", "d1"), ids(ray.after().battery()));
        assertEquals(List.of("d4", "f4"), ids(ray.after().directControllers()));
        assertEquals(2, ray.before().supportCount());
        assertEquals(1, ray.after().supportCount());
    }

    @Test
    void movingAlongTheSameRayDoesNotChangeMembership() {
        var result = events("k7/6q1/5b2/8/3B4/1N6/8/7K b - - 0 1", "f6e5");
        assertTrue(result.changes().stream().noneMatch(e -> e.square().equals("d4")));
    }

    @Test
    void landingOnTwoRetainedRaysKeepsTheirSourcesSeparate() {
        var result = events("k7/3b1q2/4br2/8/3N4/8/8/7K w - - 0 1", "d4f5");
        var rays = result.changes().stream().filter(e -> e.square().equals("f5")).toList();
        assertEquals(List.of("N", "NW"), rays.stream().map(Change::direction).toList());
        assertEquals(List.of("f6", "f7"), ids(rays.getFirst().after().battery()));
        assertEquals(List.of("e6", "d7"), ids(rays.getLast().after().battery()));
        for (var ray : rays) {
            assertEquals(TARGET_CHANGED, ray.transition());
            assertEquals(BatteryEvents.SquareReason.MOVE_DESTINATION, ray.reason());
            assertNull(ray.before().target());
            assertEquals("d4", ray.after().target().id());
            assertEquals("f5", ray.after().target().state().square());
            assertEquals(List.of("e6", "f6"), ids(ray.after().directControllers()));
        }
    }

    private static Change onlyAt(BatteryEvents result, String square) {
        var changes = result.changes().stream().filter(e -> e.square().equals(square)).toList();
        assertEquals(1, changes.size(), square);
        return changes.getFirst();
    }

    private static List<String> ids(List<Participant> participants) {
        return participants.stream().map(Participant::id).toList();
    }

    private static BatteryEvents events(String fen, String uci) {
        Board board = new Board();
        board.loadFromFen(fen);
        var graph = new AttackGraphCalculator();
        var snapshot = graph.snapshot(board);
        var move = board.legalMoves().stream().filter(m -> m.toString().equals(uci)).findFirst().orElseThrow();
        assertTrue(board.doMove(move));
        var delta = graph.delta(snapshot, move, board);
        var events = new DirectRelationEventCalculator().calculate(uci, snapshot, delta).batteries();
        assertEquals(Status.AVAILABLE, events.status());
        return events;
    }
}
