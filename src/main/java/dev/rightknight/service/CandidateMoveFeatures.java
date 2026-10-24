package dev.rightknight.service;

import dev.rightknight.model.AttackTarget;
import java.util.List;
import dev.rightknight.model.attack.AttackDelta;

/** Shared by every legal move, including the played move. recapture is null when the preceding move is unavailable. */
public record CandidateMoveFeatures(
        boolean capture,
        Boolean recapture,
        boolean givesCheck,
        boolean promotion,
        List<AttackTarget> newAttackTargets,
        AttackDelta attackDelta
) {
    public CandidateMoveFeatures {
        newAttackTargets = List.copyOf(newAttackTargets);
    }
}
