package dev.rightknight.service;

import org.springframework.stereotype.Service;

@Service
public class GameSyncRequestService {

    private final GameSyncService gameSyncService;
    private final GameSyncWorker gameSyncWorker;

    public GameSyncRequestService(
            GameSyncService gameSyncService,
            GameSyncWorker gameSyncWorker
    ) {
        this.gameSyncService = gameSyncService;
        this.gameSyncWorker = gameSyncWorker;
    }

}
