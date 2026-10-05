package dev.rightknight.engine;

/** EXACT means an unbounded search score, not a game-theoretic exact value. */
public enum ScoreBound {
    EXACT, LOWER, UPPER
}
