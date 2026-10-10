package dev.rightknight.analysis;

import dev.rightknight.model.GameMoveAnalysisEntity;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Objects;

import static dev.rightknight.analysis.PositionFeature.*;

/** Classifies existing pre-move metrics without engine work or database access. */
@Component
public class PositionComplexityCalculator {

    public PositionComplexity calculate(GameMoveAnalysisEntity analysis) {
        Objects.requireNonNull(analysis, "analysis");
        Integer legalMoves = analysis.getLegalMovesCount();
        Integer recaptures = analysis.getRecaptureMovesCount();
        Boolean previousCapture = analysis.getPreviousMoveCapture();
        validateCounts(legalMoves, recaptures, previousCapture);

        EnumSet<PositionFeature> features = EnumSet.noneOf(PositionFeature.class);
        EnumSet<PositionFeature> unknown = EnumSet.noneOf(PositionFeature.class);
        classify(IN_CHECK, analysis.getInCheck(), features, unknown);
        classify(SINGLE_LEGAL_MOVE, legalMoves == null ? null : legalMoves == 1, features, unknown);
        classify(PREVIOUS_MOVE_WAS_CAPTURE, previousCapture, features, unknown);
        classify(RECAPTURE_AVAILABLE, recaptures == null ? null : recaptures > 0, features, unknown);
        classify(MULTIPLE_RECAPTURES_AVAILABLE, recaptures == null ? null : recaptures > 1, features, unknown);
        return new PositionComplexity(features, unknown);
    }

    private static void classify(PositionFeature feature, Boolean present,
                                 EnumSet<PositionFeature> features, EnumSet<PositionFeature> unknown) {
        if (present == null) {
            unknown.add(feature);
        } else if (present) {
            features.add(feature);
        }
    }

    private static void validateCounts(Integer legalMoves, Integer recaptures, Boolean previousCapture) {
        if (legalMoves != null && legalMoves < 0 || recaptures != null && recaptures < 0) {
            throw new IllegalArgumentException("Move counts must not be negative");
        }
        if (legalMoves != null && recaptures != null && recaptures > legalMoves) {
            throw new IllegalArgumentException("Recaptures cannot exceed legal moves");
        }
        if (Boolean.FALSE.equals(previousCapture) && recaptures != null && recaptures > 0) {
            throw new IllegalArgumentException("Recaptures require a preceding capture");
        }
    }
}
