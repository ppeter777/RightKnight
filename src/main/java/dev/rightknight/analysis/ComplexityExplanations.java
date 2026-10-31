package dev.rightknight.analysis;

import dev.rightknight.analysis.ComplexityAssessment.CandidateAssessment;
import dev.rightknight.model.GameMoveAnalysisEntity;
import java.util.*;

/** Descriptive hypotheses over observed candidates, not a calibrated difficulty score. */
public record ComplexityExplanations(String version, int advantageThresholdCp,
        int analyzedCandidateCount, Boolean candidateSetComplete, Integer recaptureMovesCount,
        List<Integer> acceptableRanks, List<Integer> activeRanks, List<Integer> advantageousActiveRanks,
        List<Explanation> explanations) {
    public ComplexityExplanations {
        acceptableRanks = List.copyOf(acceptableRanks);
        activeRanks = List.copyOf(activeRanks);
        advantageousActiveRanks = List.copyOf(advantageousActiveRanks);
        explanations = List.copyOf(explanations);
    }

    public enum Rule {
        ALL_OBSERVED_CANDIDATES_ACCEPTABLE,
        ONLY_ACCEPTABLE_OBSERVED_MOVE_IS_RECAPTURE,
        TWO_OR_THREE_ACCEPTABLE_WITH_WORSE_ALTERNATIVES,
        ONLY_ONE_ACTIVE_CONTINUATION_HAS_ADVANTAGE
    }
    public enum Status { PRESENT, ABSENT, UNKNOWN }
    public record Explanation(Rule rule, Status status, String reason) {}

    private static final Set<CandidateMoveCategory> ACTIVE = EnumSet.of(
            CandidateMoveCategory.CAPTURE, CandidateMoveCategory.GIVES_CHECK,
            CandidateMoveCategory.PROMOTION, CandidateMoveCategory.NEW_ATTACK_ON_MORE_VALUABLE_PIECE,
            CandidateMoveCategory.NEW_PAWN_ATTACK_ON_PIECE);

    public static ComplexityExplanations calculate(GameMoveAnalysisEntity analysis,
            List<CandidateAssessment> candidates, Boolean complete, int advantageThresholdCp) {
        if (advantageThresholdCp <= 0) throw new IllegalArgumentException("Advantage threshold must be positive");
        var accepted = candidates.stream().filter(c -> Boolean.TRUE.equals(c.acceptable())).toList();
        var active = candidates.stream().filter(c -> Boolean.TRUE.equals(active(c))).toList();
        var advantageous = active.stream().filter(c -> c.lossCp() != null && c.evalCp() != null
                && c.evalCp() >= advantageThresholdCp).toList();
        boolean qualityKnown = !candidates.isEmpty() && candidates.stream().allMatch(c -> c.acceptable() != null);
        List<Explanation> output = new ArrayList<>();
        output.add(explain(Rule.ALL_OBSERVED_CANDIDATES_ACCEPTABLE,
                qualityKnown ? accepted.size() == candidates.size() : null,
                "Все рассмотренные кандидаты приемлемы; это не доказательство спокойной позиции."));
        Boolean recapture = null;
        if (Boolean.FALSE.equals(analysis.getPreviousMoveCapture())) recapture = false;
        else if (qualityKnown) {
            if (accepted.size() != 1) recapture = false;
            else if (Boolean.TRUE.equals(analysis.getPreviousMoveCapture())) {
                var features = accepted.getFirst().features();
                if (!features.unknownCategories().contains(CandidateMoveCategory.RECAPTURE)) {
                    recapture = features.categories().contains(CandidateMoveCategory.RECAPTURE);
                }
            }
        }
        output.add(explain(Rule.ONLY_ACCEPTABLE_OBSERVED_MOVE_IS_RECAPTURE, recapture,
                "Единственный приемлемый среди рассмотренных ходов — ответное взятие; число легальных ответных взятий указано отдельно."));
        output.add(explain(Rule.TWO_OR_THREE_ACCEPTABLE_WITH_WORSE_ALTERNATIVES,
                qualityKnown ? accepted.size() >= 2 && accepted.size() <= 3 && accepted.size() < candidates.size() : null,
                "Среди рассмотренных ходов приемлемы два или три, остальные выходят за порог приемлемости."));
        boolean activityKnown = candidates.stream().allMatch(c -> active(c) != null);
        output.add(explain(Rule.ONLY_ONE_ACTIVE_CONTINUATION_HAS_ADVANTAGE,
                qualityKnown && activityKnown ? active.size() >= 2 && advantageous.size() == 1 : null,
                "Из нескольких активных корневых ходов только один достигает заданного порога преимущества; тихие ходы проверяются отдельно в списке кандидатов."));
        return new ComplexityExplanations("observed-patterns-v1", advantageThresholdCp, candidates.size(), complete,
                analysis.getRecaptureMovesCount(), ranks(accepted), ranks(active), ranks(advantageous), output);
    }

    private static Boolean active(CandidateAssessment candidate) {
        if (!Collections.disjoint(candidate.features().categories(), ACTIVE)) return true;
        if (!Collections.disjoint(candidate.features().unknownCategories(), ACTIVE)) return null;
        return false;
    }

    private static List<Integer> ranks(List<CandidateAssessment> candidates) {
        return candidates.stream().map(CandidateAssessment::pvRank).toList();
    }

    private static Explanation explain(Rule rule, Boolean present, String definition) {
        return new Explanation(rule, present == null ? Status.UNKNOWN : present ? Status.PRESENT : Status.ABSENT,
                present == null ? "Недостаточно сопоставимых оценок или признаков для проверки. " + definition
                        : present ? definition : "Условие не выполнено. Проверяемое условие: " + definition);
    }
}
