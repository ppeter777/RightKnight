package dev.rightknight.service.importer;

import chariot.model.Player;
import org.springframework.stereotype.Component;

@Component
public class LichessGameConverter {

    public ImportedGame toImportedGame(chariot.model.Game game) {
        var white = game.players().white();
        var black = game.players().black();

        String[] clockLimit = {null};
        game.clock().map(clock -> {
            int minutes = clock.initial() / 60;
            int increment = clock.increment();
            clockLimit[0] = minutes + "+" + increment;
            return clock;
        });

        String[] openingName = {null};
        String[] openingEco = {null};
        game.opening().map(opening -> {
            openingName[0] = opening.name();
            openingEco[0] = opening.eco();
            return opening;
        });

        String winner = null;
        if (game.winner().isPresent()) {
            winner = game.winner().get().name();
        }

        return new ImportedGame(
                game.id(),
                game.createdAt(),
                game.speed(),
                game.rated(),
                white.name(),
                extractRating(white),
                black.name(),
                extractRating(black),
                winner,
                game.pgn().orElse(""),
                openingName[0],
                openingEco[0],
                clockLimit[0]
        );
    }

    private int extractRating(Player player) {
        if (player instanceof Player.Account account) {
            return account.rating();
        }

        return 0;
    }
}
