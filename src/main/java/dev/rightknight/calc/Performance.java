package dev.rightknight.calc;

import chariot.Client;
import chariot.model.Game;
import chariot.model.Player;
import dev.rightknight.model.GameEntity;
import dev.rightknight.repository.GameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.ZonedDateTime;
import java.util.*;

@Service
public class Performance {

    private static final Logger log = LoggerFactory.getLogger(Performance.class);

    @Autowired
    private GameRepository gameRepository;

    public Map<String, Integer> performanceCalc(String player, ZonedDateTime from, ZonedDateTime until, String mode, Boolean rated) {
        String normalizedPlayer = normalizePlayer(player);

        // 1. Получаем все игры за период (из БД или API)
        List<GameEntity> allGames = getGames(normalizedPlayer, from, until);

        // 2. Фильтруем их по выбранным в форме параметрам
        List<GameEntity> filteredGames = allGames.stream()
                .filter(g -> isMatch(g, mode, rated))
                .toList();

        log.info(
                "Performance calculation: player={}, from={}, until={}, mode={}, rated={}, allGames={}, filteredGames={}",
                normalizedPlayer, from, until, mode, rated, allGames.size(), filteredGames.size()
        );

        // 3. Отдаем отфильтрованный список Entity в расчет
        return calculateResults(filteredGames);
    }


    private List<GameEntity> getGames(String player, ZonedDateTime from, ZonedDateTime until) {
        // 1. Ищем в БД
        List<GameEntity> dbGames = gameRepository.findAllByUserIdIgnoreCaseAndCreatedAtBetween(player, from, until);

        log.info(
                "Performance cache lookup: player={}, from={}, until={}, dbGames={}",
                player, from, until, dbGames.size()
        );

        if (!dbGames.isEmpty()) {
            return dbGames;
        }

        // 2. Если в базе пусто, качаем
        log.info("Performance cache miss. Loading games from Lichess: player={}", player);
        List<GameEntity> apiGames = fetchGamesFromLichess(player, from, until);

        long gamesWithOpponentRating = apiGames.stream()
                .filter(g -> g.getOpponentRating() > 0)
                .count();

        log.info(
                "Lichess games loaded: player={}, apiGames={}, gamesWithOpponentRating={}",
                player, apiGames.size(), gamesWithOpponentRating
        );

        // 3. Сохраняем скачанное
        saveGames(apiGames, player);

        return apiGames;
    }

    private boolean isMatch(GameEntity game, String mode, Boolean rated) {
        // Проверяем режим игры (blitz, rapid и т.д.)
        // mode.equals("all") позволяет пропустить фильтрацию, если выбрано "All Modes"
        boolean modeMatches = mode.equals("all") || game.getMode().equalsIgnoreCase(mode);

        // Проверяем тип игры (Rated/Casual)
        // Если rated == null (All Games), фильтр не применяется
        boolean ratedMatches = (rated == null) || (game.isRated() == rated);

        return modeMatches && ratedMatches;
    }

    private List<GameEntity> fetchGamesFromLichess(String player, ZonedDateTime from, ZonedDateTime until) {
        var client = Client.basic();
        return client.games().byUserId(player, params -> params
                        .since(from)
                        .until(until)
                        .pgn(true) // Чтобы скачивался текст партий
                        .opening(true) // Чтобы были дебюты
                ).stream()
                .map(g -> mapToEntity(g, player))
                .toList();
    }

    private void saveGames(List<GameEntity> apiGames, String player) {
        if (apiGames.isEmpty()) {
            log.info("No games to save: player={}", player);
            return;
        }

        // Spring Data JPA сам поймет по ID (Primary Key),
        // что если игра уже есть - ее надо обновить, если нет - создать.
        gameRepository.saveAll(apiGames);
        log.info("Saved games to database: player={}, games={}", player, apiGames.size());
    }

    private GameInfo parseGame(Game game, String userId) {
        var white = game.players().white();
        var black = game.players().black();

        boolean isWhite = white.name().equalsIgnoreCase(userId);
        var opponent = isWhite ? black : white;

        // Безопасно достаем рейтинг через паттерн-матчинг (Java 17+)
        int oppRating = (opponent instanceof Player.Account checked)
                ? checked.rating()
                : 0;

        float score = 0.5f;
        if (game.winner().isPresent()) {
            boolean whiteWon = game.winner().get().name().equals("white");
            score = (isWhite == whiteWon) ? 1.0f : 0.0f;
        }

        return new GameInfo(isWhite, oppRating, score);
    }

