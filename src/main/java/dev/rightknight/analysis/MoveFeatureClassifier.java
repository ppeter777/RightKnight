package dev.rightknight.analysis;

import dev.rightknight.model.GameMoveAnalysisCandidateEntity;
import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.AttackTarget;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import java.util.List;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Objects;

import static dev.rightknight.analysis.CandidateMoveCategory.*;

/** Shared classifier for played and candidate moves; no lazy parent access or quality judgement. */
@Component
public class MoveFeatureClassifier {
    public CandidateMoveClassification classify(GameMoveAnalysisCandidateEntity candidate,
                                                Boolean previousMoveCapture) {
        return classify(candidate, previousMoveCapture, null);
    }

    /** movingPieceAfter is the piece on the destination AFTER the move (including promotion). */
    public CandidateMoveClassification classify(GameMoveAnalysisCandidateEntity candidate,
                                                Boolean previousMoveCapture, Piece movingPieceAfter) {
        Objects.requireNonNull(candidate, "candidate");
        return classify(candidate.getCapture(), candidate.getRecapture(), candidate.getGivesCheck(),
                candidate.getPromotion(), candidate.getNewAttackTargets(), previousMoveCapture, movingPieceAfter);
    }

    public CandidateMoveClassification classifyPlayed(GameMoveAnalysisEntity analysis, Piece movingPieceAfter) {
        Objects.requireNonNull(analysis, "analysis");
        return classify(analysis.getPlayedCapture(), analysis.getPlayedRecapture(), analysis.getPlayedGivesCheck(),
                analysis.getPlayedPromotion(), analysis.getPlayedNewAttackTargets(),
                analysis.getPreviousMoveCapture(), movingPieceAfter);
    }

    private CandidateMoveClassification classify(Boolean capture, Boolean recapture, Boolean givesCheck,
                                                 Boolean promotion, List<AttackTarget> targets,
                                                 Boolean previousMoveCapture, Piece movingPieceAfter) {
        if (Boolean.TRUE.equals(recapture)
                && (Boolean.FALSE.equals(capture) || Boolean.FALSE.equals(previousMoveCapture))) {
            throw new IllegalArgumentException("Recapture contradicts capture or previous-move flags");
        }
        var categories = EnumSet.noneOf(CandidateMoveCategory.class);
        var unknown = EnumSet.noneOf(CandidateMoveCategory.class);
        classify(CAPTURE, capture, categories, unknown);
        classify(RECAPTURE, recapture, categories, unknown);
        classify(OTHER_CAPTURE, otherCapture(previousMoveCapture, capture, recapture), categories, unknown);
        classify(GIVES_CHECK, givesCheck, categories, unknown);
        classify(PROMOTION, promotion, categories, unknown);
        Boolean moreValuable = null;
        Boolean pawnAttack = null;
        if (targets != null) {
            if (targets.isEmpty()) {
                moreValuable = false;
                pawnAttack = false;
            } else if (movingPieceAfter != null && movingPieceAfter != Piece.NONE) {
                moreValuable = targets.stream().anyMatch(t ->
                        value(t.piece().getPieceType()) > value(movingPieceAfter.getPieceType()));
                pawnAttack = movingPieceAfter.getPieceType() == PieceType.PAWN
                        && targets.stream().anyMatch(t -> t.piece().getPieceType() != PieceType.PAWN);
            }
        }
        classify(NEW_ATTACK_ON_MORE_VALUABLE_PIECE, moreValuable, categories, unknown);
        classify(NEW_PAWN_ATTACK_ON_PIECE, pawnAttack, categories, unknown);
        return new CandidateMoveClassification(categories, unknown);
    }

    private static int value(PieceType piece) {
        return switch (piece) {
            case PAWN -> 1;
            case KNIGHT, BISHOP -> 3;
            case ROOK -> 5;
            case QUEEN -> 9;
            case KING -> Integer.MAX_VALUE;
            default -> throw new IllegalArgumentException("Unexpected piece type: " + piece);
        };
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
