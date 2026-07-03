package dev.rightknight.repository;

import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.GameEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import java.util.List;

@Repository
public interface GameRepository extends CrudRepository<GameEntity, String> {

    // существующие методы
    List<GameEntity> findAllByUserId(String userId);

    List<GameEntity> findAllByUserIdIgnoreCaseAndCreatedAtBetween(
            String userId,
            ZonedDateTime start,
            ZonedDateTime end
    );

    List<GameEntity> findTop50ByOrderByCreatedAtDesc();

    List<GameEntity> findByOpeningNameContainingIgnoreCase(String search);

    List<GameEntity> findByOwnerOrderByCreatedAtDesc(AppUserEntity owner);

    List<GameEntity> findTop50ByOwnerOrderByCreatedAtDesc(AppUserEntity owner);

    List<GameEntity> findByOwnerAndOpeningNameContainingIgnoreCaseOrderByCreatedAtDesc(
            AppUserEntity owner,
            String openingName
    );

    List<GameEntity> findTop50ByUserIdOrderByCreatedAtDesc(String userId);

    List<GameEntity> findByUserIdAndOpeningNameContainingIgnoreCase(
            String userId,
            String openingName
    );

    Optional<GameEntity> findFirstByUserIdIgnoreCaseOrderByCreatedAtDesc(String userId);

    // новые методы для админки
    long countByCreatedAtAfter(ZonedDateTime createdAt);

    long countByOwner(AppUserEntity owner);

    Optional<GameEntity> findFirstByOwnerOrderByCreatedAtAsc(AppUserEntity owner);

    Optional<GameEntity> findFirstByOwnerOrderByCreatedAtDesc(AppUserEntity owner);

    void deleteByOwner(AppUserEntity owner);

    @Query("""
           select count(m)
           from GameMoveEntity m
           where m.game = ?1
           """)
    long countMovesByGame(GameEntity game);

    List<GameEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}