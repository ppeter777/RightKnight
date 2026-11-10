package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.PieceType;
import dev.rightknight.analysis.DirectRelationEvents.Participant;
import dev.rightknight.analysis.DirectRelationEvents.Status;
import dev.rightknight.model.attack.AttackDelta;
import dev.rightknight.model.attack.AttackSnapshot;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;
import dev.rightknight.model.attack.AttackSnapshot.Relation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static dev.rightknight.model.attack.AttackSnapshot.RelationKind.ATTACK;

/** New and removed direct attacks on higher-valued pieces of either side. Mover is shown before the move. */
public record ValuableAttackEvents(Status status, Participant mover, List<Attack> added, List<Attack> removed) {
    public ValuableAttackEvents {
        added = List.copyOf(added);
        removed = List.copyOf(removed);
    }

    public enum Reason { ATTACKER_MOVED, TARGET_MOVED, LINE_OPENED, LINE_BLOCKED, ATTACKER_CAPTURED, TARGET_CAPTURED }
    public record Attack(Participant attacker, Participant target, Reason reason) {}

    static ValuableAttackEvents unavailable(Status status) {
        return new ValuableAttackEvents(status, null, List.of(), List.of());
    }

    static ValuableAttackEvents calculate(String uci, AttackSnapshot root, AttackDelta delta) {
        var status = DirectRelationEventCalculator.inputStatus(uci, root, delta);
        if (status != Status.AVAILABLE) return unavailable(status);
        return calculate(root, delta, delta.piecesAfter(root));
    }

    static ValuableAttackEvents calculate(AttackSnapshot root, AttackDelta delta, Map<String, PieceState> after) {
        if (!root.calculatedKinds().contains(ATTACK)) return unavailable(Status.MISSING_DATA);
        String from = delta.moveUci().substring(0, 2);
        var movingPiece = root.pieces().get(from);
        if (movingPiece == null) return unavailable(Status.INCOMPATIBLE_DATA);
        return new ValuableAttackEvents(Status.AVAILABLE, new Participant(from, movingPiece),
                attacks(delta.addedRelations(), after, root.pieces(), true),
                attacks(delta.removedRelations(), root.pieces(), after, false));
    }

    private static List<Attack> attacks(List<Relation> relations, Map<String, PieceState> position,
                                        Map<String, PieceState> other, boolean added) {
        var attacks = new ArrayList<Attack>();
        for (var relation : relations) {
            if (relation.kind() != ATTACK) continue;
            var source = position.get(relation.sourceId());
            var target = position.get(relation.targetId());
            if (!isMoreValuable(source.type(), target.type())) continue;
            var reason = reason(source, target, other.get(relation.sourceId()), other.get(relation.targetId()), added);
            attacks.add(new Attack(new Participant(relation.sourceId(), source),
                    new Participant(relation.targetId(), target), reason));
        }
        attacks.sort(Comparator.comparing((Attack a) -> a.target().id()).thenComparing(a -> a.attacker().id()));
        return attacks;
    }

    private static Reason reason(PieceState source, PieceState target, PieceState otherSource,
                                 PieceState otherTarget, boolean added) {
        if (otherTarget == null) return Reason.TARGET_CAPTURED;
        if (otherSource == null) return Reason.ATTACKER_CAPTURED;
        if (!source.square().equals(otherSource.square())) return Reason.ATTACKER_MOVED;
        if (!target.square().equals(otherTarget.square())) return Reason.TARGET_MOVED;
        return added ? Reason.LINE_OPENED : Reason.LINE_BLOCKED;
    }

    static boolean isMoreValuable(PieceType attacker, PieceType target) {
        return target != PieceType.KING && value(target) > value(attacker);
    }

    private static int value(PieceType piece) {
        return switch (piece) {
            case PAWN -> 1;
            case KNIGHT, BISHOP -> 3;
            case ROOK -> 5;
            case QUEEN -> 9;
            case KING -> Integer.MAX_VALUE;
            default -> throw new IllegalArgumentException("Unexpected piece type: " + piece);
        };
    }

    Boolean attackByMovingSide() {
        if (status != Status.AVAILABLE) return null;
        return added.stream().anyMatch(a -> a.attacker().state().color() == mover.state().color());
    }
}
