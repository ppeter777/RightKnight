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
        if (lossCp == null) {
            return LossCategory.UNKNOWN;
        }
        if (lossCp < 20) {
            return LossCategory.NEGLIGIBLE;
        }
        if (lossCp < 50) {
            return LossCategory.SMALL;
        }
        if (lossCp < 100) {
            return LossCategory.SIGNIFICANT;
        }
        return LossCategory.SEVERE;
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
