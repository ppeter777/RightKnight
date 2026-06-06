package dev.rightknight.repository;

import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.GameImportRangeEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;

@Repository
public interface GameImportRangeRepository extends CrudRepository<GameImportRangeEntity, Long> {

    boolean existsByAppUserAndStatusAndRangeFromLessThanEqualAndRangeUntilGreaterThanEqual(
            AppUserEntity appUser,
            String status,
            ZonedDateTime rangeFrom,
            ZonedDateTime rangeUntil
    );
}
