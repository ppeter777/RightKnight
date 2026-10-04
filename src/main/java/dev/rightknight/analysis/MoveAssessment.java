package dev.rightknight.analysis;

public record MoveAssessment(
        int lossCp,
        LossCategory lossCategory,
        long moveTimeMs,
        double expectedMoveTimeMs,
        double timeUsageRatio,
        TimeUsageCategory timeUsageCategory
) {
}
