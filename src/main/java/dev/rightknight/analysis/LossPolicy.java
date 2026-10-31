package dev.rightknight.analysis;

/** Provisional loss bands, not claims that a zero-loss move is an actual inaccuracy. */
public record LossPolicy(String version, int mistakeFromCp, int blunderFromCp, int criticalFromCp) {
    public LossPolicy {
        if (version == null || version.isBlank() || mistakeFromCp <= 0
                || blunderFromCp <= mistakeFromCp || criticalFromCp <= blunderFromCp) {
            throw new IllegalArgumentException("Invalid loss policy");
        }
    }

    public static LossPolicy v1() { return new LossPolicy("cp-loss-v1", 50, 100, 200); }

    public LossCategory classify(Long lossCp) {
        if (lossCp == null) return LossCategory.UNKNOWN;
        if (lossCp < 0) throw new IllegalArgumentException("Loss cannot be negative");
        if (lossCp < mistakeFromCp) return LossCategory.INACCURACY;
        if (lossCp < blunderFromCp) return LossCategory.MISTAKE;
        if (lossCp < criticalFromCp) return LossCategory.BLUNDER;
        return LossCategory.CRITICAL_BLUNDER;
    }

    public Boolean acceptable(Long lossCp) {
        return lossCp == null ? null : classify(lossCp) == LossCategory.INACCURACY;
    }
}
