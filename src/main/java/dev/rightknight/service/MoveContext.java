package dev.rightknight.service;

import java.util.Map;
import dev.rightknight.model.attack.AttackSnapshot;

public record MoveContext(
        Boolean previousMoveCapture,
        Integer recaptureMovesCount,
        AttackSnapshot attackSnapshotBefore,
        Map<String, CandidateMoveFeatures> legalMoveFeatures
) {
    public MoveContext {
        legalMoveFeatures = Map.copyOf(legalMoveFeatures);
    }
}
