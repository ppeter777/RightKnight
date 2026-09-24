package dev.rightknight.repository;

import dev.rightknight.model.GameAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameAnalysisRepository
        extends JpaRepository<GameAnalysisEntity, Long> {
}
