package dev.rightknight.analysis;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.move.Move;
import dev.rightknight.engine.ScoreBound;
import dev.rightknight.model.GameMoveAnalysisCandidateEntity;
import dev.rightknight.model.GameMoveAnalysisEntity;
import org.springframework.stereotype.Component;
import java.util.*;
import static dev.rightknight.analysis.ComplexityAssessment.*;
import static dev.rightknight.analysis.ComplexityAssessment.UnknownReason.*;

/** Reads only supplied data. No engine calls, DB access, played-move quality or solve-time input. */
@Component
public class ComplexityAssessmentCalculator {
    public static final String MODEL_VERSION = "complexity-diagnostic-v1";
    private final PositionComplexityCalculator positions;
    private final MoveFeatureClassifier moves;

    public ComplexityAssessmentCalculator(PositionComplexityCalculator positions, MoveFeatureClassifier moves) {
        this.positions = positions;
        this.moves = moves;
    }

    public ComplexityAssessment calculate(GameMoveAnalysisEntity analysis,
            List<GameMoveAnalysisCandidateEntity> candidates, String fenBefore,
            Integer requestedMultiPv, int topPvLimit) {
        return calculate(analysis, candidates, fenBefore, requestedMultiPv, topPvLimit, LossPolicy.v2());
    }

