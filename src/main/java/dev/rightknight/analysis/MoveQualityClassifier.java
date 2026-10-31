package dev.rightknight.analysis;

import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.GameMoveEntity;
import org.springframework.stereotype.Component;

@Component
public class MoveQualityClassifier {

    public MoveAssessment classify(
            GameMoveEntity move,
            GameMoveAnalysisEntity analysis,
            Long expectedMoveTimeMs) {

        Integer lossCp = analysis.getLossCp();

        long moveTimeMs = move.getMoveTimeMs() != null
                ? move.getMoveTimeMs()
                : 0;

        double timeUsageRatio =
                calculateMoveTimeRatio(move, expectedMoveTimeMs);

        return new MoveAssessment(
                lossCp,
                classifyLoss(lossCp),
                moveTimeMs,
                expectedMoveTimeMs,
                timeUsageRatio,
                classifyTime(timeUsageRatio)
        );
    }

    private LossCategory classifyLoss(Integer lossCp) {
        return LossPolicy.v1().classify(lossCp == null ? null : lossCp.longValue());
    }

    private double calculateMoveTimeRatio(GameMoveEntity move, Long expectedMoveTime) {
        if (move.getMoveTimeMs() == null ||
                expectedMoveTime == null ||
                expectedMoveTime <= 0) {
            return 0.0;
        }

        return (double) move.getMoveTimeMs() / expectedMoveTime;
    }

    private TimeUsageCategory classifyTime(double ratio) {
        if (ratio < 0.5) {
            return TimeUsageCategory.FAST;
        }
        if (ratio > 1.5) {
            return TimeUsageCategory.LONG;
        }
        return TimeUsageCategory.NORMAL;
    }
}
