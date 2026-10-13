package dev.rightknight.analysis;

import dev.rightknight.model.GameMoveAnalysisEntity;
import org.junit.jupiter.api.Test;
import static dev.rightknight.analysis.PositionFeature.*;
import static org.junit.jupiter.api.Assertions.*;

class OtherCaptureAvailableTest {
    private final PositionComplexityCalculator calculator = new PositionComplexityCalculator();

    @Test
    void otherCaptureCanCoexistWithRecaptures() {
        var result = calculate(true, 3, 2);
        assertTrue(result.features().contains(OTHER_CAPTURE_AVAILABLE));
        assertTrue(result.features().contains(RECAPTURE_AVAILABLE));
        assertEquals(1.0, result.complexityFactor());
    }

    @Test
    void otherCaptureDoesNotRequireAnyRecapture() {
        assertTrue(calculate(true, 1, 0).features().contains(OTHER_CAPTURE_AVAILABLE));
    }

    @Test
    void equalCaptureAndRecaptureCountsMeanNoOtherCapture() {
        assertFalse(calculate(true, 4, 4).features().contains(OTHER_CAPTURE_AVAILABLE));
    }

    @Test
    void quietPreviousMoveDoesNotQualifyEvenWhenCapturesExist() {
        var result = calculate(false, 4, 0);
        assertFalse(result.features().contains(OTHER_CAPTURE_AVAILABLE));
        assertFalse(result.unknownFeatures().contains(OTHER_CAPTURE_AVAILABLE));
    }

    @Test
    void incompleteCountsOrHistoryStayUnknown() {
        assertTrue(calculate(true, null, 0).unknownFeatures().contains(OTHER_CAPTURE_AVAILABLE));
        assertTrue(calculate(true, 3, null).unknownFeatures().contains(OTHER_CAPTURE_AVAILABLE));
        assertTrue(calculate(null, 3, null).unknownFeatures().contains(OTHER_CAPTURE_AVAILABLE));
    }

    @Test
    void noCapturesOrQuietPredecessorCanRuleOutOtherCapture() {
        assertFalse(calculate(null, 0, null).unknownFeatures().contains(OTHER_CAPTURE_AVAILABLE));
        assertFalse(calculate(false, null, null).unknownFeatures().contains(OTHER_CAPTURE_AVAILABLE));
    }

    @Test
    void inconsistentCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> calculate(true, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> calculate(true, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> calculate(true, 21, 0));
    }

    private PositionComplexity calculate(Boolean previousCapture, Integer captures, Integer recaptures) {
        var analysis = new GameMoveAnalysisEntity();
        analysis.setInCheck(false);
        analysis.setLegalMovesCount(20);
        analysis.setPreviousMoveCapture(previousCapture);
        analysis.setCaptureMovesCount(captures);
        analysis.setRecaptureMovesCount(recaptures);
        return calculator.calculate(analysis);
    }
}
