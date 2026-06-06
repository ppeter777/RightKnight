package dev.rightknight.service.importer;

import java.time.ZonedDateTime;

public record ImportedGame(
        String lichessGameId,
        ZonedDateTime createdAt,
        String speed,
        boolean rated,
        String whiteName,
        int whiteRating,
        String blackName,
        int blackRating,
        String winner,
        String pgn,
        String openingName,
        String openingEco,
        String clockLimit
) {
}
