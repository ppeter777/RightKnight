package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.Piece;
import dev.rightknight.model.GameMoveAnalysisCandidateEntity;
import org.springframework.stereotype.Component;

/** Compatibility adapter; all rules live in MoveFeatureClassifier. */
@Component
public class CandidateMoveClassifier {
    private final MoveFeatureClassifier classifier = new MoveFeatureClassifier();

    public CandidateMoveClassification classify(GameMoveAnalysisCandidateEntity candidate,
                                                Boolean previousMoveCapture) {
        return classifier.classify(candidate, previousMoveCapture);
    }

    public CandidateMoveClassification classify(GameMoveAnalysisCandidateEntity candidate,
                                                Boolean previousMoveCapture, Piece movingPieceAfter) {
        return classifier.classify(candidate, previousMoveCapture, movingPieceAfter);
    }
}
