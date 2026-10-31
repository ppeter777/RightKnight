package dev.rightknight.analysis;

/** Versioned provisional loss bands. */
public record LossPolicy(String version, int inaccuracyFromCp, int mistakeFromCp, int blunderFromCp, int criticalFromCp) {
    public LossPolicy {
        if (version == null || version.isBlank() || inaccuracyFromCp <= 0 || mistakeFromCp <= inaccuracyFromCp
                || blunderFromCp <= mistakeFromCp || criticalFromCp <= blunderFromCp) {
            throw new IllegalArgumentException("Invalid loss policy");
        }
    }

    public static LossPolicy v2() { return new LossPolicy("cp-loss-v2", 20, 50, 100, 200); }

    public LossCategory classify(Long lossCp) {
        if (lossCp == null) return LossCategory.UNKNOWN;
        if (lossCp < 0) throw new IllegalArgumentException("Loss cannot be negative");
        if (lossCp < inaccuracyFromCp) return LossCategory.ACCURATE;
        if (lossCp < mistakeFromCp) return LossCategory.INACCURACY;
        if (lossCp < blunderFromCp) return LossCategory.MISTAKE;
        if (lossCp < criticalFromCp) return LossCategory.BLUNDER;
        return LossCategory.CRITICAL_BLUNDER;
    }

    public Boolean acceptable(Long lossCp) {
        if (lossCp == null) return null;
        var category = classify(lossCp);
        return category == LossCategory.ACCURATE || category == LossCategory.INACCURACY;
    }
}
