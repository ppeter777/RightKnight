package dev.rightknight.analysis;

import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.analysis.DiscoveredAttackEvents.BatteryRay;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;
import dev.rightknight.model.attack.AttackSnapshot.Relation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.github.bhlangonijr.chesslib.PieceType.KING;
import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.ATTACK;
import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.DEFENCE;

/** One geometric fork per attacker with two or more current targets and at least one new attack. */
public record ForkEvents(Status status, List<Fork> added) {
    public ForkEvents {
        added = List.copyOf(added);
    }

    public record Fork(Participant attacker, List<Target> targets) {
        public Fork {
            targets = List.copyOf(targets);
        }
    }

    /** All pieces and defensive rays describe the position after the move. */
    public record Target(Participant piece, boolean newAttack, Integer materialValue,
                         List<Participant> defenders, List<BatteryRay> defensiveBatteries) {
        public Target {
            defenders = defenders == null ? null : List.copyOf(defenders);
            defensiveBatteries = defensiveBatteries == null ? null : List.copyOf(defensiveBatteries);
        }
    }

    static ForkEvents calculate(AttackSnapshot root, AttackDelta delta, Map<String, PieceState> pieces,
                                BatteryEventCalculator.Positions batteries) {
        if (!root.calculatedKinds().contains(ATTACK)) return new ForkEvents(Status.MISSING_DATA, List.of());
        // Stable piece IDs let an attack persist even when its source or target has moved.
        var byAttacker = new TreeMap<String, Map<String, Boolean>>();
        for (Relation edge : delta.addedRelations()) {
            if (edge.kind() == ATTACK) {
                byAttacker.computeIfAbsent(edge.sourceId(), ignored -> new TreeMap<>()).put(edge.targetId(), true);
            }
        }
        if (byAttacker.isEmpty()) return new ForkEvents(Status.AVAILABLE, List.of());

        var current = new HashSet<>(root.relations());
        current.removeAll(delta.removedRelations());
        current.addAll(delta.addedRelations());
        Map<String, List<String>> defenders = new HashMap<>();
        for (Relation edge : current) {
            if (edge.kind() == DEFENCE) {
                defenders.computeIfAbsent(edge.targetId(), ignored -> new ArrayList<>()).add(edge.sourceId());
            } else if (byAttacker.containsKey(edge.sourceId())) {
                byAttacker.get(edge.sourceId()).putIfAbsent(edge.targetId(), false);
            }
        }

        boolean hasDefences = root.calculatedKinds().contains(DEFENCE);
        List<Fork> forks = new ArrayList<>();
        for (var group : byAttacker.entrySet()) {
            if (group.getValue().size() < 2) continue;
            var targets = group.getValue().entrySet().stream().map(entry ->
                    target(entry.getKey(), entry.getValue(), pieces, hasDefences, defenders, batteries)).toList();
            String source = group.getKey();
            forks.add(new Fork(new Participant(source, pieces.get(source)), targets));
        }
        return new ForkEvents(Status.AVAILABLE, forks);
    }

    private static Target target(String id, boolean newAttack, Map<String, PieceState> pieces, boolean hasDefences,
                                 Map<String, List<String>> defenders, BatteryEventCalculator.Positions batteries) {
        var piece = pieces.get(id);
        var participant = new Participant(id, piece);
        if (piece.type() == KING) return new Target(participant, newAttack, null, null, null);
        var direct = hasDefences
                ? DirectRelationEventCalculator.participants(defenders.getOrDefault(id, List.of()), pieces) : null;
        var rays = !hasDefences || batteries == null ? null : batteries.afterRays(piece.square(), piece.color(), pieces);
        return new Target(participant, newAttack, ValuableAttackEvents.value(piece.type()), direct, rays);
    }
}
