package dev.rightknight.service;

import chariot.Client;
import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.GameEntity;
import dev.rightknight.repository.GameRepository;
import dev.rightknight.service.importer.ImportedGameMapper;
import dev.rightknight.service.importer.LichessGameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class LichessGameImportService implements GameImportService {

    private static final Logger log = LoggerFactory.getLogger(LichessGameImportService.class);

    private final GameRepository gameRepository;

    private final ImportedGameMapper importedGameMapper;

    private final LichessGameConverter lichessGameConverter;

    public LichessGameImportService(
            GameRepository gameRepository,
            ImportedGameMapper importedGameMapper,
            LichessGameConverter lichessGameConverter
    ) {
        this.gameRepository = gameRepository;
        this.importedGameMapper = importedGameMapper;
        this.lichessGameConverter = lichessGameConverter;
    }

    @Override
    public int importGames(AppUserEntity appUser, ZonedDateTime from, ZonedDateTime until) {
        String lichessUsername = normalize(appUser.getLichessUsername());

        log.info(
                "Loading games from Lichess: appUserId={}, lichessUsername={}, from={}, until={}",
                appUser.getId(), lichessUsername, from, until
        );

        var client = Client.basic();

        List<GameEntity> games = client.games()
                .byUserId(lichessUsername, params -> params
                        .since(from)
                        .until(until)
                        .pgn(true)
                        .opening(true)
                )
                .stream()
                .map(lichessGameConverter::toImportedGame)
                .map(importedGame -> importedGameMapper.toEntity(importedGame, appUser, lichessUsername))
                .toList();

        gameRepository.saveAll(games);

        log.info(
                "Loaded games from Lichess: appUserId={}, lichessUsername={}, games={}",
                appUser.getId(), lichessUsername, games.size()
        );

        return games.size();
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