    private GameEntity mapToEntity(chariot.model.Game game, String userId) {
        String normalizedUserId = normalizePlayer(userId);

        var entity = new GameEntity();
        entity.setId(normalizedUserId + ":" + game.id());
        entity.setUserId(normalizedUserId);
        entity.setCreatedAt(game.createdAt());
        entity.setMode(game.speed());
        entity.setRated(game.rated());

        // Определяем, за какой цвет играл наш пользователь
        boolean isWhite = game.players().white().name().equalsIgnoreCase(normalizedUserId);
        entity.setWhite(isWhite);

        game.clock().map(c -> {
            int minutes = c.initial() / 60;
            int increment = c.increment();
            entity.setClockLimit(minutes + "+" + increment);
            return c;
        });

        // Достаем оппонента и его рейтинг
        var opponent = isWhite ? game.players().black() : game.players().white();
        entity.setOpponentId(opponent.name());

        // В версии 0.1.21 используем Account для рейтинга
        if (opponent instanceof chariot.model.Player.Account account) {
            entity.setOpponentRating(account.rating());
        } else {
            entity.setOpponentRating(0);
        }

        // Считаем результат (score)
        float score = 0.5f;
        if (game.winner().isPresent()) {
            boolean whiteWon = game.winner().get().name().equals("white");
            score = (isWhite == whiteWon) ? 1.0f : 0.0f;
        }
        entity.setScore(score);

        // Новые поля: PGN и Дебюты
        entity.setPgn(game.pgn().orElse(""));

        // opening() возвращает Optional, поэтому используем map/orElse
        game.opening().map(o -> {
            entity.setOpeningName(o.name());
            entity.setOpeningEco(o.eco());
            return o;
        });

        return entity;
    }

    private Map<String, Integer> calculateResults(List<GameEntity> games) {
        // Разделяем игры по цветам для детального расчета
        var whiteGames = games.stream().filter(GameEntity::isWhite).toList();
        var blackGames = games.stream().filter(g -> !g.isWhite()).toList();

        Map<String, Integer> results = new LinkedHashMap<>();

        // Считаем общее
        results.put("performanceBoth", calcSpecificPerf(games));
        results.put("gamesPlayed", games.size());

        // Считаем белыми
        results.put("performanceWhite", calcSpecificPerf(whiteGames));
        results.put("gamesWhite", whiteGames.size());

        // Считаем черными
        results.put("performanceBlack", calcSpecificPerf(blackGames));
        results.put("gamesBlack", blackGames.size());

        return results;
    }

    // Вспомогательный метод, чтобы не дублировать логику подготовки списков
    private int calcSpecificPerf(List<GameEntity> games) {
        if (games.isEmpty()) return 0;

        // Оставляем только игры, где у соперника есть рейтинг
        var validGames = games.stream()
                .filter(g -> g.getOpponentRating() > 0)
                .toList();

        if (validGames.isEmpty()) return 0;

        List<Float> ratings = validGames.stream()
                .map(g -> (float) g.getOpponentRating())
                .toList();

        float totalScore = (float) validGames.stream()
                .mapToDouble(GameEntity::getScore)
                .sum();

        // Старый добрый метод с бинарным поиском
        return performanceRating(ratings, totalScore);
    }

    public int performanceRating(List<Float> opponentRatings, float score) {
        float lo = 0, hi = 4000;
        for (int i = 0; i < 20; i++) { // 20 итераций достаточно для точности
            float mid = (lo + hi) / 2;
            float expected = 0;
            for (float opp : opponentRatings) {
                expected += 1 / (1 + Math.pow(10, (opp - mid) / 400.0));
            }
            if (expected < score) lo = mid; else hi = mid;
        }
        return Math.round(lo);
    }

    private String normalizePlayer(String player) {
        return player.trim().toLowerCase(Locale.ROOT);
    }

    record GameInfo(boolean isWhite, int oppRating, float score) {}
}
