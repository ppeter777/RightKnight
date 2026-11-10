package dev.rightknight.analysis;

import dev.rightknight.model.GameMoveAnalysisCandidateEntity;
import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.AttackTarget;
import dev.rightknight.model.attack.AttackSnapshot;
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
        return classify(candidate, previousMoveCapture, movingPieceAfter, null);
    }

    /** Supply the shared root explicitly to include attacks uncovered by other pieces. */
    public CandidateMoveClassification classify(GameMoveAnalysisCandidateEntity candidate, Boolean previousMoveCapture,
                                                Piece movingPieceAfter, AttackSnapshot root) {
        Objects.requireNonNull(candidate, "candidate");
        return classify(candidate.getCapture(), candidate.getRecapture(), candidate.getGivesCheck(),
                candidate.getPromotion(), candidate.getNewAttackTargets(), previousMoveCapture, movingPieceAfter,
                root == null ? null : ValuableAttackEvents.calculate(candidate.getMoveUci(), root, candidate.getAttackDelta()));
    }

    public CandidateMoveClassification classifyPlayed(GameMoveAnalysisEntity analysis, Piece movingPieceAfter) {
        Objects.requireNonNull(analysis, "analysis");
        var root = analysis.getAttackSnapshotBefore();
        var delta = analysis.getPlayedAttackDelta();
        var events = root == null ? null : ValuableAttackEvents.calculate(delta == null ? null : delta.moveUci(), root, delta);
        return classify(analysis.getPlayedCapture(), analysis.getPlayedRecapture(), analysis.getPlayedGivesCheck(),
                analysis.getPlayedPromotion(), analysis.getPlayedNewAttackTargets(),
                analysis.getPreviousMoveCapture(), movingPieceAfter, events);
    }

    private CandidateMoveClassification classify(Boolean capture, Boolean recapture, Boolean givesCheck,
                                                 Boolean promotion, List<AttackTarget> targets,
                                                 Boolean previousMoveCapture, Piece movingPieceAfter,
                                                 ValuableAttackEvents graphAttacks) {
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
        Boolean moreValuable = graphAttacks == null
                ? legacyValuableAttack(targets, movingPieceAfter) : graphAttacks.attackByMovingSide();
        Boolean pawnAttack = null;
        if (targets != null) {
            if (targets.isEmpty()) {
                pawnAttack = false;
            } else if (movingPieceAfter != null && movingPieceAfter != Piece.NONE) {
                pawnAttack = movingPieceAfter.getPieceType() == PieceType.PAWN
                        && targets.stream().anyMatch(t -> t.piece().getPieceType() != PieceType.PAWN);
            }
        }
        classify(NEW_ATTACK_ON_MORE_VALUABLE_PIECE, moreValuable, categories, unknown);
        classify(NEW_PAWN_ATTACK_ON_PIECE, pawnAttack, categories, unknown);
        return new CandidateMoveClassification(categories, unknown);
    }

    private static Boolean legacyValuableAttack(List<AttackTarget> targets, Piece movingPieceAfter) {
        if (targets == null) return null;
        if (targets.isEmpty()) return false;
        if (movingPieceAfter == null || movingPieceAfter == Piece.NONE) return null;
        return targets.stream().anyMatch(t ->
                ValuableAttackEvents.isMoreValuable(movingPieceAfter.getPieceType(), t.piece().getPieceType()));
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
