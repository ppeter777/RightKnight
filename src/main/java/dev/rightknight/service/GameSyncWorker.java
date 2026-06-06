package dev.rightknight.service;

import dev.rightknight.repository.AppUserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class GameSyncWorker {

    private final AppUserRepository appUserRepository;
    private final GameSyncService gameSyncService;

    public GameSyncWorker(
            AppUserRepository appUserRepository,
            GameSyncService gameSyncService
    ) {
        this.appUserRepository = appUserRepository;
        this.gameSyncService = gameSyncService;
    }

    @Async("gameSyncExecutor")
    public void syncUserGames(Long appUserId) {
        appUserRepository.findById(appUserId)
                .ifPresent(gameSyncService::syncNow);
    }
}
