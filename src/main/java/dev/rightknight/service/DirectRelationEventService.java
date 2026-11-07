package dev.rightknight.service;

import dev.rightknight.analysis.DirectRelationEventCalculator;
import dev.rightknight.analysis.DirectRelationEvents;
import dev.rightknight.repository.GameMoveAnalysisCandidateRepository;
import dev.rightknight.repository.GameMoveAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DirectRelationEventService {
    private final GameMoveAnalysisRepository analyses;
    private final GameMoveAnalysisCandidateRepository candidates;
    private final DirectRelationEventCalculator calculator = new DirectRelationEventCalculator();

    public record Candidate(int pvRank, DirectRelationEvents events) {}
    public record Report(DirectRelationEvents played, List<Candidate> candidates) {
        public Report { candidates = List.copyOf(candidates); }
    }

    @Transactional(readOnly = true)
    public Report assess(long analysisId) {
        var analysis = analyses.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown move analysis: " + analysisId));
        var root = analysis.getAttackSnapshotBefore();
        var played = calculator.calculate(analysis.getGameMove().getUci(), root, analysis.getPlayedAttackDelta());
        var alternatives = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(analysisId).stream()
                .map(candidate -> new Candidate(candidate.getPvRank(),
                        calculator.calculate(candidate.getMoveUci(), root, candidate.getAttackDelta())))
                .toList();
        return new Report(played, alternatives);
    }
}
