package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.SourcesChange;
import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.analysis.SquareControlEvents.Change;
import dev.rightknight.analysis.SquareControlEvents.OccupancyChange;
import dev.rightknight.analysis.SquareControlEvents.Reason;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackDelta.Control;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class SquareControlEventCalculator {
    private static final Set<String> CENTER = Set.of("d4", "e4", "d5", "e5");

    static SquareControlEvents calculate(AttackSnapshot root, AttackDelta delta, Map<String, PieceState> afterPieces) {
        if (root.schemaVersion() < 2) return new SquareControlEvents(Status.MISSING_DATA, List.of());

        var beforeTargets = Participant.bySquare(root.pieces());
        var afterTargets = Participant.bySquare(afterPieces);
        var removed = sourcesBySquare(delta.removedControls());
        var added = sourcesBySquare(delta.addedControls());
        var squares = new TreeSet<>(removed.keySet());
        squares.addAll(added.keySet());

        List<Change> changes = new ArrayList<>();
        for (String square : squares) {
            var before = beforeTargets.get(square);
            var after = afterTargets.get(square);
            var occupancy = occupancy(before, after);
            var reasons = EnumSet.noneOf(Reason.class);
            if (occupancy != OccupancyChange.UNCHANGED) reasons.add(Reason.OCCUPANCY_CHANGED);
            if (CENTER.contains(square)) reasons.add(Reason.CENTER);

            var oldControllers = root.squareControl().get(square);
            var newSources = added.getOrDefault(square, List.of());
            // King control in the stored graph is geometric. Old controllers plus additions cover both rings.
            oldControllers.values().forEach(ids -> addKingReasons(reasons, ids, root.pieces()));
            addKingReasons(reasons, newSources, afterPieces);
            if (reasons.isEmpty()) continue;

            var lostSources = removed.getOrDefault(square, List.of());
            var white = sources(Side.WHITE, oldControllers, lostSources, newSources, root.pieces(), afterPieces);
            var black = sources(Side.BLACK, oldControllers, lostSources, newSources, root.pieces(), afterPieces);
            changes.add(new Change(square, occupancy, before, after, white, black, List.copyOf(reasons)));
        }
        return new SquareControlEvents(Status.AVAILABLE, changes);
    }

    private static OccupancyChange occupancy(Participant before, Participant after) {
        if (before == null) return after == null ? OccupancyChange.UNCHANGED : OccupancyChange.OCCUPIED;
        if (after == null) return OccupancyChange.VACATED;
        return before.id().equals(after.id()) ? OccupancyChange.UNCHANGED : OccupancyChange.REPLACED;
    }

    private static Map<String, List<String>> sourcesBySquare(List<Control> controls) {
        Map<String, List<String>> result = new HashMap<>();
        for (var control : controls) {
            result.computeIfAbsent(control.square(), ignored -> new ArrayList<>()).add(control.sourceId());
        }
        return result;
    }

    private static void addKingReasons(EnumSet<Reason> reasons, List<String> sources, Map<String, PieceState> pieces) {
        for (String id : sources) {
            var piece = pieces.get(id);
            if (piece.type() == PieceType.KING) {
                reasons.add(piece.color() == Side.WHITE ? Reason.WHITE_KING_ZONE : Reason.BLACK_KING_ZONE);
            }
        }
    }

    private static SourcesChange sources(Side side, Map<Side, List<String>> before, List<String> removed,
                                         List<String> added, Map<String, PieceState> beforePieces,
                                         Map<String, PieceState> afterPieces) {
        return SourcesChange.compare(before.get(side).size(), participants(removed, side, beforePieces),
                participants(added, side, afterPieces), false);
    }

    private static List<Participant> participants(List<String> ids, Side side, Map<String, PieceState> pieces) {
        return ids.stream().filter(id -> pieces.get(id).color() == side).sorted()
                .map(id -> new Participant(id, pieces.get(id))).toList();
    }
}
