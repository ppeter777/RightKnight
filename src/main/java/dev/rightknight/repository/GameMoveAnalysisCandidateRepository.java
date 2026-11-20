package dev.rightknight.repository;

import dev.rightknight.model.GameMoveAnalysisCandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameMoveAnalysisCandidateRepository extends JpaRepository<GameMoveAnalysisCandidateEntity, Long> {
    List<GameMoveAnalysisCandidateEntity> findByGameMoveAnalysis_IdOrderByPvRankAsc(Long analysisId);
    List<GameMoveAnalysisCandidateEntity> findByGameMoveAnalysis_GameAnalysis_IdOrderByPvRankAsc(long runId);
}
