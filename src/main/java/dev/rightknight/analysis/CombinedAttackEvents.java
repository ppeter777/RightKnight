package dev.rightknight.analysis;

import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** A new attack by the moving piece and at least one newly opened attack by a friendly slider. */
public record CombinedAttackEvents(Status status, List<Combination> added) {
    public CombinedAttackEvents {
        added = List.copyOf(added);
    }

    public record DirectTarget(Participant target, boolean newAttack) {}
    public record OpenedAttack(Participant attacker, Participant target) {}
    public record Combination(Participant mover, List<DirectTarget> directTargets,
                              List<OpenedAttack> discoveredAttacks, int distinctTargetCount) {
        public Combination {
            directTargets = List.copyOf(directTargets);
            discoveredAttacks = List.copyOf(discoveredAttacks);
        }
    }

    static CombinedAttackEvents calculate(String moverId, Map<String, PieceState> after,
                                          DirectAttackIndex direct, DiscoveredAttackEvents discovered) {
        var targets = direct.byAttacker().get(moverId);
        var empty = new CombinedAttackEvents(discovered.status(), List.of());
        if (targets == null || discovered.added().isEmpty()) return empty;

        var mover = new Participant(moverId, after.get(moverId));
        var opened = discovered.added().stream()
                .filter(attack -> attack.attacker().state().color() == mover.state().color())
                .map(attack -> new OpenedAttack(attack.attacker(), attack.target()))
                .toList();
        if (opened.isEmpty()) return empty;

        var directTargets = targets.entrySet().stream()
                .map(e -> new DirectTarget(new Participant(e.getKey(), after.get(e.getKey())), e.getValue()))
                .toList();
        var distinctTargets = new HashSet<>(targets.keySet());
        opened.forEach(attack -> distinctTargets.add(attack.target().id()));
        return new CombinedAttackEvents(Status.AVAILABLE,
                List.of(new Combination(mover, directTargets, opened, distinctTargets.size())));
    }
}
