package dev.rightknight.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import dev.rightknight.model.attack.AttackDelta.Control;
import dev.rightknight.model.attack.AttackDelta.PieceChange;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;
import dev.rightknight.model.attack.AttackSnapshot.Relation;
import dev.rightknight.model.attack.AttackSnapshot.RelationKind;
import dev.rightknight.model.attack.AttackSnapshot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure geometric calculation for standard chess. Does not mutate the supplied boards. */
public final class AttackGraphCalculator {
    public static final int FEATURES_VERSION = 2;
    private static final int SCHEMA_VERSION = 2;
    private static final List<RelationKind> KINDS = List.of(RelationKind.ATTACK, RelationKind.DEFENCE);

    public AttackSnapshot snapshot(Board board) {
        Map<String, PieceState> pieces = new LinkedHashMap<>();
        for (Square square : Square.values()) {
            if (square == Square.NONE) continue;
            Piece piece = board.getPiece(square);
            if (piece != Piece.NONE) pieces.put(name(square), state(piece, square));
        }
        return graph(board, pieces);
    }

    /** afterBoard must be the result of applying this legal move to before, once. */
    public AttackDelta delta(AttackSnapshot before, Move move, Board afterBoard) {
        if (before.schemaVersion() != SCHEMA_VERSION || !before.calculatedKinds().equals(KINDS)) {
            throw new IllegalArgumentException("Unsupported attack snapshot");
        }
        String from = name(move.getFrom());
        PieceState moving = before.pieces().values().stream().filter(p -> p.square().equals(from))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Missing moving piece"));
        boolean castle = moving.type() == PieceType.KING
                && Math.abs(move.getTo().getFile().ordinal() - move.getFrom().getFile().ordinal()) == 2;
        boolean kingSide = move.getTo().getFile().ordinal() > move.getFrom().getFile().ordinal();
        String rookFrom = (kingSide ? "h" : "a") + from.charAt(1);
        String rookTo = (kingSide ? "f" : "d") + from.charAt(1);
        Map<String, PieceState> afterPieces = new LinkedHashMap<>();
        List<PieceChange> changes = new ArrayList<>();
        before.pieces().forEach((id, original) -> {
            String destination = original.square();
            if (destination.equals(from)) destination = name(move.getTo());
            else if (castle && destination.equals(rookFrom)) destination = rookTo;
            Square square = Square.valueOf(destination.toUpperCase(Locale.ROOT));
            Piece actual = afterBoard.getPiece(square);
            // Unmoved pieces disappearing from their squares were captured. This also covers EP.
            PieceState after = actual == Piece.NONE || actual.getPieceSide() != original.color()
                    ? null : state(actual, square);
            if (after != null) afterPieces.put(id, after);
            if (!original.equals(after)) changes.add(new PieceChange(id, original, after));
        });
        var after = graph(afterBoard, afterPieces);
        List<Relation> afterRelations = after.relations();
        var beforeControls = controls(before);
        var afterControls = controls(after);
        var oldControls = new HashSet<>(beforeControls);
        var newControls = new HashSet<>(afterControls);
        var oldSet = new HashSet<>(before.relations());
        var newSet = new HashSet<>(afterRelations);
        return new AttackDelta(SCHEMA_VERSION, move.toString(), changes,
                afterRelations.stream().filter(r -> !oldSet.contains(r)).toList(),
                before.relations().stream().filter(r -> !newSet.contains(r)).toList(),
                afterControls.stream().filter(c -> !oldControls.contains(c)).toList(),
                beforeControls.stream().filter(c -> !newControls.contains(c)).toList());
    }

    private AttackSnapshot graph(Board board, Map<String, PieceState> pieces) {
        Map<String, Map<Side, List<String>>> control = new LinkedHashMap<>();
        for (Square square : Square.values()) {
            if (square == Square.NONE) continue;
            Map<Side, List<String>> sides = new EnumMap<>(Side.class);
            sides.put(Side.WHITE, new ArrayList<>());
            sides.put(Side.BLACK, new ArrayList<>());
            control.put(name(square), sides);
            for (var source : pieces.entrySet()) {
                Square from = Square.valueOf(source.getValue().square().toUpperCase(Locale.ROOT));
                if (DirectAttackDetector.attacks(board, from, square, board.getPiece(from))) {
                    sides.get(source.getValue().color()).add(source.getKey());
                }
            }
        }
        // Occupied-square relations are a projection of control, never a separate geometry calculation.
        List<Relation> relations = new ArrayList<>();
        for (var target : pieces.entrySet()) {
            control.get(target.getValue().square()).forEach((color, sources) -> {
                RelationKind kind = color == target.getValue().color() ? RelationKind.DEFENCE : RelationKind.ATTACK;
                if (kind == RelationKind.DEFENCE && target.getValue().type() == PieceType.KING) return;
                sources.forEach(source -> relations.add(new Relation(kind, source, target.getKey())));
            });
        }
        return new AttackSnapshot(SCHEMA_VERSION, KINDS, pieces, relations, control);
    }

    private List<Control> controls(AttackSnapshot snapshot) {
        List<Control> result = new ArrayList<>();
        snapshot.squareControl().forEach((square, sides) ->
                sides.values().forEach(ids -> ids.forEach(id -> result.add(new Control(id, square)))));
        return result;
    }

    private static PieceState state(Piece piece, Square square) {
        return new PieceState(piece.getPieceSide(), piece.getPieceType(), name(square));
    }

    private static String name(Square square) {
        return square.name().toLowerCase(Locale.ROOT);
    }
}
