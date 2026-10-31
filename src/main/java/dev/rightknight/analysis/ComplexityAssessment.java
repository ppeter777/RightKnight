package dev.rightknight.analysis;

import dev.rightknight.engine.ScoreBound;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Diagnostic assessment of the root and its observed candidates; never a human difficulty score. */
public record ComplexityAssessment(
        String modelVersion, LossPolicy lossPolicy, Integer requestedMultiPv,
        int analyzedCandidateCount, Boolean candidateSetComplete, PositionComplexity position,
        Long bestSecondGapCp, Set<UnknownReason> gapUnknownReasons,
        Integer acceptableCandidateCount, Double acceptableCandidateRatio,
        Set<UnknownReason> unknownReasons, List<CandidateAssessment> candidates,
        RankWindow window, ComplexityExplanations explanations, double complexityFactor) {
    public ComplexityAssessment {
        gapUnknownReasons = Set.copyOf(gapUnknownReasons);
        unknownReasons = Set.copyOf(unknownReasons);
        candidates = List.copyOf(candidates);
    }

    public enum UnknownReason {
        NO_CANDIDATES, MISSING_RANK_ONE, MISSING_RANK_TWO, MISSING_CP_SCORE,
        MATE_SCORE, NON_EXACT_SCORE, MISSING_DEPTH, DEPTH_MISMATCH, INCONSISTENT_RANKING
    }

    public record CandidateAssessment(int pvRank, String moveUci, Integer evalCp, Integer mateIn,
            Integer depth, ScoreBound scoreBound, Long lossCp, LossCategory lossCategory,
            Boolean acceptable, Set<UnknownReason> unknownReasons, CandidateMoveClassification features) {
        public CandidateAssessment { unknownReasons = Set.copyOf(unknownReasons); }
    }

    /** Rank window is independent of how many candidates the engine was asked to return. */
    public record RankWindow(int topPvLimit, boolean ranksComplete, int observedCandidateCount,
            Integer acceptableCandidateCount, Map<LossCategory, List<Integer>> lossCategoryRanks,
            Map<CandidateMoveCategory, CategoryEvidence> moveCategoryRanks) {
        public RankWindow {
            var copy = new java.util.EnumMap<LossCategory, List<Integer>>(LossCategory.class);
            lossCategoryRanks.forEach((key, ranks) -> copy.put(key, List.copyOf(ranks)));
            lossCategoryRanks = java.util.Collections.unmodifiableMap(copy);
            moveCategoryRanks = Map.copyOf(moveCategoryRanks);
        }
    }

    /** acceptableRanks lists confirmed matches only; unknown feature/quality data is not false. */
    public record CategoryEvidence(List<Integer> presentRanks, List<Integer> unknownRanks,
            List<Integer> acceptableRanks) {
        public CategoryEvidence {
            presentRanks = List.copyOf(presentRanks);
            unknownRanks = List.copyOf(unknownRanks);
            acceptableRanks = List.copyOf(acceptableRanks);
        }
    }
}
