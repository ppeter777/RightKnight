package dev.rightknight.repository;

import dev.rightknight.model.GameMoveAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameMoveAnalysisRepository
        extends JpaRepository<GameMoveAnalysisEntity, Long> {
}