    public ComplexityAssessment calculate(GameMoveAnalysisEntity analysis,
            List<GameMoveAnalysisCandidateEntity> candidates, String fenBefore,
            Integer requestedMultiPv, int topPvLimit, LossPolicy policy) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(policy, "policy");
        if (topPvLimit <= 0 || requestedMultiPv != null && requestedMultiPv <= 0) {
            throw new IllegalArgumentException("MultiPV limits must be positive");
        }
        Board board = null;
        Map<String, Move> legal = null;
        if (fenBefore != null) {
            board = new Board();
            board.loadFromFen(fenBefore);
            legal = new HashMap<>();
            for (Move move : board.legalMoves()) legal.put(move.toString(), move);
            if (analysis.getLegalMovesCount() != null && analysis.getLegalMovesCount() != legal.size()) {
                throw new IllegalArgumentException("Legal move count does not match root FEN");
            }
        }
        Set<Integer> ranks = new HashSet<>();
        Set<String> ucis = new HashSet<>();
        for (var candidate : candidates) {
            if (candidate.getPvRank() == null || candidate.getPvRank() <= 0
                    || !ranks.add(candidate.getPvRank()) || candidate.getMoveUci() == null
                    || !candidate.getMoveUci().matches("[a-h][1-8][a-h][1-8][qrbn]?")
                    || !ucis.add(candidate.getMoveUci())) {
                throw new IllegalArgumentException("Invalid or duplicate candidate rank/UCI");
            }
            if (legal != null && !legal.containsKey(candidate.getMoveUci())) {
                throw new IllegalArgumentException("Candidate is not legal in root FEN");
            }
            var parent = candidate.getGameMoveAnalysis();
            if (parent != null && parent != analysis && (analysis.getId() == null
                    || !analysis.getId().equals(parent.getId()))) {
                throw new IllegalArgumentException("Candidate belongs to a different root analysis");
            }
        }
        if (analysis.getLegalMovesCount() != null && candidates.size() > analysis.getLegalMovesCount()) {
            throw new IllegalArgumentException("More candidates than legal moves");
        }
        var sorted = candidates.stream().sorted(Comparator.comparingInt(GameMoveAnalysisCandidateEntity::getPvRank)).toList();
        var best = sorted.stream().filter(c -> c.getPvRank() == 1).findFirst().orElse(null);
        var second = sorted.stream().filter(c -> c.getPvRank() == 2).findFirst().orElse(null);
        var gapReasons = comparisonReasons(best, second);
        if (second == null) gapReasons.add(MISSING_RANK_TWO);
        Long gap = gapReasons.isEmpty() ? (long) best.getEvalCp() - second.getEvalCp() : null;
        boolean inconsistentOrder = false;
        for (int i = 1; i < sorted.size(); i++) {
            if (comparisonReasons(sorted.get(i - 1), sorted.get(i)).contains(INCONSISTENT_RANKING)) {
                inconsistentOrder = true;
            }
        }
        List<CandidateAssessment> results = new ArrayList<>();
        var allReasons = EnumSet.noneOf(UnknownReason.class);
        if (sorted.isEmpty()) allReasons.add(NO_CANDIDATES);
        for (var candidate : sorted) {
            var reasons = comparisonReasons(best, candidate);
            if (inconsistentOrder) reasons.add(INCONSISTENT_RANKING);
            Long loss = reasons.isEmpty() ? (long) best.getEvalCp() - candidate.getEvalCp() : null;
            Piece pieceAfter = null;
            if (legal != null) {
                Move move = legal.get(candidate.getMoveUci());
                pieceAfter = move.getPromotion() == Piece.NONE ? board.getPiece(move.getFrom()) : move.getPromotion();
            }
            results.add(new CandidateAssessment(candidate.getPvRank(), candidate.getMoveUci(),
                    candidate.getEvalCp(), candidate.getMateIn(), candidate.getDepth(), candidate.getScoreBound(),
                    loss, policy.classify(loss), policy.acceptable(loss), reasons,
                    moves.classify(candidate, analysis.getPreviousMoveCapture(), pieceAfter)));
            allReasons.addAll(reasons);
        }
        Integer acceptable = acceptableCount(results);
        var inWindow = results.stream().filter(c -> c.pvRank() <= topPvLimit).toList();
        int expectedRanks = legal == null ? topPvLimit : Math.min(topPvLimit, legal.size());
        boolean windowComplete = inWindow.size() == expectedRanks;
        // Distinct positive ranks <= expectedRanks must cover the full prefix.
        windowComplete &= inWindow.stream().allMatch(c -> c.pvRank() <= expectedRanks);
        var lossRanks = new EnumMap<LossCategory, List<Integer>>(LossCategory.class);
        for (var category : LossCategory.values()) {
            lossRanks.put(category, inWindow.stream().filter(c -> c.lossCategory() == category).map(CandidateAssessment::pvRank).toList());
        }
        var featureRanks = new EnumMap<CandidateMoveCategory, CategoryEvidence>(CandidateMoveCategory.class);
        for (var category : CandidateMoveCategory.values()) {
            var present = inWindow.stream().filter(c -> c.features().categories().contains(category)).toList();
            featureRanks.put(category, new CategoryEvidence(present.stream().map(CandidateAssessment::pvRank).toList(),
                    inWindow.stream().filter(c -> c.features().unknownCategories().contains(category)).map(CandidateAssessment::pvRank).toList(),
                    present.stream().filter(c -> Boolean.TRUE.equals(c.acceptable())).map(CandidateAssessment::pvRank).toList()));
        }
        return new ComplexityAssessment(MODEL_VERSION, policy, requestedMultiPv, results.size(),
                legal == null ? null : legal.keySet().equals(ucis), positions.calculate(analysis), gap, gapReasons,
                acceptable, acceptable == null ? null : acceptable / (double) results.size(), allReasons, results,
                new RankWindow(topPvLimit, windowComplete, inWindow.size(), acceptableCount(inWindow), lossRanks, featureRanks), 1.0);
    }

    private static Integer acceptableCount(List<CandidateAssessment> candidates) {
        if (candidates.isEmpty() || candidates.stream().anyMatch(c -> c.acceptable() == null)) return null;
        return (int) candidates.stream().filter(c -> Boolean.TRUE.equals(c.acceptable())).count();
    }

    private static EnumSet<UnknownReason> comparisonReasons(GameMoveAnalysisCandidateEntity best,
                                                           GameMoveAnalysisCandidateEntity candidate) {
        var reasons = EnumSet.noneOf(UnknownReason.class);
        if (best == null) reasons.add(MISSING_RANK_ONE);
        if (best != null) scoreReasons(best, reasons);
        if (candidate != null) scoreReasons(candidate, reasons);
        if (best != null && candidate != null) {
            if (best.getDepth() != null && candidate.getDepth() != null && !best.getDepth().equals(candidate.getDepth())) {
                reasons.add(DEPTH_MISMATCH);
            }
            if (best.getEvalCp() != null && candidate.getEvalCp() != null && candidate.getEvalCp() > best.getEvalCp()) {
                reasons.add(INCONSISTENT_RANKING);
            }
        }
        return reasons;
    }

    private static void scoreReasons(GameMoveAnalysisCandidateEntity candidate, Set<UnknownReason> reasons) {
        if (candidate.getEvalCp() == null) reasons.add(MISSING_CP_SCORE);
        if (candidate.getMateIn() != null) reasons.add(MATE_SCORE);
        if (candidate.getScoreBound() != ScoreBound.EXACT) reasons.add(NON_EXACT_SCORE);
        if (candidate.getDepth() == null || candidate.getDepth() <= 0) reasons.add(MISSING_DEPTH);
    }
}
