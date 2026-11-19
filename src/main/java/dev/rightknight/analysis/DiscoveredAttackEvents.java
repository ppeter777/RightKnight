package dev.rightknight.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.SourcesChange;
import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.analysis.DirectRelationEvents.TargetChange;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.bhlangonijr.chesslib.PieceType.*;

/** Newly opened direct attacks by stationary sliders on stationary targets of either side. */
public record DiscoveredAttackEvents(Status status, List<Attack> added) {
    public DiscoveredAttackEvents {
        added = List.copyOf(added);
    }

    /** Mover and blockers are shown before the move. Blockers follow the ray from attacker to target. */
    public record Attack(Participant mover, Participant attacker, Participant target, List<Participant> blockers,
                         boolean discoveredCheck, Sources attacks, Sources defences, DefensiveBatteries defensiveBatteries) {
        public Attack {
            blockers = List.copyOf(blockers);
        }
    }

    /** Rays run from the direct defender to the rear slider(s). */
    public record BatteryRay(String direction, List<Participant> pieces) {
        public BatteryRay {
            pieces = List.copyOf(pieces);
        }
    }

    public record DefensiveBatteries(List<BatteryRay> before, List<BatteryRay> after) {
        public DefensiveBatteries {
            before = List.copyOf(before);
            after = List.copyOf(after);
        }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public int beforeSupportCount() {
            return supportCount(before);
        }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public int afterSupportCount() {
            return supportCount(after);
        }

        private static int supportCount(List<BatteryRay> rays) {
            return rays.stream().mapToInt(ray -> ray.pieces().size() - 1).sum();
        }
    }

    /** Each list contains the full set of participants in the corresponding position. */
    public record Sources(int beforeCount, List<Participant> before, int afterCount, List<Participant> after) {
        public Sources {
            before = List.copyOf(before);
            after = List.copyOf(after);
        }
    }

    static List<Attack> opened(TargetChange target, Participant mover, List<String> oldAttackers,
                               List<String> oldDefenders, Map<String, PieceState> before, Map<String, PieceState> after,
                               BatteryEventCalculator.Positions batteries) {
        if (target.attacks() == null || target.attacks().added().isEmpty() || !target.before().equals(target.after())) {
            return List.of();
        }
        var sliders = target.attacks().added().stream().filter(attacker -> {
            var type = attacker.state().type();
            return (type == BISHOP || type == ROOK || type == QUEEN) && attacker.state().equals(before.get(attacker.id()));
        }).toList();
        if (sliders.isEmpty()) return List.of();

        var attacks = sources(oldAttackers, target.attacks(), before, after);
        var defences = target.defences() == null ? null : sources(oldDefenders, target.defences(), before, after);
        var victim = new Participant(target.id(), target.after());
        boolean check = victim.state().type() == KING;
        var defensiveBatteries = batteries == null || check || defences == null
                ? null : batteries.defences(victim.state(), before, after);
        return sliders.stream().map(attacker -> new Attack(mover, attacker, victim,
                blockers(attacker.state().square(), victim.state().square(), before),
                check, attacks, defences, defensiveBatteries)).toList();
    }

    private static Sources sources(List<String> oldIds, SourcesChange change,
                                   Map<String, PieceState> before, Map<String, PieceState> after) {
        return new Sources(change.beforeCount(), DirectRelationEventCalculator.participants(oldIds, before),
                change.afterCount(), change.participantsAfter(oldIds, after));
    }

    private static List<Participant> blockers(String from, String to, Map<String, PieceState> before) {
        int dx = to.charAt(0) - from.charAt(0);
        int dy = to.charAt(1) - from.charAt(1);
        var blockers = new ArrayList<Participant>();
        // A newly added direct edge guarantees that the ray is clear after the move.
        for (int step = 1; step < Math.max(Math.abs(dx), Math.abs(dy)); step++) {
            char file = (char) (from.charAt(0) + step * Integer.signum(dx));
            char rank = (char) (from.charAt(1) + step * Integer.signum(dy));
            String square = "" + file + rank;
            var blocker = before.get(square); // Root IDs are the pre-move squares.
            if (blocker != null) blockers.add(new Participant(square, blocker));
        }
        return blockers;
    }
}
