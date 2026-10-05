package dev.rightknight.analysis;

public record MoveAssessment(
        Integer lossCp,
        LossCategory lossCategory,
        long moveTimeMs,
        double expectedMoveTimeMs,
        double timeUsageRatio,
        TimeUsageCategory timeUsageCategory
) {
}
