package dev.rightknight.service;

import dev.rightknight.analysis.ExpectedMoveTimeCalculator;
import dev.rightknight.analysis.LossCategory;
import dev.rightknight.analysis.MoveQualityClassifier;
import dev.rightknight.analysis.TimeUsageCategory;
import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.GameMoveEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MoveAssessmentTest {

    private GameMoveEntity move;
    private GameMoveAnalysisEntity gameMoveAnalysisEntity;
    private MoveQualityClassifier moveQualityClassifier;
    private ExpectedMoveTimeCalculator moveTimeCalculator;

    @BeforeEach
    void setUp() {
        moveQualityClassifier = new MoveQualityClassifier();
        gameMoveAnalysisEntity = new GameMoveAnalysisEntity();
        moveTimeCalculator = new ExpectedMoveTimeCalculator();
        move = new GameMoveEntity();
    }

    @Test
    void blunderLossFastMove() {

        gameMoveAnalysisEntity.setLossCp(100);
        long initialTimeMs = 180000L;
        long incrementMs = 2000L;
        move.setMoveTimeMs(3000L);
        Long expectedMoveTime = moveTimeCalculator.calculateBaseExpectedTimeMs(initialTimeMs, incrementMs, 40);
        var moveQuality = moveQualityClassifier.classify(move, gameMoveAnalysisEntity, expectedMoveTime);

        assertEquals(LossCategory.BLUNDER, moveQuality.lossCategory());
        assertEquals(TimeUsageCategory.FAST, moveQuality.timeUsageCategory());
    }

    @Test
    void mistakeLossNormalMove() {

        gameMoveAnalysisEntity.setLossCp(60);
        long initialTimeMs = 180000L;
        long incrementMs = 2000L;
        move.setMoveTimeMs(8000L);
        Long expectedMoveTime = moveTimeCalculator.calculateBaseExpectedTimeMs(initialTimeMs, incrementMs, 40);
        var moveQuality = moveQualityClassifier.classify(move, gameMoveAnalysisEntity, expectedMoveTime);

        assertEquals(LossCategory.MISTAKE, moveQuality.lossCategory());
        assertEquals(TimeUsageCategory.NORMAL, moveQuality.timeUsageCategory());

    }

    @Test
    void inaccuracyLossLongMove() {

        gameMoveAnalysisEntity.setLossCp(30);
        long initialTimeMs = 180000L;
        long incrementMs = 2000L;
        move.setMoveTimeMs(12000L);
        Long expectedMoveTime = moveTimeCalculator.calculateBaseExpectedTimeMs(initialTimeMs, incrementMs, 40);
        var moveQuality = moveQualityClassifier.classify(move, gameMoveAnalysisEntity, expectedMoveTime);

        assertEquals(LossCategory.INACCURACY, moveQuality.lossCategory());
        assertEquals(TimeUsageCategory.LONG, moveQuality.timeUsageCategory());

    }

    @Test
    void smallLossIsAccurate() {
        gameMoveAnalysisEntity.setLossCp(10);
        long initialTimeMs = 180000L;
        long incrementMs = 2000L;
        move.setClockBeforeMs(2000L);
        move.setMoveTimeMs(3000L);
        Long expectedMoveTime = moveTimeCalculator.calculateBaseExpectedTimeMs(initialTimeMs, incrementMs, 40);
        var moveQuality = moveQualityClassifier.classify(move, gameMoveAnalysisEntity, expectedMoveTime);

        assertEquals(LossCategory.ACCURATE, moveQuality.lossCategory());
        assertEquals(TimeUsageCategory.FAST, moveQuality.timeUsageCategory());

    }

    @Test
    void missingCpLossIsUnknown() {
        var assessment = moveQualityClassifier.classify(move, gameMoveAnalysisEntity, 1000L);
        assertEquals(LossCategory.UNKNOWN, assessment.lossCategory());
        org.junit.jupiter.api.Assertions.assertNull(assessment.lossCp());
    }

}
