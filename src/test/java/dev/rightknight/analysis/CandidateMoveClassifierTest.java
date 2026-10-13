package dev.rightknight.analysis;

import dev.rightknight.model.GameMoveAnalysisCandidateEntity;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Set;
import static dev.rightknight.analysis.CandidateMoveCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class CandidateMoveClassifierTest {
    private final CandidateMoveClassifier classifier = new CandidateMoveClassifier();

    @Test
    void otherCaptureMayGiveCheckAndPromote() {
        var result = classify(true, false, true, true, true);
        assertEquals(Set.of(CAPTURE, OTHER_CAPTURE, GIVES_CHECK, PROMOTION), result.categories());
        assertTrue(result.complete());
    }

    @Test
    void recaptureIsAlsoCaptureButNeverOtherCapture() {
        var result = classify(true, true, true, false, true);
        assertEquals(Set.of(CAPTURE, RECAPTURE, GIVES_CHECK), result.categories());
        assertTrue(result.complete());
    }

    @Test
    void captureAfterQuietMoveIsOnlyGenericCapture() {
        assertEquals(Set.of(CAPTURE), classify(true, false, false, false, false).categories());
    }

    @Test
    void quietMoveAfterCaptureIsNotOtherCapture() {
        assertEquals(Set.of(), classify(false, false, false, false, true).categories());
    }

    @Test
    void missingHistoryPreservesKnownCheckAndCapture() {
        var result = classify(true, null, true, false, null);
        assertEquals(Set.of(CAPTURE, GIVES_CHECK), result.categories());
        assertEquals(Set.of(RECAPTURE, OTHER_CAPTURE), result.unknownCategories());
        assertFalse(result.complete());
    }

    @Test
    void missingRecaptureFlagCannotProveOtherCapture() {
        assertEquals(Set.of(RECAPTURE, OTHER_CAPTURE),
                classify(true, null, false, false, true).unknownCategories());
    }

    @Test
    void missingFlagsAreNotFalse() {
        var result = classify(null, null, null, null, null);
        assertTrue(result.categories().isEmpty());
        assertEquals(EnumSet.allOf(CandidateMoveCategory.class), result.unknownCategories());
    }

    @Test
    void knownExclusionsCanRuleOutOtherCaptureWithIncompleteHistory() {
        assertFalse(classify(false, null, false, false, null).unknownCategories().contains(OTHER_CAPTURE));
        assertFalse(classify(null, null, false, false, false).unknownCategories().contains(OTHER_CAPTURE));
        assertFalse(classify(true, true, false, false, null).unknownCategories().contains(OTHER_CAPTURE));
    }

    @Test
    void classificationDoesNotDependOnRankScoreOrPlayedMove() {
        var candidate = candidate(true, false, false, false);
        candidate.setPvRank(5);
        candidate.setEvalCp(-2000);
        var losing = classifier.classify(candidate, true);
        candidate.setPvRank(1);
        candidate.setEvalCp(null);
        candidate.setMateIn(5);
        assertEquals(losing, classifier.classify(candidate, true));
        assertTrue(losing.categories().contains(OTHER_CAPTURE));
    }

    @Test
    void inconsistentRecaptureFlagsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> classify(false, true, false, false, true));
        assertThrows(IllegalArgumentException.class, () -> classify(true, true, false, false, false));
        assertThrows(NullPointerException.class, () -> classifier.classify(null, true));
    }

    @Test
    void resultIsImmutableAndRejectsOverlappingUnknowns() {
        var categories = EnumSet.of(CAPTURE);
        var unknown = EnumSet.of(RECAPTURE);
        var result = new CandidateMoveClassification(categories, unknown);
        categories.clear();
        unknown.clear();
        assertEquals(Set.of(CAPTURE), result.categories());
        assertEquals(Set.of(RECAPTURE), result.unknownCategories());
        assertThrows(UnsupportedOperationException.class, () -> result.categories().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.unknownCategories().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new CandidateMoveClassification(Set.of(CAPTURE), Set.of(CAPTURE)));
    }

    private CandidateMoveClassification classify(Boolean capture, Boolean recapture, Boolean check,
                                                 Boolean promotion, Boolean previousCapture) {
        return classifier.classify(candidate(capture, recapture, check, promotion), previousCapture);
    }

    private GameMoveAnalysisCandidateEntity candidate(Boolean capture, Boolean recapture,
                                                       Boolean check, Boolean promotion) {
        var candidate = new GameMoveAnalysisCandidateEntity();
        candidate.setCapture(capture);
        candidate.setRecapture(recapture);
        candidate.setGivesCheck(check);
        candidate.setPromotion(promotion);
        return candidate;
    }
}
