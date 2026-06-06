package dev.rightknight.service;

import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.GameEntity;
import dev.rightknight.model.GameSyncStateEntity;
import dev.rightknight.repository.GameRepository;
import dev.rightknight.repository.GameSyncStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameSyncServiceTest {

    @Mock
    private GameSyncStateRepository gameSyncStateRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameImportService gameImportService;

    private GameSyncService gameSyncService;

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-06-05T12:00:00Z"),
            ZoneId.of("UTC")
    );

    @BeforeEach
    void setUp() {
        gameSyncService = new GameSyncService(
                gameSyncStateRepository,
                gameRepository,
                gameImportService,
                fixedClock
        );
    }

    @Test
    void shouldNotImportGamesWhenLichessUsernameIsBlank() {
        var user = new AppUserEntity();
        user.setId(1L);
        user.setLichessUsername(" ");

        boolean result = gameSyncService.shouldSync(user);

        assertThat(result).isFalse();

        verifyNoInteractions(gameSyncStateRepository);
        verifyNoInteractions(gameRepository);
        verifyNoInteractions(gameImportService);
    }

    @Test
    void shouldImportLastYearForNewUser() {
        var user = user("PeTeR777");

        when(gameSyncStateRepository.findByAppUser(user)).thenReturn(Optional.empty());
        when(gameSyncStateRepository.save(any(GameSyncStateEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(gameRepository.findFirstByUserIdIgnoreCaseOrderByCreatedAtDesc("peter777"))
                .thenReturn(Optional.empty());
        when(gameImportService.importGames(any(), any(), any())).thenReturn(10);

        gameSyncService.syncNow(user);

        ArgumentCaptor<ZonedDateTime> fromCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        ArgumentCaptor<ZonedDateTime> untilCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);

        verify(gameImportService).importGames(
                eq(user),
                fromCaptor.capture(),
                untilCaptor.capture()
        );

        assertThat(fromCaptor.getValue()).isEqualTo(ZonedDateTime.now(fixedClock).minusYears(1));
        assertThat(untilCaptor.getValue()).isEqualTo(ZonedDateTime.now(fixedClock));
    }

    @Test
    void shouldNotImportWhenLastSuccessfulSyncIsRecent() {
        var user = user("peter777");

        var state = new GameSyncStateEntity();
        state.setAppUser(user);
        state.setLichessUsername("peter777");
        state.setStatus("IDLE");
        state.setLastSuccessAt(ZonedDateTime.now(fixedClock).minusMinutes(5));

        when(gameSyncStateRepository.findByAppUser(user)).thenReturn(Optional.of(state));

        boolean result = gameSyncService.shouldSync(user);

        assertThat(result).isFalse();

        verifyNoInteractions(gameImportService);
        verify(gameRepository, never()).findFirstByUserIdIgnoreCaseOrderByCreatedAtDesc(anyString());
    }

    @Test
    void shouldUseOneDayOverlapWhenNewestGameExists() {
        var user = user("peter777");

        var state = new GameSyncStateEntity();
        state.setAppUser(user);
        state.setLichessUsername("peter777");
        state.setStatus("IDLE");
        state.setLastSuccessAt(ZonedDateTime.now(fixedClock).minusHours(1));

        var newestGame = new GameEntity();
        newestGame.setId("peter777:abc123");
        newestGame.setCreatedAt(ZonedDateTime.parse("2026-06-01T10:00:00Z"));

        when(gameSyncStateRepository.findByAppUser(user)).thenReturn(Optional.of(state));
        when(gameRepository.findFirstByUserIdIgnoreCaseOrderByCreatedAtDesc("peter777"))
                .thenReturn(Optional.of(newestGame));
        when(gameImportService.importGames(any(), any(), any())).thenReturn(3);

        gameSyncService.syncNow(user);

        ArgumentCaptor<ZonedDateTime> fromCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);

        verify(gameImportService).importGames(
                eq(user),
                fromCaptor.capture(),
                eq(ZonedDateTime.now(fixedClock))
        );

        assertThat(fromCaptor.getValue()).isEqualTo(ZonedDateTime.parse("2026-05-31T10:00:00Z"));
    }

    @Test
    void shouldMarkSyncAsFailedWhenImportThrowsException() {
        var user = user("peter777");

        var state = new GameSyncStateEntity();
        state.setAppUser(user);
        state.setLichessUsername("peter777");
        state.setStatus("IDLE");
        state.setLastSuccessAt(ZonedDateTime.now(fixedClock).minusHours(1));

        when(gameSyncStateRepository.findByAppUser(user)).thenReturn(Optional.of(state));
        when(gameRepository.findFirstByUserIdIgnoreCaseOrderByCreatedAtDesc("peter777"))
                .thenReturn(Optional.empty());
        when(gameImportService.importGames(any(), any(), any()))
                .thenThrow(new RuntimeException("Lichess unavailable"));

        gameSyncService.syncNow(user);

        assertThat(state.getStatus()).isEqualTo("FAILED");
        assertThat(state.getLastError()).isEqualTo("Lichess unavailable");
        verify(gameSyncStateRepository, atLeastOnce()).save(state);
    }

    @Test
    void shouldReturnTrueWhenStateDoesNotExist() {
        var user = user("peter777");

        when(gameSyncStateRepository.findByAppUser(user)).thenReturn(Optional.empty());

        boolean result = gameSyncService.shouldSync(user);

        assertThat(result).isTrue();

        verifyNoInteractions(gameImportService);
        verifyNoInteractions(gameRepository);
    }

    private AppUserEntity user(String lichessUsername) {
        var user = new AppUserEntity();
        user.setId(1L);
        user.setUsername("peter");
        user.setLichessUsername(lichessUsername);
        return user;
    }
}
