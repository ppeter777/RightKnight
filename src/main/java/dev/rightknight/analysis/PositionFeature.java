package dev.rightknight.analysis;

/** Facts about the position before the player's move, not difficulty labels. */
public enum PositionFeature {
    IN_CHECK,
    SINGLE_LEGAL_MOVE,
    PREVIOUS_MOVE_WAS_CAPTURE,
    RECAPTURE_AVAILABLE,
    MULTIPLE_RECAPTURES_AVAILABLE
}
