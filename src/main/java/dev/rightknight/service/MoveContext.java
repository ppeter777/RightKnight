package dev.rightknight.service;

import java.util.Map;

public record MoveContext(
        Boolean previousMoveCapture,
        Integer recaptureMovesCount,
        Map<String, CandidateMoveFeatures> legalMoveFeatures
) {
    public MoveContext {
        legalMoveFeatures = Map.copyOf(legalMoveFeatures);
    }
}
