package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.PieceType;
import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.analysis.DirectRelationEvents.TargetChange;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/** Direct geometric threats against surviving minor and major pieces. */
public record UndefendedPieceEvents(Status status, List<Change> changes) {
    public UndefendedPieceEvents {
        changes = List.copyOf(changes);
    }

    public enum Transition { EXPOSED, ANSWERED, WITHDRAWN }
    public enum Reason { FIRST_ATTACK, LAST_DEFENDER_LOST, PROMOTION, DEFENDER_GAINED,
                         TARGET_MOVED, ATTACKER_CAPTURED, LINE_BLOCKED, ATTACKER_MOVED }

    /** Attackers are all direct attackers in the threatened position (after exposure, before resolution). */
    public record Change(TargetChange target, Transition transition, List<Participant> attackers, List<Reason> reasons) {
        public Change {
            attackers = List.copyOf(attackers);
            reasons = List.copyOf(reasons);
        }
    }

    static Optional<Change> change(TargetChange target, List<String> beforeAttackers,
                                   Map<String, PieceState> before, Map<String, PieceState> after, String uci) {
        if (target.after() == null) return Optional.empty();
        var attacks = target.attacks();
        var defences = target.defences();
        boolean wasExposed = exposed(target.before(), attacks.beforeCount(), defences.beforeCount());
        boolean nowExposed = exposed(target.after(), attacks.afterCount(), defences.afterCount());
        if (wasExposed == nowExposed) return Optional.empty();

        var reasons = EnumSet.noneOf(Reason.class);
        var attackerIds = new TreeSet<>(beforeAttackers);
        Transition transition;
        if (nowExposed) {
            transition = Transition.EXPOSED;
            if (attacks.beforeCount() == 0) reasons.add(Reason.FIRST_ATTACK);
            if (defences.beforeCount() > 0) reasons.add(Reason.LAST_DEFENDER_LOST);
            if (target.before().type() == PieceType.PAWN) reasons.add(Reason.PROMOTION);
            attacks.removed().forEach(p -> attackerIds.remove(p.id()));
            attacks.added().forEach(p -> attackerIds.add(p.id()));
        } else {
            var mover = before.get(uci.substring(0, 2));
            transition = mover.color() == target.before().color() ? Transition.ANSWERED : Transition.WITHDRAWN;
            if (defences.afterCount() > 0) reasons.add(Reason.DEFENDER_GAINED);
            boolean moved = !target.before().square().equals(target.after().square());
            if (moved) reasons.add(Reason.TARGET_MOVED);
            for (var attacker : attacks.removed()) {
                PieceState survivor = after.get(attacker.id());
                if (survivor == null) reasons.add(Reason.ATTACKER_CAPTURED);
                else if (!attacker.state().square().equals(survivor.square())) reasons.add(Reason.ATTACKER_MOVED);
                else if (!moved) reasons.add(Reason.LINE_BLOCKED);
            }
        }
        var pieces = nowExposed ? after : before;
        var attackers = attackerIds.stream().map(id -> new Participant(id, pieces.get(id))).toList();
        return Optional.of(new Change(target, transition, attackers, List.copyOf(reasons)));
    }

    private static boolean exposed(PieceState target, int attackers, int defenders) {
        return target.type() != PieceType.KING && target.type() != PieceType.PAWN && attackers > 0 && defenders == 0;
    }
}
