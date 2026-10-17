package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.Piece;
import dev.rightknight.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static dev.rightknight.analysis.CandidateMoveCategory.*;
import static org.junit.jupiter.api.Assertions.*;

class MoveFeatureClassifierTest {
    private final MoveFeatureClassifier classifier = new MoveFeatureClassifier();

    @Test
    void distinguishesHigherValueEqualValueAndPawnTargets() {
        var bishop = candidate(List.of(new AttackTarget("d8", Piece.BLACK_QUEEN)));
        assertEquals(Set.of(NEW_ATTACK_ON_MORE_VALUABLE_PIECE), classifier.classify(bishop, false, Piece.WHITE_BISHOP).categories());
        var pawn = candidate(List.of(new AttackTarget("f5", Piece.BLACK_KNIGHT)));
        assertEquals(Set.of(NEW_ATTACK_ON_MORE_VALUABLE_PIECE, NEW_PAWN_ATTACK_ON_PIECE), classifier.classify(pawn, false, Piece.WHITE_PAWN).categories());
        assertEquals(Set.of(), classifier.classify(pawn, false, Piece.WHITE_BISHOP).categories());
        assertEquals(Set.of(), classifier.classify(candidate(List.of(new AttackTarget("f5", Piece.BLACK_PAWN))), false, Piece.WHITE_PAWN).categories());
        // A newly promoted queen attacking a rook is not a lower-value attacker.
        assertEquals(Set.of(), classifier.classify(candidate(List.of(new AttackTarget("h8", Piece.BLACK_ROOK))), false, Piece.WHITE_QUEEN).categories());
    }

    @Test
    void unknownTargetsDifferFromEmptyTargetsAndMissingAttacker() {
        Set<CandidateMoveCategory> attacks = Set.of(NEW_ATTACK_ON_MORE_VALUABLE_PIECE, NEW_PAWN_ATTACK_ON_PIECE);
        assertEquals(attacks, classifier.classify(candidate(null), false, Piece.WHITE_PAWN).unknownCategories());
        assertTrue(classifier.classify(candidate(List.of()), false).complete());
        assertEquals(attacks, classifier.classify(candidate(List.of(new AttackTarget("f5", Piece.BLACK_KNIGHT))), false).unknownCategories());
    }

    @Test
    void playedAndCandidateUseIdenticalRulesForOtherCaptureAndAttack() {
        var candidate = candidate(List.of(new AttackTarget("d8", Piece.BLACK_QUEEN)));
        candidate.setCapture(true);
        GameMoveAnalysisEntity played = new GameMoveAnalysisEntity();
        played.setPreviousMoveCapture(true);
        played.setPlayedCapture(true);
        played.setPlayedRecapture(false);
        played.setPlayedGivesCheck(false);
        played.setPlayedPromotion(false);
        played.setPlayedNewAttackTargets(candidate.getNewAttackTargets());
        var expected = classifier.classify(candidate, true, Piece.WHITE_BISHOP);
        assertEquals(Set.of(CAPTURE, OTHER_CAPTURE, NEW_ATTACK_ON_MORE_VALUABLE_PIECE), expected.categories());
        assertEquals(expected, classifier.classifyPlayed(played, Piece.WHITE_BISHOP));
        played.setPlayedRecapture(true);
        assertFalse(classifier.classifyPlayed(played, Piece.WHITE_BISHOP).categories().contains(OTHER_CAPTURE));
    }

    private GameMoveAnalysisCandidateEntity candidate(List<AttackTarget> targets) {
        var candidate = new GameMoveAnalysisCandidateEntity();
        candidate.setCapture(false);
        candidate.setRecapture(false);
        candidate.setGivesCheck(false);
        candidate.setPromotion(false);
        candidate.setNewAttackTargets(targets);
        return candidate;
    }
}
