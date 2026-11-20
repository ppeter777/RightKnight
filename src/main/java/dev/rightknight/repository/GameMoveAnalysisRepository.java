package dev.rightknight.repository;

import dev.rightknight.model.GameMoveAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameMoveAnalysisRepository
        extends JpaRepository<GameMoveAnalysisEntity, Long> {
    GameMoveAnalysisEntity findByGameMoveId(Long moveId);
    List<GameMoveAnalysisEntity> findByGameAnalysis_Id(long runId);
}
