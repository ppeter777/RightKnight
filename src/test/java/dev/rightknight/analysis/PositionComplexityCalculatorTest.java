package dev.rightknight.analysis;

import dev.rightknight.model.GameMoveAnalysisEntity;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static dev.rightknight.analysis.PositionFeature.*;
import static org.junit.jupiter.api.Assertions.*;

class PositionComplexityCalculatorTest {
    private final PositionComplexityCalculator calculator = new PositionComplexityCalculator();

    @Test
    void ordinaryPositionHasKnownEmptyFeatureSet() {
        var result = calculate(false, 20, false, 0);
        assertEquals(Set.of(), result.features());
        assertTrue(result.complete());
        assertEquals(1.0, result.complexityFactor());
    }

    @Test
    void featuresOverlapAndMultipleImpliesAvailable() {
        var result = calculate(true, 4, true, 2);
        assertEquals(Set.of(IN_CHECK, PREVIOUS_MOVE_WAS_CAPTURE, RECAPTURE_AVAILABLE,
                MULTIPLE_RECAPTURES_AVAILABLE), result.features());
        assertTrue(result.complete());
        assertEquals(1.0, result.complexityFactor());
    }

    @Test
    void singleLegalRecaptureIsStillNeutral() {
        var result = calculate(true, 1, true, 1);
        assertEquals(Set.of(IN_CHECK, SINGLE_LEGAL_MOVE, PREVIOUS_MOVE_WAS_CAPTURE,
                RECAPTURE_AVAILABLE), result.features());
        assertEquals(1.0, result.complexityFactor());
    }

    @Test
    void aCaptureDoesNotGuaranteeARecapture() {
        assertEquals(Set.of(PREVIOUS_MOVE_WAS_CAPTURE), calculate(false, 12, true, 0).features());
    }

    @Test
    void unknownHistoricalMetricsAreNotFalse() {
        var result = calculate(null, null, null, null);
        assertTrue(result.features().isEmpty());
        assertEquals(EnumSet.allOf(PositionFeature.class), result.unknownFeatures());
        assertFalse(result.complete());
        assertEquals(1.0, result.complexityFactor());
    }

    @Test
    void missingHistoryDoesNotHideKnownPositionFacts() {
        var result = calculate(true, 1, null, null);
        assertEquals(Set.of(IN_CHECK, SINGLE_LEGAL_MOVE), result.features());
        assertEquals(Set.of(PREVIOUS_MOVE_WAS_CAPTURE, RECAPTURE_AVAILABLE,
                MULTIPLE_RECAPTURES_AVAILABLE), result.unknownFeatures());
    }

    @Test
    void missingRecaptureCountIsNotInferredFromPreviousCapture() {
        var result = calculate(false, 10, true, null);
        assertEquals(Set.of(PREVIOUS_MOVE_WAS_CAPTURE), result.features());
        assertEquals(Set.of(RECAPTURE_AVAILABLE, MULTIPLE_RECAPTURES_AVAILABLE), result.unknownFeatures());
    }

    @Test
    void terminalPositionsDoNotHaveSingleLegalMove() {
        assertEquals(Set.of(IN_CHECK), calculate(true, 0, false, 0).features());
        assertTrue(calculate(false, 0, false, 0).features().isEmpty());
    }

    @Test
    void resultIsAnImmutableSnapshot() {
        var present = EnumSet.of(IN_CHECK);
        var missing = EnumSet.of(SINGLE_LEGAL_MOVE);
        var result = new PositionComplexity(present, missing);
        present.clear();
        missing.clear();
        assertEquals(Set.of(IN_CHECK), result.features());
        assertEquals(Set.of(SINGLE_LEGAL_MOVE), result.unknownFeatures());
        assertThrows(UnsupportedOperationException.class, () -> result.features().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.unknownFeatures().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new PositionComplexity(Set.of(IN_CHECK), Set.of(IN_CHECK)));
    }

    @Test
    void invalidMetricsAreRejected() {
        assertThrows(NullPointerException.class, () -> calculator.calculate(null));
        assertThrows(IllegalArgumentException.class, () -> calculate(false, -1, false, 0));
        assertThrows(IllegalArgumentException.class, () -> calculate(false, 20, true, -1));
        assertThrows(IllegalArgumentException.class, () -> calculate(false, 1, true, 2));
        assertThrows(IllegalArgumentException.class, () -> calculate(false, 20, false, 1));
    }

    private PositionComplexity calculate(Boolean inCheck, Integer legalMoves,
                                          Boolean previousCapture, Integer recaptures) {
        var analysis = new GameMoveAnalysisEntity();
        analysis.setInCheck(inCheck);
        analysis.setLegalMovesCount(legalMoves);
        analysis.setPreviousMoveCapture(previousCapture);
        analysis.setRecaptureMovesCount(recaptures);
        return calculator.calculate(analysis);
    }
}
