package dev.rightknight.model.attack;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;
import dev.rightknight.model.attack.AttackSnapshot.Relation;
import java.util.List;

/** Relative to the parent analysis's snapshot; never compare deltas from different roots/versions. */
public record AttackDelta(int schemaVersion, String moveUci, List<PieceChange> pieceChanges,
                          List<Relation> addedRelations, List<Relation> removedRelations) {
    public AttackDelta {
        pieceChanges = List.copyOf(pieceChanges);
        addedRelations = List.copyOf(addedRelations);
        removedRelations = List.copyOf(removedRelations);
    }

    /** after=null means captured, including en passant. Promotion keeps the ID and changes type. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PieceChange(String pieceId, PieceState before, PieceState after) {}
}
