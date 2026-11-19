package dev.rightknight.analysis;

import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.model.attack.AttackSnapshot.Relation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.ATTACK;
import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.DEFENCE;

/** Current direct targets for each source with a new attack. The flag marks a newly added edge. */
record DirectAttackIndex(Map<String, Map<String, Boolean>> byAttacker, Map<String, List<String>> defenders) {
    static DirectAttackIndex from(AttackSnapshot root, AttackDelta delta) {
        // Stable piece IDs let an attack persist even when its source or target has moved.
        var byAttacker = new TreeMap<String, Map<String, Boolean>>();
        for (Relation edge : delta.addedRelations()) {
            if (edge.kind() == ATTACK) {
                byAttacker.computeIfAbsent(edge.sourceId(), ignored -> new TreeMap<>()).put(edge.targetId(), true);
            }
        }
        if (byAttacker.isEmpty()) return new DirectAttackIndex(Map.of(), Map.of());

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
        return new DirectAttackIndex(byAttacker, defenders);
    }
}
