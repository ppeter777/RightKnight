package dev.rightknight.service.admin;

import dev.rightknight.model.AppUserEntity;
import dev.rightknight.model.GameEntity;
import dev.rightknight.repository.AppUserRepository;
import dev.rightknight.repository.GameRepository;
import org.springframework.stereotype.Service;

//import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.data.domain.PageRequest;

/**
 * Service to provide game summaries for the admin UI. This class
 * aggregates information about games and allows filtering by owner,
 * Lichess username, date range and result.
 */
@Service
public class AdminGameService {
    private final GameRepository gameRepository;
    private final AppUserRepository userRepository;

    public AdminGameService(GameRepository gameRepository, AppUserRepository userRepository) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
    }

    /** DTO summarising a game record for the list view. */
    public static class GameSummary {
        public String id;
        public Long ownerId;
        public String ownerUsername;
        public String ownerEmail;
        public String ownerLichessUsername;
        public String white;
        public String black;
        public String result;
        public ZonedDateTime gameDate;
        public ZonedDateTime importedDate;
        public long movesCount;
    }

    /**
     * Builds a summary DTO for a game.
     *
     * @param game the game entity
     * @return summary information
     */
    public GameSummary buildSummary(GameEntity game) {
        GameSummary summary = new GameSummary();
        summary.id = game.getId();

        if (game.getOwner() != null) {
            summary.ownerId = game.getOwner().getId();
            summary.ownerUsername = game.getOwner().getUsername();
            summary.ownerEmail = game.getOwner().getEmail();
            summary.ownerLichessUsername = game.getOwner().getLichessUsername();
        }

        if (game.isWhite()) {
            summary.white = game.getUserId();
            summary.black = game.getOpponentId();
        } else {
            summary.white = game.getOpponentId();
            summary.black = game.getUserId();
        }

        summary.result = toResult(game);
        summary.gameDate = game.getCreatedAt();
        summary.importedDate = game.getCreatedAt();
        summary.movesCount = gameRepository.countMovesByGame(game);
        return summary;
    }

    /**
     * Finds games with optional filtering by owner ID, lichess username,
     * date range and result. If a filter value is {@code null}, the
     * corresponding constraint is ignored.
     */
    public List<GameSummary> findGames(
            Long ownerId,
            String lichessUsername,
            ZonedDateTime dateFrom,
            ZonedDateTime dateTo,
            String resultFilter
    ) {
        // Retrieve all games first. For a small dataset this is acceptable
        // and avoids adding complex queries to existing repositories.
        List<GameEntity> allGames = StreamSupport.stream(gameRepository.findAll().spliterator(), false)
                .toList();
        return allGames.stream()
                .filter(g -> {
                    boolean matches = true;
                    if (ownerId != null) {
                        matches &= (g.getOwner() != null && ownerId.equals(g.getOwner().getId()));
                    }
                    if (lichessUsername != null && !lichessUsername.isBlank()) {
                        AppUserEntity owner = g.getOwner();
                        if (owner != null && owner.getLichessUsername() != null) {
                            matches &= owner.getLichessUsername().toLowerCase().contains(lichessUsername.toLowerCase());
                        } else {
                            matches = false;
                        }
                    }
                    ZonedDateTime createdAt = g.getCreatedAt();

                    if (dateFrom != null) {
                        matches &= createdAt != null && !createdAt.isBefore(dateFrom);
                    }

                    if (dateTo != null) {
                        matches &= createdAt != null && !createdAt.isAfter(dateTo);
                    }
                    if (resultFilter != null && !resultFilter.isBlank()) {
                        matches &= toResult(g).equalsIgnoreCase(resultFilter);
                    }
                    return matches;
                })
                .map(this::buildSummary)
                .collect(Collectors.toList());
    }

    private String toResult(GameEntity game) {
        float score = game.getScore();

        if (score == 0.5f) {
            return "1/2-1/2";
        }

        if (game.isWhite()) {
            return score == 1.0f ? "1-0" : "0-1";
        }

        return score == 1.0f ? "0-1" : "1-0";
    }

    public static class GamePage {
        public List<GameSummary> games;
        public int page;
        public int size;
        public long total;
        public int totalPages;
    }
    public GamePage findGamesPage(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 10), 100);

        var pageable = PageRequest.of(safePage, safeSize);

        List<GameSummary> games = gameRepository
                .findAllByOrderByCreatedAtDesc(pageable)
                .stream()
                .map(this::buildSummary)
                .toList();

        long total = gameRepository.count();

        GamePage result = new GamePage();
        result.games = games;
        result.page = safePage;
        result.size = safeSize;
        result.total = total;
        result.totalPages = (int) Math.ceil((double) total / safeSize);

        return result;
    }
}

