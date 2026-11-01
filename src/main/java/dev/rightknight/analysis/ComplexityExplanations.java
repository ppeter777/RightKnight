package dev.rightknight.analysis;

import dev.rightknight.analysis.ComplexityAssessment.CandidateAssessment;
import dev.rightknight.model.GameMoveAnalysisEntity;
import java.util.*;

/** Descriptive hypotheses over observed candidates, not a calibrated difficulty score. */
public record ComplexityExplanations(String version, int advantageThresholdCp,
        int analyzedCandidateCount, Boolean candidateSetComplete, Integer recaptureMovesCount,
        Boolean recapturesFullyAnalyzed, List<Integer> recaptureRanks,
        List<Integer> acceptableRecaptureRanks, List<Integer> unknownRecaptureRanks,
        List<Integer> acceptableRanks, List<Integer> activeRanks, List<Integer> advantageousActiveRanks,
        List<Explanation> explanations) {
    public ComplexityExplanations {
        recaptureRanks = List.copyOf(recaptureRanks);
        acceptableRecaptureRanks = List.copyOf(acceptableRecaptureRanks);
        unknownRecaptureRanks = List.copyOf(unknownRecaptureRanks);
        acceptableRanks = List.copyOf(acceptableRanks);
        activeRanks = List.copyOf(activeRanks);
        advantageousActiveRanks = List.copyOf(advantageousActiveRanks);
        explanations = List.copyOf(explanations);
    }

    public enum Rule {
        ALL_OBSERVED_CANDIDATES_ACCEPTABLE,
        ONLY_ACCEPTABLE_OBSERVED_MOVE_IS_RECAPTURE,
        ONLY_ONE_ACCEPTABLE_OBSERVED_MOVE,
        SINGLE_LEGAL_RECAPTURE_IS_ONLY_ACCEPTABLE_OBSERVED_MOVE,
        MULTIPLE_LEGAL_RECAPTURES_ONLY_ONE_ACCEPTABLE_OBSERVED_MOVE,
        MULTIPLE_ACCEPTABLE_OBSERVED_MOVES_ALL_RECAPTURES,
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
        var recaptures = candidates.stream().filter(c -> Boolean.TRUE.equals(recapture(c))).toList();
        var unknownRecaptures = candidates.stream().filter(c -> recapture(c) == null).toList();
        Integer legalRecaptures = analysis.getRecaptureMovesCount();
        if (legalRecaptures != null && recaptures.size() > legalRecaptures) {
            throw new IllegalArgumentException("Observed recaptures exceed legal recapture count");
        }
        Boolean recapturesComplete = legalRecaptures == null || analysis.getPreviousMoveCapture() == null
                || !unknownRecaptures.isEmpty() ? null : recaptures.size() == legalRecaptures;
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
        output.add(explain(Rule.ONLY_ONE_ACCEPTABLE_OBSERVED_MOVE,
                qualityKnown ? accepted.size() == 1 : null,
                "Среди рассмотренных ходов приемлем только один; это не обязательно активный ход или ход с преимуществом."));
        output.add(explain(Rule.SINGLE_LEGAL_RECAPTURE_IS_ONLY_ACCEPTABLE_OBSERVED_MOVE,
                and(recapture, legalRecaptures == null ? null : legalRecaptures == 1),
                "Легально только одно ответное взятие, и оно является единственным приемлемым среди рассмотренных ходов."));
        // An unseen legal recapture must not be implicitly classified as unacceptable.
        output.add(explain(Rule.MULTIPLE_LEGAL_RECAPTURES_ONLY_ONE_ACCEPTABLE_OBSERVED_MOVE,
                and(recapture, legalRecaptures == null ? null : legalRecaptures > 1,
                        Boolean.TRUE.equals(recapturesComplete) ? true : null),
                "Легальных ответных взятий несколько, все они представлены в анализе; только одно приемлемо, и иных приемлемых ходов среди рассмотренных нет."));
        Boolean allAcceptedRecaptures = true;
        for (var candidate : accepted) allAcceptedRecaptures = and(allAcceptedRecaptures, recapture(candidate));
        output.add(explain(Rule.MULTIPLE_ACCEPTABLE_OBSERVED_MOVES_ALL_RECAPTURES,
                and(analysis.getPreviousMoveCapture(), qualityKnown ? accepted.size() >= 2 : null, allAcceptedRecaptures),
                "Среди рассмотренных ходов приемлемы несколько, и все они — ответные взятия; полнота охвата ответных взятий указана отдельно."));
        output.add(explain(Rule.TWO_OR_THREE_ACCEPTABLE_WITH_WORSE_ALTERNATIVES,
                qualityKnown ? accepted.size() >= 2 && accepted.size() <= 3 && accepted.size() < candidates.size() : null,
                "Среди рассмотренных ходов приемлемы два или три, остальные выходят за порог приемлемости."));
        boolean activityKnown = candidates.stream().allMatch(c -> active(c) != null);
        output.add(explain(Rule.ONLY_ONE_ACTIVE_CONTINUATION_HAS_ADVANTAGE,
                qualityKnown && activityKnown ? active.size() >= 2 && advantageous.size() == 1 : null,
                "Из нескольких активных корневых ходов только один достигает заданного порога преимущества; тихие ходы проверяются отдельно в списке кандидатов."));
        return new ComplexityExplanations("observed-patterns-v2", advantageThresholdCp, candidates.size(), complete,
                legalRecaptures, recapturesComplete, ranks(recaptures),
                ranks(recaptures.stream().filter(c -> Boolean.TRUE.equals(c.acceptable())).toList()),
                ranks(unknownRecaptures), ranks(accepted), ranks(active), ranks(advantageous), output);
    }

    private static Boolean recapture(CandidateAssessment candidate) {
        var features = candidate.features();
        if (features.categories().contains(CandidateMoveCategory.RECAPTURE)) return true;
        return features.unknownCategories().contains(CandidateMoveCategory.RECAPTURE) ? null : false;
    }

    private static Boolean and(Boolean... conditions) {
        boolean unknown = false;
        for (Boolean value : conditions) {
            if (Boolean.FALSE.equals(value)) return false;
            if (value == null) unknown = true;
        }
        return unknown ? null : Boolean.TRUE;
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
                present == null ? "Недостаточно сопоставимых оценок, признаков или охвата ответных взятий для проверки. " + definition
                        : present ? definition : "Условие не выполнено. Проверяемое условие: " + definition);
    }
}
