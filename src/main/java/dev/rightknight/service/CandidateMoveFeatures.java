package dev.rightknight.service;

/** recapture is null when the preceding move is unavailable. */
public record CandidateMoveFeatures(
        boolean capture,
        Boolean recapture,
        boolean givesCheck,
        boolean promotion
) {}
