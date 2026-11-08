package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import dev.rightknight.analysis.BatteryEvents.*;
import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;
import dev.rightknight.model.attack.XrayControl;

import java.util.*;

final class BatteryEventCalculator {
    private record Ray(String square, Side side, String direction) {}
    private static final Comparator<Ray> ORDER = Comparator.comparing(Ray::square)
            .thenComparing(Ray::side).thenComparing(Ray::direction);

    static BatteryEvents calculate(AttackSnapshot root, AttackDelta delta, Map<String, PieceState> afterPieces) {
        if (root.schemaVersion() < 3) return new BatteryEvents(Status.MISSING_DATA, List.of());

        var afterControls = new HashSet<>(root.xrayControl());
        afterControls.removeAll(delta.removedXrayControls());
        afterControls.addAll(delta.addedXrayControls());

        var beforeTargets = occupants(root.pieces());
        var afterTargets = occupants(afterPieces);
        String destination = delta.moveUci().substring(2, 4);
        var squares = new HashSet<>(beforeTargets.keySet());
        squares.addAll(afterTargets.keySet());
        squares.add(destination);

        var before = batteries(root.xrayControl(), root.pieces(), squares);
        var after = batteries(afterControls, afterPieces, squares);
        var rays = new TreeSet<>(ORDER);
        rays.addAll(before.keySet());
        rays.addAll(after.keySet());

        List<Change> changes = new ArrayList<>();
        for (var ray : rays) {
            String square = ray.square();
            var oldIds = before.getOrDefault(ray, List.of());
            var newIds = after.getOrDefault(ray, List.of());
            var oldTarget = beforeTargets.get(square);
            var newTarget = afterTargets.get(square);
            if (oldIds.equals(newIds) && Objects.equals(oldTarget, newTarget)) continue;

            var oldDirect = root.squareControl().get(square).get(ray.side());
            var newDirect = new TreeSet<>(oldDirect);
            for (var removed : delta.removedControls()) {
                if (removed.square().equals(square)) newDirect.remove(removed.sourceId());
            }
            for (var added : delta.addedControls()) {
                if (added.square().equals(square) && afterPieces.get(added.sourceId()).color() == ray.side()) {
                    newDirect.add(added.sourceId());
                }
            }

            var reason = square.equals(destination) ? SquareReason.MOVE_DESTINATION : SquareReason.OCCUPIED_TARGET;
            changes.add(new Change(square, reason, ray.side(), ray.direction(), transition(oldIds, newIds),
                    state(oldTarget, ray.side(), oldDirect.stream().sorted().toList(), oldIds, root.pieces()),
                    state(newTarget, ray.side(), newDirect, newIds, afterPieces)));
        }
        return new BatteryEvents(Status.AVAILABLE, changes);
    }

    private static Map<String, Participant> occupants(Map<String, PieceState> pieces) {
        Map<String, Participant> result = new HashMap<>();
        pieces.forEach((id, state) -> result.put(state.square(), new Participant(id, state)));
        return result;
    }

    private static Map<Ray, List<String>> batteries(Collection<XrayControl> controls,
                                                     Map<String, PieceState> pieces, Set<String> squares) {
        Map<Ray, List<String>> result = new HashMap<>();
        for (var control : controls) {
            String square = control.square();
            if (!squares.contains(square)) continue;
            var source = pieces.get(control.sourceId());
            var ray = new Ray(square, source.color(), direction(square, source.square()));
            // On one ray each shorter v3 chain is a prefix of its longest chain when viewed from the target.
            if (result.getOrDefault(ray, List.of()).size() >= control.throughIds().size() + 1) continue;
            var ids = new ArrayList<>(control.throughIds().reversed());
            ids.add(control.sourceId());
            result.put(ray, ids);
        }
        return result;
    }

    private static String direction(String from, String to) {
        int rank = to.charAt(1) - from.charAt(1);
        int file = to.charAt(0) - from.charAt(0);
        String vertical = rank > 0 ? "N" : rank < 0 ? "S" : "";
        String horizontal = file > 0 ? "E" : file < 0 ? "W" : "";
        return vertical + horizontal;
    }

    private static Transition transition(List<String> before, List<String> after) {
        if (before.isEmpty()) return Transition.FORMED;
        if (after.isEmpty()) return Transition.DISSOLVED;
        if (before.equals(after)) return Transition.TARGET_CHANGED;
        return Transition.COMPOSITION_CHANGED;
    }

    private static State state(Participant target, Side side, Collection<String> direct, List<String> battery,
                               Map<String, PieceState> pieces) {
        ControlKind kind = ControlKind.SQUARE_CONTROL;
        if (target != null) {
            if (target.state().color() != side) kind = ControlKind.ATTACK;
            else if (target.state().type() != PieceType.KING) kind = ControlKind.DEFENCE;
        }
        return new State(target, kind, participants(direct, pieces), participants(battery, pieces));
    }

    private static List<Participant> participants(Collection<String> ids, Map<String, PieceState> pieces) {
        return ids.stream().map(id -> new Participant(id, pieces.get(id))).toList();
    }
}
