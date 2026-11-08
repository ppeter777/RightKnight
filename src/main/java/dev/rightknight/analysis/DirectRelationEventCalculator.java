package dev.rightknight.analysis;

import dev.rightknight.analysis.DirectRelationEvents.*;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;
import dev.rightknight.model.attack.AttackSnapshot.Relation;
import dev.rightknight.model.attack.AttackSnapshot.RelationKind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.ATTACK;
import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.DEFENCE;

public final class DirectRelationEventCalculator {
    private static final Pattern UCI = Pattern.compile("[a-h][1-8][a-h][1-8][qrbn]?");

    private record Incoming(String targetId, RelationKind kind) {}

    public DirectRelationEvents calculate(String moveUci, AttackSnapshot root, AttackDelta delta) {
        if (root == null || delta == null) {
            return new DirectRelationEvents(moveUci, Status.MISSING_DATA, List.of(), List.of());
        }
        if (root.schemaVersion() < 1 || root.schemaVersion() > 3
                || delta.schemaVersion() != root.schemaVersion() || moveUci == null
                || !moveUci.equals(delta.moveUci()) || !UCI.matcher(moveUci).matches()) {
            return new DirectRelationEvents(moveUci, Status.INCOMPATIBLE_DATA, List.of(), List.of());
        }

        Map<String, PieceState> after = new HashMap<>(root.pieces());
        var targets = new TreeSet<String>();
        for (var piece : delta.pieceChanges()) {
            if (piece.after() == null) {
                after.remove(piece.pieceId());
                targets.add(piece.pieceId());
            } else {
                after.put(piece.pieceId(), piece.after());
            }
        }

        var before = incoming(root.relations());
        var removed = incoming(delta.removedRelations());
        var added = incoming(delta.addedRelations());
        removed.keySet().forEach(key -> targets.add(key.targetId()));
        added.keySet().forEach(key -> targets.add(key.targetId()));

        List<TargetChange> changes = new ArrayList<>();
        for (String id : targets) {
            Map<RelationKind, SourcesChange> byKind = new EnumMap<>(RelationKind.class);
            boolean captured = !after.containsKey(id);
            for (RelationKind kind : root.calculatedKinds()) {
                var key = new Incoming(id, kind);
                int oldCount = before.getOrDefault(key, List.of()).size();
                var lost = participants(removed.getOrDefault(key, List.of()), root.pieces());
                var gained = participants(added.getOrDefault(key, List.of()), after);
                int newCount = oldCount - lost.size() + gained.size();
                Transition transition = transition(oldCount, newCount, !lost.isEmpty() || !gained.isEmpty(), captured);
                byKind.put(kind, new SourcesChange(oldCount, newCount, lost, gained, transition));
            }
            changes.add(new TargetChange(id, root.pieces().get(id), after.get(id), byKind.get(ATTACK), byKind.get(DEFENCE)));
        }
        return new DirectRelationEvents(moveUci, Status.AVAILABLE, root.calculatedKinds(), changes,
                BatteryEventCalculator.calculate(root, delta, after));
    }

    private static Map<Incoming, List<String>> incoming(List<Relation> relations) {
        Map<Incoming, List<String>> result = new HashMap<>();
        for (Relation relation : relations) {
            var key = new Incoming(relation.targetId(), relation.kind());
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(relation.sourceId());
        }
        return result;
    }

    private static List<Participant> participants(List<String> ids, Map<String, PieceState> pieces) {
        return ids.stream().sorted().map(id -> new Participant(id, pieces.get(id))).toList();
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
