package dev.rightknight.analysis;

import java.util.Collections;
import java.util.Set;

/** A snapshot: absent features are false only when they are not in unknownFeatures. */
public record PositionComplexity(
        Set<PositionFeature> features,
        Set<PositionFeature> unknownFeatures) {

    public PositionComplexity {
        features = Set.copyOf(features);
        unknownFeatures = Set.copyOf(unknownFeatures);
        if (!Collections.disjoint(features, unknownFeatures)) {
            throw new IllegalArgumentException("A feature cannot be both present and unknown");
        }
    }

    public boolean complete() {
        return unknownFeatures.isEmpty();
    }

    /** Intentionally neutral until difficulty is calibrated on labelled positions. */
    public double complexityFactor() {
        return 1.0;
    }
}
