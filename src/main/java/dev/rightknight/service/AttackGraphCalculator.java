package dev.rightknight.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackDelta.PieceChange;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;
import dev.rightknight.model.attack.AttackSnapshot.Relation;
import dev.rightknight.model.attack.AttackSnapshot.RelationKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure geometric calculation for standard chess. Does not mutate the supplied boards. */
public final class AttackGraphCalculator {
    public static final int FEATURES_VERSION = 1;
    private static final int SCHEMA_VERSION = 1;
    private static final List<RelationKind> KINDS = List.of(RelationKind.ATTACK, RelationKind.DEFENCE);

    public AttackSnapshot snapshot(Board board) {
        Map<String, PieceState> pieces = new LinkedHashMap<>();
        for (Square square : Square.values()) {
            if (square == Square.NONE) continue;
            Piece piece = board.getPiece(square);
            if (piece != Piece.NONE) pieces.put(name(square), state(piece, square));
        }
        return new AttackSnapshot(SCHEMA_VERSION, KINDS, pieces, relations(board, pieces));
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
        List<Relation> afterRelations = relations(afterBoard, afterPieces);
        var oldSet = new HashSet<>(before.relations());
        var newSet = new HashSet<>(afterRelations);
        return new AttackDelta(SCHEMA_VERSION, move.toString(), changes,
                afterRelations.stream().filter(r -> !oldSet.contains(r)).toList(),
                before.relations().stream().filter(r -> !newSet.contains(r)).toList());
    }

    private List<Relation> relations(Board board, Map<String, PieceState> pieces) {
        List<Relation> result = new ArrayList<>();
        for (var source : pieces.entrySet()) {
            Square from = Square.valueOf(source.getValue().square().toUpperCase(Locale.ROOT));
            Piece attacker = board.getPiece(from);
            for (var target : pieces.entrySet()) {
                if (source.getKey().equals(target.getKey())) continue;
                Square to = Square.valueOf(target.getValue().square().toUpperCase(Locale.ROOT));
                if (DirectAttackDetector.attacks(board, from, to, attacker)) {
                    RelationKind kind = source.getValue().color() == target.getValue().color()
                            ? RelationKind.DEFENCE : RelationKind.ATTACK;
                    result.add(new Relation(kind, source.getKey(), target.getKey()));
                }
            }
        }
        return List.copyOf(result);
    }

    private static PieceState state(Piece piece, Square square) {
        return new PieceState(piece.getPieceSide(), piece.getPieceType(), name(square));
    }

    private static String name(Square square) {
        return square.name().toLowerCase(Locale.ROOT);
    }
}
