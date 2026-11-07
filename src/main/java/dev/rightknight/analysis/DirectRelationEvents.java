package dev.rightknight.analysis;

import dev.rightknight.model.attack.AttackSnapshot.PieceState;
import dev.rightknight.model.attack.AttackSnapshot.RelationKind;

import java.util.List;

/** Direct geometric relations for one move, relative to its own root snapshot. */
public record DirectRelationEvents(String moveUci, Status status, List<RelationKind> calculatedKinds,
                                   List<TargetChange> targets) {
    public DirectRelationEvents {
        calculatedKinds = List.copyOf(calculatedKinds);
        targets = List.copyOf(targets);
    }

    public enum Status { AVAILABLE, MISSING_DATA, INCOMPATIBLE_DATA }
    public enum Transition { UNCHANGED, FIRST_GAINED, LAST_LOST, REPLACED, CHANGED, TARGET_CAPTURED }

    public record Participant(String id, PieceState state) {}

    /** after is null for a capture. A null attacks/defences field means that kind was not calculated. */
    public record TargetChange(String id, PieceState before, PieceState after,
                               SourcesChange attacks, SourcesChange defences) {}

    /** Removed sources are described before the move, added sources after it. */
    public record SourcesChange(int beforeCount, int afterCount, List<Participant> removed,
                                List<Participant> added, Transition transition) {
        public SourcesChange {
            removed = List.copyOf(removed);
            added = List.copyOf(added);
        }
    }
}
