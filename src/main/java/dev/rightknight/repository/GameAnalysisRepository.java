package dev.rightknight.repository;

import dev.rightknight.model.GameAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface GameAnalysisRepository
        extends JpaRepository<GameAnalysisEntity, Long> {
    @EntityGraph(attributePaths = "game")
    Optional<GameAnalysisEntity> findByIdAndGame_Id(long id, String gameId);
}
