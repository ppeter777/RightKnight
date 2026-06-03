package dev.rightknight.repository;

import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.GameSyncStateEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameSyncStateRepository extends CrudRepository<GameSyncStateEntity, Long> {

    Optional<GameSyncStateEntity> findByAppUser(AppUserEntity appUser);
}
