package dev.rightknight.repository;

import dev.rightknight.model.GameMoveEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameMoveRepository extends JpaRepository<GameMoveEntity, Long> {

    List<GameMoveEntity> findByGame_IdOrderByPly(String gameId);

    Optional<GameMoveEntity> findById(Long moveId);

    void deleteByGame_Id(String gameId);

    boolean existsByGame_Id(String gameId);

    List<GameMoveEntity> findByGame_IdOrderByPlyAsc(String gameId);
}
