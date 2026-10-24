package dev.rightknight.model.attack;

import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Occupied-square geometric relations, including king targets. IDs are root-position squares. */
public record AttackSnapshot(int schemaVersion, List<RelationKind> calculatedKinds,
                             Map<String, PieceState> pieces, List<Relation> relations) {
    public AttackSnapshot {
        calculatedKinds = List.copyOf(calculatedKinds);
        pieces = Collections.unmodifiableMap(new LinkedHashMap<>(pieces));
        relations = List.copyOf(relations);
    }

    public enum RelationKind { ATTACK, DEFENCE }

    public record PieceState(Side color, PieceType type, String square) {
        public PieceState {
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(type, "type");
            if (type == PieceType.NONE || square == null || !square.matches("[a-h][1-8]")) {
                throw new IllegalArgumentException("Invalid piece state");
            }
        }
    }

    public record Relation(RelationKind kind, String sourceId, String targetId) {
        public Relation {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(targetId, "targetId");
            if (sourceId.equals(targetId)) throw new IllegalArgumentException("Self-relation");
        }
    }
}
