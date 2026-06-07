package dev.rightknight.service;

import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.GameSyncStateEntity;
import dev.rightknight.model.GameImportRangeEntity;
import dev.rightknight.repository.GameImportRangeRepository;
import dev.rightknight.repository.GameRepository;
import dev.rightknight.repository.GameSyncStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.time.Clock;
import java.util.Optional;

@Service
public class GameSyncService {

    private static final Logger log = LoggerFactory.getLogger(GameSyncService.class);

    private static final Duration MIN_SYNC_INTERVAL = Duration.ofMinutes(15);

    private final GameSyncStateRepository gameSyncStateRepository;
    private final GameRepository gameRepository;
    private final GameImportService gameImportService;
    private final Clock clock;

    private final GameImportRangeRepository gameImportRangeRepository;

    public GameSyncService(
            GameSyncStateRepository gameSyncStateRepository,
            GameRepository gameRepository,
            GameImportService gameImportService,
            Clock clock,
            GameImportRangeRepository gameImportRangeRepository
    ) {
        this.gameSyncStateRepository = gameSyncStateRepository;
        this.gameRepository = gameRepository;
        this.gameImportService = gameImportService;
        this.clock = clock;
        this.gameImportRangeRepository = gameImportRangeRepository;
    }

    private void sync(
            AppUserEntity appUser,
            GameSyncStateEntity state,
            String lichessUsername,
            ZonedDateTime now
    ) {
        try {
            state.setStatus("RUNNING");
            state.setLastError(null);
            gameSyncStateRepository.save(state);

            ZonedDateTime from = calculateSyncFrom(lichessUsername, now);
            ZonedDateTime until = now;

            int importedGames = gameImportService.importGames(appUser, from, until);

            var newestGame = gameRepository.findFirstByUserIdIgnoreCaseOrderByCreatedAtDesc(lichessUsername);

            newestGame.ifPresent(game -> {
                state.setNewestGameCreatedAt(game.getCreatedAt());
                state.setNewestGameId(game.getId());
            });

            state.setSyncFrom(now.minusYears(1));
            state.setLastSuccessAt(ZonedDateTime.now(clock));
            state.setStatus("IDLE");
            gameSyncStateRepository.save(state);

            log.info(
                    "Game sync finished: appUserId={}, lichessUsername={}, importedGames={}",
                    appUser.getId(), lichessUsername, importedGames
            );
        } catch (Exception e) {
            log.warn(
                    "Game sync failed: appUserId={}, lichessUsername={}, error={}",
                    appUser.getId(), lichessUsername, e.getMessage(), e
            );

            state.setStatus("FAILED");
            state.setLastError(e.getMessage());
            gameSyncStateRepository.save(state);
        }
    }

    public void syncNow(AppUserEntity appUser) {
        if (appUser.getLichessUsername() == null || appUser.getLichessUsername().isBlank()) {
            return;
        }

        String lichessUsername = normalize(appUser.getLichessUsername());
        ZonedDateTime now = ZonedDateTime.now(clock);

        GameSyncStateEntity state = gameSyncStateRepository
                .findByAppUser(appUser)
                .orElseGet(() -> createInitialState(appUser, lichessUsername, now));

        sync(appUser, state, lichessUsername, now);
    }

    public boolean shouldSync(AppUserEntity appUser) {

        if (appUser.getLichessUsername() == null ||
                appUser.getLichessUsername().isBlank()) {
            return false;
        }

        var state = gameSyncStateRepository
                .findByAppUser(appUser)
                .orElse(null);

        if (state == null) {
            return true;
        }

        if ("RUNNING".equals(state.getStatus())) {
            return false;
        }

        ZonedDateTime now = ZonedDateTime.now(clock);

        return state.getLastSuccessAt() == null
                || state.getLastSuccessAt().isBefore(
                now.minus(MIN_SYNC_INTERVAL)
        );
    }

    private ZonedDateTime calculateSyncFrom(String lichessUsername, ZonedDateTime now) {
        ZonedDateTime earliestAllowed = now.minusYears(1);

        return gameRepository.findFirstByUserIdIgnoreCaseOrderByCreatedAtDesc(lichessUsername)
                .map(game -> game.getCreatedAt().minusDays(1))
                .filter(date -> date.isAfter(earliestAllowed))
                .orElse(earliestAllowed);
    }

    private GameSyncStateEntity createInitialState(
            AppUserEntity appUser,
            String lichessUsername,
            ZonedDateTime now
    ) {
        var state = new GameSyncStateEntity();
        state.setAppUser(appUser);
        state.setLichessUsername(lichessUsername);
        state.setSyncFrom(now.minusYears(1));
        state.setStatus("IDLE");
        return gameSyncStateRepository.save(state);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public void syncPeriodNow(AppUserEntity appUser, ZonedDateTime from, ZonedDateTime until) {
        if (appUser.getLichessUsername() == null || appUser.getLichessUsername().isBlank()) {
            return;
        }

        String lichessUsername = normalize(appUser.getLichessUsername());
        log.info(
                "Period game sync requested: appUserId={}, lichessUsername={}, from={}, until={}",
                appUser.getId(), lichessUsername, from, until
        );

        boolean alreadyCovered = gameImportRangeRepository
                .existsByAppUserAndStatusAndRangeFromLessThanEqualAndRangeUntilGreaterThanEqual(
                        appUser,
                        "SUCCESS",
                        from,
                        until
                );

        if (alreadyCovered) {
            log.info(
                    "Period game sync skipped, range already covered: appUserId={}, lichessUsername={}, from={}, until={}",
                    appUser.getId(), lichessUsername, from, until
            );
            return;
        }

        var range = new GameImportRangeEntity();
        range.setAppUser(appUser);
        range.setLichessUsername(lichessUsername);
        range.setRangeFrom(from);
        range.setRangeUntil(until);
        range.setStatus("RUNNING");

        gameImportRangeRepository.save(range);

        try {
            log.info(
                    "Period game sync started: appUserId={}, lichessUsername={}, from={}, until={}",
                    appUser.getId(), lichessUsername, from, until
            );
            int importedGames = gameImportService.importGames(
                    appUser,
                    from.minusDays(1),
                    until
            );

            log.info(
                    "Period game sync finished: appUserId={}, lichessUsername={}, from={}, until={}, importedGames={}",
                    appUser.getId(), lichessUsername, from, until, importedGames
            );
            range.setStatus("SUCCESS");
            range.setGamesImported(importedGames);
            range.setLastError(null);
            gameImportRangeRepository.save(range);
        } catch (Exception e) {
            log.warn(
                    "Period game sync failed: appUserId={}, lichessUsername={}, from={}, until={}, error={}",
                    appUser.getId(), lichessUsername, from, until, e.getMessage(), e
            );
            range.setStatus("FAILED");
            range.setLastError(e.getMessage());
            gameImportRangeRepository.save(range);

            throw e;
        }
    }

    public boolean isPeriodCovered(AppUserEntity appUser, ZonedDateTime from, ZonedDateTime until) {
        if (appUser.getLichessUsername() == null || appUser.getLichessUsername().isBlank()) {
            return false;
        }

        return gameImportRangeRepository
                .existsByAppUserAndStatusAndRangeFromLessThanEqualAndRangeUntilGreaterThanEqual(
                        appUser,
                        "SUCCESS",
                        from,
                        until
                );
    }

    public Optional<GameSyncStateEntity> getSyncState(AppUserEntity appUser) {
        return gameSyncStateRepository.findByAppUser(appUser);
    }

}
