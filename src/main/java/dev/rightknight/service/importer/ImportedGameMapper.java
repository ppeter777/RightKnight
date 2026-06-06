package dev.rightknight.service.importer;

import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.GameEntity;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class ImportedGameMapper {

    public GameEntity toEntity(ImportedGame game, AppUserEntity appUser, String lichessUsername) {
        String normalizedUserId = lichessUsername.trim().toLowerCase(Locale.ROOT);

        var entity = new GameEntity();

        entity.setId(normalizedUserId + ":" + game.lichessGameId());
        entity.setUserId(normalizedUserId);
        entity.setOwner(appUser);

        entity.setCreatedAt(game.createdAt());
        entity.setMode(game.speed());
        entity.setRated(game.rated());

        boolean isWhite = game.whiteName().equalsIgnoreCase(normalizedUserId);
        entity.setWhite(isWhite);

        entity.setUserRating(isWhite ? game.whiteRating() : game.blackRating());
        entity.setOpponentId(isWhite ? game.blackName() : game.whiteName());
        entity.setOpponentRating(isWhite ? game.blackRating() : game.whiteRating());

        float score = 0.5f;
        if (game.winner() != null) {
            boolean whiteWon = game.winner().equalsIgnoreCase("white");
            score = (isWhite == whiteWon) ? 1.0f : 0.0f;
        }
        entity.setScore(score);

        entity.setPgn(game.pgn());
        entity.setOpeningName(game.openingName());
        entity.setOpeningEco(game.openingEco());
        entity.setClockLimit(game.clockLimit());

        return entity;
    }
}
