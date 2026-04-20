package dev.rightknight.repository;

import dev.rightknight.model.GameEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface GameRepository extends CrudRepository<GameEntity, String> {
    // Поиск игр конкретного пользователя
    List<GameEntity> findAllByUserId(String userId);
    List<GameEntity> findAllByUserIdAndCreatedAtBetween(String userId, ZonedDateTime start, ZonedDateTime end);
}
