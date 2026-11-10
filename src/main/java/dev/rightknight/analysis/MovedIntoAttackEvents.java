package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.PieceType;
import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.analysis.DirectRelationEvents.TargetChange;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Geometric exposure of a relocated piece on its destination, including after captures and promotion. */
public record MovedIntoAttackEvents(Status status, List<Change> changes) {
    public MovedIntoAttackEvents {
        changes = List.copyOf(changes);
    }

    public enum Kind { UNDEFENDED, OUTNUMBERED }

    /** Participants refer to the position after the move. The target includes its previous square and counts. */
    public record Change(TargetChange target, Kind kind, List<Participant> attackers, List<Participant> defenders) {
        public Change {
            attackers = List.copyOf(attackers);
            defenders = List.copyOf(defenders);
        }
    }

    static Optional<Change> change(TargetChange target, List<String> oldAttackers, List<String> oldDefenders,
                                   Map<String, PieceState> after) {
        if (target.after() == null || target.after().type() == PieceType.KING
                || target.before().square().equals(target.after().square())) return Optional.empty();
        var attacks = target.attacks();
        var defences = target.defences();
        if (attacks.afterCount() <= defences.afterCount()) return Optional.empty();
        var kind = defences.afterCount() == 0 ? Kind.UNDEFENDED : Kind.OUTNUMBERED;
        return Optional.of(new Change(target, kind, attacks.participantsAfter(oldAttackers, after),
                defences.participantsAfter(oldDefenders, after)));
    }
}
