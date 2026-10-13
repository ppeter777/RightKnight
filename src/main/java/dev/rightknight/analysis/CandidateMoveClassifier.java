package dev.rightknight.analysis;

import dev.rightknight.model.GameMoveAnalysisCandidateEntity;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Objects;

import static dev.rightknight.analysis.CandidateMoveCategory.*;

/** Uses saved candidate flags; does not access the lazy parent or judge move quality. */
@Component
public class CandidateMoveClassifier {
    public CandidateMoveClassification classify(GameMoveAnalysisCandidateEntity candidate,
                                                Boolean previousMoveCapture) {
        Objects.requireNonNull(candidate, "candidate");
        Boolean capture = candidate.getCapture();
        Boolean recapture = candidate.getRecapture();
        if (Boolean.TRUE.equals(recapture)
                && (Boolean.FALSE.equals(capture) || Boolean.FALSE.equals(previousMoveCapture))) {
            throw new IllegalArgumentException("Recapture contradicts capture or previous-move flags");
        }
        var categories = EnumSet.noneOf(CandidateMoveCategory.class);
        var unknown = EnumSet.noneOf(CandidateMoveCategory.class);
        classify(CAPTURE, capture, categories, unknown);
        classify(RECAPTURE, recapture, categories, unknown);
        classify(OTHER_CAPTURE, otherCapture(previousMoveCapture, capture, recapture), categories, unknown);
        classify(GIVES_CHECK, candidate.getGivesCheck(), categories, unknown);
        classify(PROMOTION, candidate.getPromotion(), categories, unknown);
        return new CandidateMoveClassification(categories, unknown);
    }

    private static Boolean otherCapture(Boolean previousCapture, Boolean capture, Boolean recapture) {
        if (Boolean.FALSE.equals(previousCapture) || Boolean.FALSE.equals(capture)
                || Boolean.TRUE.equals(recapture)) {
            return false;
        }
        if (previousCapture == null || capture == null || recapture == null) {
            return null;
        }
        return true;
    }

    private static void classify(CandidateMoveCategory category, Boolean present,
                                 EnumSet<CandidateMoveCategory> categories,
                                 EnumSet<CandidateMoveCategory> unknown) {
        if (present == null) {
            unknown.add(category);
        } else if (present) {
            categories.add(category);
        }
    }
}
