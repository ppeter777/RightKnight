package dev.rightknight.service.importer;

import dev.rightknight.model.AppUserEntity;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ImportedGameMapperTest {

    private final ImportedGameMapper mapper = new ImportedGameMapper();

    @Test
    void shouldMapWhiteWinForUser() {
        var user = user();

        var importedGame = new ImportedGame(
                "abc123",
                ZonedDateTime.parse("2026-06-01T10:00:00Z"),
                "blitz",
                true,
                "PeTeR777",
                2100,
                "opponent",
                2050,
                "white",
                "1. e4 e5",
                "Ruy Lopez",
                "C60",
                "3+2"
        );

        var entity = mapper.toEntity(importedGame, user, "peter777");

        assertThat(entity.getId()).isEqualTo("peter777:abc123");
        assertThat(entity.getUserId()).isEqualTo("peter777");
        assertThat(entity.getOwner()).isEqualTo(user);
        assertThat(entity.isWhite()).isTrue();
        assertThat(entity.getUserRating()).isEqualTo(2100);
        assertThat(entity.getOpponentId()).isEqualTo("opponent");
        assertThat(entity.getOpponentRating()).isEqualTo(2050);
        assertThat(entity.getScore()).isEqualTo(1.0f);
        assertThat(entity.getMode()).isEqualTo("blitz");
        assertThat(entity.isRated()).isTrue();
        assertThat(entity.getOpeningName()).isEqualTo("Ruy Lopez");
        assertThat(entity.getOpeningEco()).isEqualTo("C60");
        assertThat(entity.getClockLimit()).isEqualTo("3+2");
    }

    @Test
    void shouldMapBlackLossForUser() {
        var user = user();

        var importedGame = new ImportedGame(
                "def456",
                ZonedDateTime.parse("2026-06-02T10:00:00Z"),
                "rapid",
                true,
                "opponent",
                2000,
                "peter777",
                2100,
                "white",
                "1. d4 d5",
                "Queen's Pawn Game",
                "D00",
                "10+0"
        );

        var entity = mapper.toEntity(importedGame, user, "peter777");

        assertThat(entity.isWhite()).isFalse();
        assertThat(entity.getUserRating()).isEqualTo(2100);
        assertThat(entity.getOpponentId()).isEqualTo("opponent");
        assertThat(entity.getOpponentRating()).isEqualTo(2000);
        assertThat(entity.getScore()).isEqualTo(0.0f);
    }

    @Test
    void shouldMapDraw() {
        var user = user();

        var importedGame = new ImportedGame(
                "draw1",
                ZonedDateTime.parse("2026-06-03T10:00:00Z"),
                "classical",
                false,
                "peter777",
                2100,
                "opponent",
                2100,
                null,
                "1. Nf3 Nf6",
                null,
                null,
                null
        );

        var entity = mapper.toEntity(importedGame, user, "peter777");

        assertThat(entity.getScore()).isEqualTo(0.5f);
        assertThat(entity.isRated()).isFalse();
    }

    private AppUserEntity user() {
        var user = new AppUserEntity();
        user.setId(1L);
        user.setUsername("peter");
        user.setLichessUsername("peter777");
        return user;
    }
}
