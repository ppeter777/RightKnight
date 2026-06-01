package dev.rightknight.repository;

import dev.rightknight.model.AppUserEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends CrudRepository<AppUserEntity, Long> {

    Optional<AppUserEntity> findByUsernameIgnoreCase(String username);

    Optional<AppUserEntity> findByLichessUsernameIgnoreCase(String lichessUsername);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByLichessUsernameIgnoreCase(String lichessUsername);
}
