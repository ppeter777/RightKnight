package dev.rightknight.service;

import dev.rightknight.analysis.ComplexityAssessment;
import dev.rightknight.analysis.ComplexityAssessmentCalculator;
import dev.rightknight.repository.GameMoveAnalysisRepository;
import dev.rightknight.repository.GameMoveAnalysisCandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** On-demand assessment of an existing analysis run, including historical partial data. */
@Service
@RequiredArgsConstructor
public class ComplexityAssessmentService {
    private final GameMoveAnalysisRepository analyses;
    private final GameMoveAnalysisCandidateRepository candidates;
    private final ComplexityAssessmentCalculator calculator;

    @Transactional(readOnly = true)
    public ComplexityAssessment assess(long analysisId, int topPvLimit) {
        return assess(analysisId, topPvLimit, 100);
    }

    @Transactional(readOnly = true)
    public ComplexityAssessment assess(long analysisId, int topPvLimit, int advantageThresholdCp) {
        var analysis = analyses.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown move analysis: " + analysisId));
        return calculator.calculate(analysis, candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(analysisId),
                analysis.getGameMove().getFenBefore(), analysis.getGameAnalysis().getMultiPv(), topPvLimit,
                dev.rightknight.analysis.LossPolicy.v2(), advantageThresholdCp);
    }
}
