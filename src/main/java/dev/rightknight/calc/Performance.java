package dev.rightknight.calc;

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
        List<GameEntity> dbGames = gameRepository.findAllByUserIdIgnoreCaseAndCreatedAtBetween(player, from, until);

        log.info(
                "Performance database lookup: player={}, from={}, until={}, dbGames={}",
                player, from, until, dbGames.size()
        );

        return dbGames;
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

}
