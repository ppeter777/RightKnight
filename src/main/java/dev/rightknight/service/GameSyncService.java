package dev.rightknight.service;

import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.GameSyncStateEntity;
import dev.rightknight.repository.GameRepository;
import dev.rightknight.repository.GameSyncStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.time.Clock;

@Service
public class GameSyncService {

    private static final Logger log = LoggerFactory.getLogger(GameSyncService.class);

    private static final Duration MIN_SYNC_INTERVAL = Duration.ofMinutes(15);

    private final GameSyncStateRepository gameSyncStateRepository;
    private final GameRepository gameRepository;
    private final GameImportService gameImportService;
    private final Clock clock;

    public GameSyncService(
            GameSyncStateRepository gameSyncStateRepository,
            GameRepository gameRepository,
            GameImportService gameImportService,
            Clock clock
    ) {
        this.gameSyncStateRepository = gameSyncStateRepository;
        this.gameRepository = gameRepository;
        this.gameImportService = gameImportService;
        this.clock = clock;
    }

    public void ensureRecentGames(AppUserEntity appUser) {
        if (appUser.getLichessUsername() == null || appUser.getLichessUsername().isBlank()) {
            return;
        }
        String lichessUsername = normalize(appUser.getLichessUsername());
        ZonedDateTime now = ZonedDateTime.now(clock);

        GameSyncStateEntity state = gameSyncStateRepository
                .findByAppUser(appUser)
                .orElseGet(() -> createInitialState(appUser, lichessUsername, now));

        if ("RUNNING".equals(state.getStatus())) {
            log.info("Game sync already running: appUserId={}, lichessUsername={}", appUser.getId(), lichessUsername);
            return;
        }

        if (state.getLastSuccessAt() != null &&
                state.getLastSuccessAt().isAfter(now.minus(MIN_SYNC_INTERVAL))) {
            return;
        }

        sync(appUser, state, lichessUsername, now);
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
}
