package dev.rightknight.model.attack;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Geometric relations and all-square control. IDs remain root-position squares. */
public record AttackSnapshot(int schemaVersion, List<RelationKind> calculatedKinds,
                             Map<String, PieceState> pieces, List<Relation> relations,
                             @JsonInclude(JsonInclude.Include.NON_NULL)
                             Map<String, Map<Side, List<String>>> squareControl,
                             @JsonInclude(JsonInclude.Include.NON_NULL) List<XrayControl> xrayControl) {
    public AttackSnapshot {
        calculatedKinds = List.copyOf(calculatedKinds);
        pieces = Collections.unmodifiableMap(new LinkedHashMap<>(pieces));
        relations = List.copyOf(relations);
        if (schemaVersion >= 3 && xrayControl == null) {
            throw new IllegalArgumentException("Schema v3 requires xrayControl");
        }
        xrayControl = xrayControl == null ? null : List.copyOf(xrayControl);
        // Missing v1 data is unknown, never an empty, calculated control map.
        if (schemaVersion >= 2 && squareControl == null) {
            throw new IllegalArgumentException("Schema v2 requires squareControl");
        }
        if (squareControl != null) {
            if (squareControl.size() != 64) throw new IllegalArgumentException("Expected 64 squares");
            Map<String, Map<Side, List<String>>> copy = new LinkedHashMap<>();
            for (Square square : Square.values()) {
                if (square == Square.NONE) continue;
                String name = square.name().toLowerCase(Locale.ROOT);
                var sides = Objects.requireNonNull(squareControl.get(name), "Missing square " + name);
                if (!sides.keySet().equals(EnumSet.allOf(Side.class))) {
                    throw new IllegalArgumentException("Expected both colors at " + name);
                }
                Map<Side, List<String>> sideCopy = new EnumMap<>(Side.class);
                sides.forEach((side, ids) -> sideCopy.put(side, List.copyOf(ids)));
                copy.put(name, Collections.unmodifiableMap(sideCopy));
            }
            squareControl = Collections.unmodifiableMap(copy);
        }
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
