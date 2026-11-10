package dev.rightknight.analysis;

import dev.rightknight.model.attack.AttackSnapshot.PieceState;
import dev.rightknight.model.attack.AttackSnapshot.RelationKind;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Direct geometric relations for one move, relative to its own root snapshot. */
public record DirectRelationEvents(String moveUci, Status status, List<RelationKind> calculatedKinds,
                                   List<TargetChange> targets, BatteryEvents batteries, SquareControlEvents squareControl,
                                   UndefendedPieceEvents undefendedPieces) {
    public DirectRelationEvents {
        if (undefendedPieces == null) {
            undefendedPieces = new UndefendedPieceEvents(
                    status == Status.INCOMPATIBLE_DATA ? status : Status.MISSING_DATA, List.of());
        }
        calculatedKinds = List.copyOf(calculatedKinds);
        targets = List.copyOf(targets);
    }

    /** Direct-only reports created by earlier callers carry no battery data. */
    public DirectRelationEvents(String moveUci, Status status, List<RelationKind> calculatedKinds,
                                List<TargetChange> targets) {
        this(moveUci, status, calculatedKinds, targets, new BatteryEvents(
                status == Status.INCOMPATIBLE_DATA ? status : Status.MISSING_DATA, List.of()));
    }

    public DirectRelationEvents(String moveUci, Status status, List<RelationKind> calculatedKinds,
                                List<TargetChange> targets, BatteryEvents batteries) {
        this(moveUci, status, calculatedKinds, targets, batteries, new SquareControlEvents(
                status == Status.INCOMPATIBLE_DATA ? status : Status.MISSING_DATA, List.of()));
    }

    public DirectRelationEvents(String moveUci, Status status, List<RelationKind> calculatedKinds,
                                List<TargetChange> targets, BatteryEvents batteries, SquareControlEvents squareControl) {
        this(moveUci, status, calculatedKinds, targets, batteries, squareControl, null);
    }

    public enum Status { AVAILABLE, MISSING_DATA, INCOMPATIBLE_DATA }
    public enum Transition { UNCHANGED, FIRST_GAINED, LAST_LOST, REPLACED, CHANGED, TARGET_CAPTURED }

    public record Participant(String id, PieceState state) {
        static Map<String, Participant> bySquare(Map<String, PieceState> pieces) {
            Map<String, Participant> result = new HashMap<>();
            pieces.forEach((id, state) -> result.put(state.square(), new Participant(id, state)));
            return result;
        }
    }

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

        static SourcesChange compare(int beforeCount, List<Participant> removed, List<Participant> added,
                                     boolean capturedTarget) {
            int afterCount = beforeCount - removed.size() + added.size();
            Transition transition = transition(beforeCount, afterCount, !removed.isEmpty() || !added.isEmpty(), capturedTarget);
            return new SourcesChange(beforeCount, afterCount, removed, added, transition);
        }

        private static Transition transition(int before, int after, boolean changed, boolean captured) {
            if (!changed) return Transition.UNCHANGED;
            if (captured) return Transition.TARGET_CAPTURED;
            if (before == 0) return Transition.FIRST_GAINED;
            if (after == 0) return Transition.LAST_LOST;
            if (before == after) return Transition.REPLACED;
            return Transition.CHANGED;
        }
    }
}
