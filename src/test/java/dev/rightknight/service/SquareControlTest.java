package dev.rightknight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.*;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.model.attack.AttackSnapshot.Relation;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.*;
import static org.junit.jupiter.api.Assertions.*;

class SquareControlTest {
    private final AttackGraphCalculator calculator = new AttackGraphCalculator();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void all64SquaresHaveBothColorsIncludingEmptyListsAndMapsAreDeeplyImmutable() {
        var graph = graph("7k/8/8/8/8/8/8/K7 w - - 0 1");
        assertEquals(2, graph.schemaVersion());
        assertEquals(64, graph.squareControl().size());
        graph.squareControl().values().forEach(s -> assertEquals(Set.of(Side.WHITE, Side.BLACK), s.keySet()));
        assertEquals(Map.of(Side.WHITE, List.of(), Side.BLACK, List.of()), graph.squareControl().get("e5"));
        assertEquals(Set.of("a2", "b1", "b2"), controlledBy(graph, "a1"));
        assertThrows(UnsupportedOperationException.class, () -> graph.squareControl().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.squareControl().get("a2").clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.squareControl().get("a2").get(Side.WHITE).clear());
    }

    @Test
    void pawnControlUsesDiagonalsNotPushesEvenOnEmptySquares() {
        var graph = graph("7k/8/4p3/8/3P4/8/8/7K w - - 0 1");
        assertEquals(Set.of("c5", "e5"), controlledBy(graph, "d4"));
        assertEquals(Set.of("d5", "f5"), controlledBy(graph, "e6"));
        assertEquals(List.of("e6"), graph.squareControl().get("d5").get(Side.BLACK));
        assertEquals(List.of(), graph.squareControl().get("d5").get(Side.WHITE));
    }

    @Test
    void slidingControlIncludesFirstBlockerOfEitherColorButNothingBeyondIt() {
        var graph = graph("7k/8/n7/8/8/P7/8/R6K w - - 0 1");
        assertEquals(Set.of("a2", "a3", "b1", "c1", "d1", "e1", "f1", "g1", "h1"), controlledBy(graph, "a1"));
        var enemy = graph("7k/8/N7/8/8/p7/8/R6K w - - 0 1");
        assertEquals(controlledBy(graph, "a1"), controlledBy(enemy, "a1"));
        assertTrue(enemy.relations().contains(new Relation(ATTACK, "a1", "a3")));
        assertFalse(enemy.relations().contains(new Relation(DEFENCE, "a1", "a6")));
    }

    @Test
    void bishopQueenKnightAndPinnedPieceControlAreGeometric() {
        var bishop = graph("7k/8/5p2/8/3B4/8/8/K7 w - - 0 1");
        assertEquals(Set.of("a1", "b2", "c3", "e5", "f6", "c5", "b6", "a7", "e3", "f2", "g1"), controlledBy(bishop, "d4"));
        var queen = graph("7k/8/8/8/3Q4/8/8/K7 w - - 0 1");
        assertEquals(27, controlledBy(queen, "d4").size());
        assertFalse(controlledBy(queen, "d4").contains("d4"));
        var pinned = graph("4r2k/8/8/8/8/8/4N3/4K3 w - - 0 1");
        assertEquals(Set.of("c1", "c3", "d4", "f4", "g3", "g1"), controlledBy(pinned, "e2"));
    }

    @Test
    void kingDefenceTargetsAreExcludedButKingSourcesChecksAndKingSquareControlRemain() {
        var graph = graph("r6k/8/8/8/8/8/8/R5RK w - - 0 1");
        assertFalse(graph.relations().stream().anyMatch(r -> r.kind() == DEFENCE && graph.pieces().get(r.targetId()).type() == PieceType.KING));
        assertTrue(graph.relations().contains(new Relation(DEFENCE, "h1", "g1")));
        assertTrue(controlledBy(graph, "g1").contains("h1"));
        var checking = graph("7k/8/8/8/8/8/8/K6R b - - 0 1");
        assertTrue(checking.relations().contains(new Relation(ATTACK, "h1", "h8")));
        assertTrue(controlledBy(checking, "h1").contains("h8"));
    }

    @Test
    void occupiedSquareRelationsAreExactlyControlProjectionWithKingDefenceFiltered() {
        var graph = graph("r1bqkb1r/pp3ppp/n3p3/3pPn2/3p4/N1P2N2/PP2BPPP/R1BQ1RK1 w kq - 0 10");
        Set<Relation> expected = new HashSet<>();
        graph.pieces().forEach((targetId, target) -> graph.squareControl().get(target.square()).forEach((color, sources) -> {
            var kind = color == target.color() ? DEFENCE : ATTACK;
            if (kind == DEFENCE && target.type() == PieceType.KING) return;
            sources.forEach(source -> expected.add(new Relation(kind, source, targetId)));
        }));
        assertEquals(expected, new HashSet<>(graph.relations()));
    }

    @Test
    void schema1JsonRemainsUnknownAndPreservesHistoricalKingDefence() throws Exception {
        var snapshot = mapper.readValue(new ClassPathResource("attack-features/v1-snapshot.json").getContentAsByteArray(), AttackSnapshot.class);
        var delta = mapper.readValue(new ClassPathResource("attack-features/v1-delta.json").getContentAsByteArray(), AttackDelta.class);
        assertEquals(1, snapshot.schemaVersion());
        assertEquals(1, delta.schemaVersion());
        assertNull(snapshot.squareControl());
        assertNull(delta.addedControls());
        assertNull(delta.removedControls());
        assertTrue(snapshot.relations().contains(new Relation(DEFENCE, "a1", "h1")));
        assertFalse(mapper.readTree(mapper.writeValueAsString(snapshot)).has("squareControl"));
        assertFalse(mapper.readTree(mapper.writeValueAsString(delta)).has("addedControls"));
        assertEquals(snapshot, mapper.readValue(mapper.writeValueAsString(snapshot), AttackSnapshot.class));
        Board board = new Board();
        board.loadFromFen("r6k/8/8/8/8/8/8/R6K w - - 0 1");
        var move = board.legalMoves().stream().filter(m -> m.toString().equals("a1a2")).findFirst().orElseThrow();
        board.doMove(move);
        assertThrows(IllegalArgumentException.class, () -> calculator.delta(snapshot, move, board));
    }

    @Test
    void schema2RequiresCalculatedFieldsAndSerializesColorKeysAsAgreed() throws Exception {
        var graph = graph("7k/8/8/8/8/8/8/K7 w - - 0 1");
        var json = mapper.readTree(mapper.writeValueAsString(graph));
        assertEquals("a1", json.at("/squareControl/a2/WHITE/0").asText());
        assertTrue(json.at("/squareControl/e5/BLACK").isArray());
        assertEquals(graph, mapper.readValue(json.toString(), AttackSnapshot.class));
        assertThrows(IllegalArgumentException.class, () -> new AttackSnapshot(2, graph.calculatedKinds(), graph.pieces(), graph.relations(), null));
        assertThrows(IllegalArgumentException.class, () -> new AttackDelta(2, "a1a2", List.of(), List.of(), List.of(), null, null));
    }

    private AttackSnapshot graph(String fen) {
        Board board = new Board();
        board.loadFromFen(fen);
        return calculator.snapshot(board);
    }

    private Set<String> controlledBy(AttackSnapshot snapshot, String sourceId) {
        Set<String> squares = new HashSet<>();
        snapshot.squareControl().forEach((square, sides) -> {
            if (sides.get(snapshot.pieces().get(sourceId).color()).contains(sourceId)) squares.add(square);
        });
        return squares;
    }
}
