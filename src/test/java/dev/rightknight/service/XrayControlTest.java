package dev.rightknight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.model.attack.XrayControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class XrayControlTest {
    private final AttackGraphCalculator calculator = new AttackGraphCalculator();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void discussedBishopSupportsQueenWithoutChangingDirectControlOrRelations() {
        var graph = graph("2R2rk1/p3b1pp/1pnqp3/1Q1p4/P2P2P1/1N5P/1P1B1P2/6K1 w - - 1 31");
        assertTrue(graph.xrayControl().contains(x("e7", "c5", "d6")));
        assertTrue(graph.xrayControl().contains(x("e7", "b4", "d6")));
        assertTrue(graph.xrayControl().contains(x("e7", "a3", "d6")));
        assertFalse(graph.xrayControl().contains(x("e7", "d6", "d6")));
        assertEquals(List.of("d6"), graph.squareControl().get("c5").get(Side.BLACK));
        assertFalse(graph.relations().stream().anyMatch(r -> r.sourceId().equals("e7") && r.targetId().equals("b5")));
        assertEquals(graph.xrayControl().size(), new HashSet<>(graph.xrayControl()).size());
    }

    @Test
    void threePieceBatteryHasOrderedChainsAndStopsAtThirdIntermediate() {
        var graph = graph("7k/8/8/8/8/3R4/3R4/3Q3K w - - 0 1");
        assertTrue(graph.xrayControl().contains(x("d1", "d4", "d2", "d3")));
        assertTrue(graph.xrayControl().contains(x("d2", "d4", "d3")));
        assertTrue(graph.xrayControl().contains(x("d1", "d3", "d2")));
        assertEquals(List.of("d3"), graph.squareControl().get("d4").get(Side.WHITE));
        var fourth = graph("7k/8/8/8/3R4/3R4/3R4/3Q3K w - - 0 1");
        assertTrue(fourth.xrayControl().contains(x("d1", "d4", "d2", "d3")));
        assertFalse(fourth.xrayControl().stream().anyMatch(c -> c.sourceId().equals("d1") && c.square().equals("d5")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"N", "P", "B", "K", "r", "q", "p"})
    void includesFirstIncompatibleOrEnemyBlockerButDoesNotContinue(String blocker) {
        String king = blocker.equals("K") ? "8" : "7K";
        var first = graph("7k/8/8/8/8/3" + blocker + "4/3R4/" + king + " w - - 0 1");
        assertTrue(first.xrayControl().stream().noneMatch(c -> c.sourceId().equals("d2")));
        var second = graph("7k/8/8/8/3" + blocker + "4/3R4/3R4/" + king + " w - - 0 1");
        assertEquals(Set.of(x("d2", "d4", "d3")), from(second, "d2"));
    }

    @Test
    void compatibilityDependsOnRayAndWorksForBothColorsAndDirections() {
        var diagonal = graph("7k/8/8/4q3/3b4/2b5/8/K7 w - - 0 1");
        assertTrue(diagonal.xrayControl().contains(x("c3", "f6", "d4", "e5")));
        assertTrue(diagonal.xrayControl().contains(x("e5", "b2", "d4", "c3")));
        var blocked = graph("7k/8/8/8/3r4/2b5/8/K7 w - - 0 1");
        assertTrue(from(blocked, "c3").isEmpty());
        var horizontal = graph("7k/8/8/8/2qrR3/8/8/K7 w - - 0 1");
        assertEquals(Set.of(x("c4", "e4", "d4")), from(horizontal, "c4"));
        // Pinned intermediate still contributes geometric battery control.
        var pinnedBattery = graph("4r2k/8/8/8/8/4B3/3Q4/4K3 w - - 0 1");
        assertTrue(pinnedBattery.xrayControl().contains(x("d2", "f4", "e3")));
    }

    @Test
    void deltasTrackChainIdentityAndTransitionsToDirectControl() {
        var moved = move("7k/8/8/8/3R4/3R4/8/3Q3K w - - 0 1", "d3d2");
        var retained = x("d1", "d5", "d3", "d4");
        assertTrue(moved.before.xrayControl().contains(retained));
        assertFalse(moved.delta.addedXrayControls().contains(retained));
        assertFalse(moved.delta.removedXrayControls().contains(retained));
        var opened = move("7k/8/8/8/8/3R4/8/3Q3K w - - 0 1", "d3e3");
        assertTrue(opened.delta.removedXrayControls().contains(x("d1", "d4", "d3")));
        assertTrue(opened.delta.addedControls().contains(new AttackDelta.Control("d1", "d4")));
        // One intermediate leaves a three-piece battery: same source/square, different chain.
        var shorter = move("7k/8/8/8/3R4/3R4/8/3Q3K w - - 0 1", "d3e3");
        assertTrue(shorter.delta.removedXrayControls().contains(x("d1", "d5", "d3", "d4")));
        assertTrue(shorter.delta.addedXrayControls().contains(x("d1", "d5", "d4")));
    }

    @Test
    void specialMovesUseStableIdsForNewBatteriesAndRemoveCapturedIntermediates() {
        var castle = move("7k/8/8/8/8/5R2/8/4K2R w K - 0 1", "e1g1");
        assertTrue(castle.delta.addedXrayControls().contains(x("h1", "f4", "f3")));
        var ep = move("7k/8/8/RR1pP2r/8/8/8/6K1 w - d6 0 1", "e5d6");
        assertTrue(ep.delta.addedXrayControls().contains(x("a5", "h5", "b5")));
        var promotion = move("k6r/6P1/8/7R/8/8/8/K7 w - - 0 1", "g7h8q");
        assertTrue(promotion.delta.addedXrayControls().contains(x("g7", "h4", "h5")));
        var capture = move("7k/8/8/r2R4/8/3Q4/8/7K b - - 0 1", "a5d5");
        assertTrue(capture.delta.removedXrayControls().contains(x("d3", "d6", "d5")));
        assertFalse(capture.delta.addedXrayControls().stream().anyMatch(c -> c.throughIds().contains("d5")));
    }

    @Test
    void schema2RemainsUnknownAndSchema3RequiresImmutableCalculatedArrays() throws Exception {
        var result = move("7k/8/8/8/8/3R4/8/3Q3K w - - 0 1", "d3e3");
        ObjectNode oldSnapshot = mapper.valueToTree(result.before);
        oldSnapshot.put("schemaVersion", 2).remove("xrayControl");
        ObjectNode oldDelta = mapper.valueToTree(result.delta);
        oldDelta.put("schemaVersion", 2).remove(List.of("addedXrayControls", "removedXrayControls"));
        var snapshot = mapper.treeToValue(oldSnapshot, AttackSnapshot.class);
        var delta = mapper.treeToValue(oldDelta, AttackDelta.class);
        assertNull(snapshot.xrayControl());
        assertNull(delta.addedXrayControls());
        assertNull(delta.removedXrayControls());
        assertEquals(oldSnapshot, mapper.valueToTree(snapshot));
        assertEquals(oldDelta, mapper.valueToTree(delta));
        oldSnapshot.put("schemaVersion", 3);
        oldDelta.put("schemaVersion", 3);
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> mapper.treeToValue(oldSnapshot, AttackSnapshot.class));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> mapper.treeToValue(oldDelta, AttackDelta.class));
        assertThrows(UnsupportedOperationException.class, () -> result.before.xrayControl().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.before.xrayControl().getFirst().throughIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.delta.removedXrayControls().clear());
        assertThrows(IllegalArgumentException.class, () -> x("d1", "d5"));
        assertThrows(IllegalArgumentException.class, () -> x("d1", "d5", "d2", "d3", "d4"));
        assertThrows(IllegalArgumentException.class, () -> x("d1", "d5", "d2", "d2"));
        assertThrows(IllegalArgumentException.class, () -> x("d1", "d5", "d1"));
    }

    private AttackSnapshot graph(String fen) {
        var board = new Board();
        board.loadFromFen(fen);
        return calculator.snapshot(board);
    }

    private Result move(String fen, String uci) {
        var board = new Board();
        board.loadFromFen(fen);
        var before = calculator.snapshot(board);
        var move = board.legalMoves().stream().filter(m -> m.toString().equals(uci)).findFirst().orElseThrow();
        assertTrue(board.doMove(move));
        String afterFen = board.getFen();
        var delta = calculator.delta(before, move, board);
        assertEquals(afterFen, board.getFen());
        Map<String, String> idsBySquare = new HashMap<>();
        var pieces = new HashMap<>(before.pieces());
        delta.pieceChanges().forEach(c -> { if (c.after() == null) pieces.remove(c.pieceId()); else pieces.put(c.pieceId(), c.after()); });
        pieces.forEach((id, piece) -> idsBySquare.put(piece.square(), id));
        var reconstructed = new HashSet<>(before.xrayControl());
        assertTrue(reconstructed.containsAll(delta.removedXrayControls()));
        assertTrue(Collections.disjoint(reconstructed, delta.addedXrayControls()));
        reconstructed.removeAll(delta.removedXrayControls());
        reconstructed.addAll(delta.addedXrayControls());
        var expected = new HashSet<XrayControl>();
        calculator.snapshot(board).xrayControl().forEach(c -> expected.add(new XrayControl(
                idsBySquare.get(c.sourceId()), c.square(), c.throughIds().stream().map(idsBySquare::get).toList())));
        assertEquals(expected, reconstructed, uci);
        return new Result(before, delta);
    }

    private Set<XrayControl> from(AttackSnapshot graph, String source) {
        Set<XrayControl> result = new HashSet<>();
        graph.xrayControl().stream().filter(c -> c.sourceId().equals(source)).forEach(result::add);
        return result;
    }

    private XrayControl x(String source, String square, String... through) {
        return new XrayControl(source, square, List.of(through));
    }

    private record Result(AttackSnapshot before, AttackDelta delta) {}
}
