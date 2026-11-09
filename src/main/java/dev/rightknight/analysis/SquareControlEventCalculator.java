package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.Side;
import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.SourcesChange;
import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.analysis.DirectRelationEvents.Transition;
import dev.rightknight.analysis.SquareControlEvents.Change;
import dev.rightknight.analysis.SquareControlEvents.OccupancyChange;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackDelta.Control;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

final class SquareControlEventCalculator {
    static SquareControlEvents calculate(AttackSnapshot root, AttackDelta delta, Map<String, PieceState> afterPieces) {
        if (root.schemaVersion() < 2) return new SquareControlEvents(Status.MISSING_DATA, List.of());

        // Piece changes include both castling pieces and the captured pawn's actual square in en passant.
        Map<String, Participant> departures = new HashMap<>();
        Map<String, Participant> arrivals = new HashMap<>();
        for (var piece : delta.pieceChanges()) {
            if (piece.before() != null) {
                departures.put(piece.before().square(), new Participant(piece.pieceId(), piece.before()));
            }
            if (piece.after() != null) {
                arrivals.put(piece.after().square(), new Participant(piece.pieceId(), piece.after()));
            }
        }

        var squares = new TreeSet<>(departures.keySet());
        squares.addAll(arrivals.keySet());
        List<Change> changes = new ArrayList<>();
        for (String square : squares) {
            var before = departures.get(square);
            var after = arrivals.get(square);
            if (before != null && after != null && before.id().equals(after.id())) continue;

            var white = sources(square, Side.WHITE, root, delta, afterPieces);
            var black = sources(square, Side.BLACK, root, delta, afterPieces);
            if (white.transition() == Transition.UNCHANGED && black.transition() == Transition.UNCHANGED) continue;
            OccupancyChange occupancy = OccupancyChange.REPLACED;
            if (before == null) occupancy = OccupancyChange.OCCUPIED;
            if (after == null) occupancy = OccupancyChange.VACATED;
            changes.add(new Change(square, occupancy, before, after, white, black));
        }
        return new SquareControlEvents(Status.AVAILABLE, changes);
    }

    private static SourcesChange sources(String square, Side side, AttackSnapshot root, AttackDelta delta,
                                         Map<String, PieceState> afterPieces) {
        int beforeCount = root.squareControl().get(square).get(side).size();
        var removed = participants(delta.removedControls(), square, side, root.pieces());
        var added = participants(delta.addedControls(), square, side, afterPieces);
        return SourcesChange.compare(beforeCount, removed, added, false);
    }

    private static List<Participant> participants(List<Control> controls, String square, Side side,
                                                  Map<String, PieceState> pieces) {
        return controls.stream().filter(control -> control.square().equals(square)).map(Control::sourceId)
                .filter(id -> pieces.get(id).color() == side).sorted()
                .map(id -> new Participant(id, pieces.get(id))).toList();
    }
}
