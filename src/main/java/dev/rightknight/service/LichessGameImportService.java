package dev.rightknight.service;

import chariot.Client;
import chariot.model.Game;
import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.GameEntity;
import dev.rightknight.repository.GameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class LichessGameImportService {

    private static final Logger log = LoggerFactory.getLogger(LichessGameImportService.class);

    private final GameRepository gameRepository;

    public LichessGameImportService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

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
                .map(game -> mapToEntity(game, appUser, lichessUsername))
                .toList();

        gameRepository.saveAll(games);

        log.info(
                "Loaded games from Lichess: appUserId={}, lichessUsername={}, games={}",
                appUser.getId(), lichessUsername, games.size()
        );

        return games.size();
    }

    private GameEntity mapToEntity(chariot.model.Game game, AppUserEntity appUser, String normalizedUserId) {
        var entity = new GameEntity();

        entity.setId(normalizedUserId + ":" + game.id());
        entity.setUserId(normalizedUserId);
        entity.setOwner(appUser);

        entity.setCreatedAt(game.createdAt());
        entity.setMode(game.speed());
        entity.setRated(game.rated());

        boolean isWhite = game.players().white().name().equalsIgnoreCase(normalizedUserId);
        entity.setWhite(isWhite);

        var userPlayer = isWhite ? game.players().white() : game.players().black();
        if (userPlayer instanceof chariot.model.Player.Account account) {
            entity.setUserRating(account.rating());
        }

        var opponent = isWhite ? game.players().black() : game.players().white();
        entity.setOpponentId(opponent.name());

        if (opponent instanceof chariot.model.Player.Account account) {
            entity.setOpponentRating(account.rating());
        } else {
            entity.setOpponentRating(0);
        }

        float score = 0.5f;
        if (game.winner().isPresent()) {
            boolean whiteWon = game.winner().get().name().equals("white");
            score = (isWhite == whiteWon) ? 1.0f : 0.0f;
        }
        entity.setScore(score);

        game.clock().map(c -> {
            int minutes = c.initial() / 60;
            int increment = c.increment();
            entity.setClockLimit(minutes + "+" + increment);
            return c;
        });

        entity.setPgn(game.pgn().orElse(""));

        game.opening().map(opening -> {
            entity.setOpeningName(opening.name());
            entity.setOpeningEco(opening.eco());
            return opening;
        });

        return entity;
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
