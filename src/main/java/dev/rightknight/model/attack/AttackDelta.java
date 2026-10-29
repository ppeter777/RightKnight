package dev.rightknight.model.attack;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;
import dev.rightknight.model.attack.AttackSnapshot.Relation;
import java.util.List;
import java.util.Objects;

/** Relative to the parent analysis's snapshot; never compare deltas from different roots/versions. */
public record AttackDelta(int schemaVersion, String moveUci, List<PieceChange> pieceChanges,
                          List<Relation> addedRelations, List<Relation> removedRelations,
                          @JsonInclude(JsonInclude.Include.NON_NULL) List<Control> addedControls,
                          @JsonInclude(JsonInclude.Include.NON_NULL) List<Control> removedControls) {
    public AttackDelta {
        pieceChanges = List.copyOf(pieceChanges);
        addedRelations = List.copyOf(addedRelations);
        removedRelations = List.copyOf(removedRelations);
        if (schemaVersion >= 2 && (addedControls == null || removedControls == null)) {
            throw new IllegalArgumentException("Schema v2 requires both control deltas");
        }
        addedControls = addedControls == null ? null : List.copyOf(addedControls);
        removedControls = removedControls == null ? null : List.copyOf(removedControls);
    }

    /** A stable source ID controlling a board square (not a target piece ID). */
    public record Control(String sourceId, String square) {
        public Control {
            Objects.requireNonNull(sourceId, "sourceId");
            if (square == null || !square.matches("[a-h][1-8]")) {
                throw new IllegalArgumentException("Invalid controlled square");
            }
        }
    }

    /** after=null means captured, including en passant. Promotion keeps the ID and changes type. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PieceChange(String pieceId, PieceState before, PieceState after) {}
}
