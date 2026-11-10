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

    static Status inputStatus(String moveUci, AttackSnapshot root, AttackDelta delta) {
        if (root == null || delta == null) return Status.MISSING_DATA;
        if (root.schemaVersion() < 1 || root.schemaVersion() > 3
                || delta.schemaVersion() != root.schemaVersion() || moveUci == null
                || !moveUci.equals(delta.moveUci()) || !UCI.matcher(moveUci).matches()) {
            return Status.INCOMPATIBLE_DATA;
        }
        return Status.AVAILABLE;
    }

    public DirectRelationEvents calculate(String moveUci, AttackSnapshot root, AttackDelta delta) {
        var status = inputStatus(moveUci, root, delta);
        if (status != Status.AVAILABLE) {
            return new DirectRelationEvents(moveUci, status, List.of(), List.of());
        }

        var after = delta.piecesAfter(root);
        var directTargets = new TreeSet<String>();
        for (var piece : delta.pieceChanges()) {
            if (piece.after() == null) directTargets.add(piece.pieceId());
        }

        var before = incoming(root.relations());
        var removed = incoming(delta.removedRelations());
        var added = incoming(delta.addedRelations());
        removed.keySet().forEach(key -> directTargets.add(key.targetId()));
        added.keySet().forEach(key -> directTargets.add(key.targetId()));

        var targets = new TreeSet<>(directTargets);
        // Promotion can expose the new piece even when the same source attacks both squares.
        if (moveUci.length() == 5) targets.add(moveUci.substring(0, 2));
        boolean threatsAvailable = root.calculatedKinds().containsAll(List.of(ATTACK, DEFENCE));
        List<UndefendedPieceEvents.Change> threats = new ArrayList<>();
        List<TargetChange> changes = new ArrayList<>();
        for (String id : targets) {
            Map<RelationKind, SourcesChange> byKind = new EnumMap<>(RelationKind.class);
            boolean captured = !after.containsKey(id);
            for (RelationKind kind : root.calculatedKinds()) {
                var key = new Incoming(id, kind);
                int oldCount = before.getOrDefault(key, List.of()).size();
                var lost = participants(removed.getOrDefault(key, List.of()), root.pieces());
                var gained = participants(added.getOrDefault(key, List.of()), after);
                byKind.put(kind, SourcesChange.compare(oldCount, lost, gained, captured));
            }
            var target = new TargetChange(id, root.pieces().get(id), after.get(id), byKind.get(ATTACK), byKind.get(DEFENCE));
            if (directTargets.contains(id)) changes.add(target);
            if (threatsAvailable) {
                var attackers = before.getOrDefault(new Incoming(id, ATTACK), List.of());
                UndefendedPieceEvents.change(target, attackers, root.pieces(), after, moveUci)
                        .ifPresent(threats::add);
            }
        }
        return new DirectRelationEvents(moveUci, Status.AVAILABLE, root.calculatedKinds(), changes,
                BatteryEventCalculator.calculate(root, delta, after), SquareControlEventCalculator.calculate(root, delta, after),
                new UndefendedPieceEvents(threatsAvailable ? Status.AVAILABLE : Status.MISSING_DATA, threats),
                ValuableAttackEvents.calculate(root, delta, after));
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
}
