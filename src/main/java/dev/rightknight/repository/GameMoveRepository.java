package dev.rightknight.repository;

import dev.rightknight.model.GameMoveEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameMoveRepository extends JpaRepository<GameMoveEntity, Long> {

    List<GameMoveEntity> findByGame_IdOrderByPly(String gameId);

    void deleteByGame_Id(String gameId);

    boolean existsByGame_Id(String gameId);
}
