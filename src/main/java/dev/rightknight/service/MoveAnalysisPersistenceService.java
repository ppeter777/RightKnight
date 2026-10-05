package dev.rightknight.service;

import dev.rightknight.engine.EngineCandidate;
import dev.rightknight.model.GameMoveAnalysisCandidateEntity;
import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.repository.GameMoveAnalysisCandidateRepository;
import dev.rightknight.repository.GameMoveAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MoveAnalysisPersistenceService {
    private final GameMoveAnalysisRepository analysisRepository;
    private final GameMoveAnalysisCandidateRepository candidateRepository;

    // Called through a separate Spring bean, after engine work has finished.
    @Transactional
    public GameMoveAnalysisEntity save(GameMoveAnalysisEntity analysis, List<EngineCandidate> before) {
        if (before.isEmpty()) {
            throw new IllegalArgumentException("A played move must have pre-move candidates");
        }
        GameMoveAnalysisEntity saved = analysisRepository.save(analysis);
        List<GameMoveAnalysisCandidateEntity> candidates = before.stream().map(source -> {
            GameMoveAnalysisCandidateEntity target = new GameMoveAnalysisCandidateEntity();
            target.setGameMoveAnalysis(saved);
            target.setPvRank(source.getRank());
            target.setMoveUci(source.getBestMove());
            target.setEvalCp(source.getEvalCp());
            target.setMateIn(source.getMateIn());
            target.setScoreBound(source.getScoreBound());
            target.setDepth(source.getDepth());
            target.setSelectiveDepth(source.getSelDepth());
            target.setPvUci(source.getPv());
            return target;
        }).toList();
        candidateRepository.saveAll(candidates);
        return saved;
    }
}
