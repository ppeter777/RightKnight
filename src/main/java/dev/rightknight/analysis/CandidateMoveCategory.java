package dev.rightknight.analysis;

/** Overlapping descriptions of a candidate, not engine quality or human difficulty. */
public enum CandidateMoveCategory {
    CAPTURE,
    RECAPTURE,
    OTHER_CAPTURE,
    GIVES_CHECK,
    PROMOTION,
    NEW_ATTACK_ON_MORE_VALUABLE_PIECE,
    NEW_PAWN_ATTACK_ON_PIECE
}
