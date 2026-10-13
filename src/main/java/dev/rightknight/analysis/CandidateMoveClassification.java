package dev.rightknight.analysis;

import java.util.Collections;
import java.util.Set;

public record CandidateMoveClassification(
        Set<CandidateMoveCategory> categories,
        Set<CandidateMoveCategory> unknownCategories) {
    public CandidateMoveClassification {
        categories = Set.copyOf(categories);
        unknownCategories = Set.copyOf(unknownCategories);
        if (!Collections.disjoint(categories, unknownCategories)) {
            throw new IllegalArgumentException("A category cannot be both present and unknown");
        }
    }

    public boolean complete() {
        return unknownCategories.isEmpty();
    }
}
