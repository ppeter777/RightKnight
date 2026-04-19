package dev.rightknight.calc;

import chariot.Client;
import chariot.model.Game;
import chariot.model.Player;
import dev.rightknight.model.GameEntity;
import dev.rightknight.repository.GameRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.*;

@Service
public class Performance {

    @Autowired
    private GameRepository gameRepository;

    public Map<String, Integer> performanceCalc(
            String player,
            ZonedDateTime from,
            ZonedDateTime until,
            String mode,    // "blitz", "rapid", "bullet" и т.д.
            Boolean rated   // true для рейтинговых, null для всех
    ) {
        var client = Client.basic();

        var games = client.games().byUserId(player, params -> {
                    params.since(from).until(until);
                    if (mode != null && !mode.equals("all")) {
                        // Превращаем строку в нужный Enum или тип для chariot
                        params.perfType(chariot.model.Enums.PerfType.valueOf(mode));
                    }
                    if (rated != null) {
                        params.rated(rated);
                    }
                }).stream()
                .map(game -> {
                    parseGame(game, player);
                    var info = parseGame(game, player);
                    if (!gameRepository.existsById(game.id())) {
                        var entity = new GameEntity();
                        entity.setId(game.id());
                        entity.setUserId(player);
                        entity.setWhite(info.isWhite()); // Вот тут проверь имя метода (setWhite)
                        entity.setScore(info.score());
                        entity.setOpponentRating(info.oppRating());
                        entity.setCreatedAt(game.createdAt());
                        entity.setMode(game.speed()); // Не забудь про mode!
                        entity.setRated(game.rated());       // И про rated!
// Убедись, что opponentId тоже заполнен
                        entity.setOpponentId(info.isWhite() ? game.players().black().name() : game.players().white().name());
                        gameRepository.save(entity);
                    }
                    return info;
                })
                .toList();


        var whiteGames = games.stream().filter(g -> g.isWhite).toList();
        var blackGames = games.stream().filter(g -> !g.isWhite).toList();

        Map<String, Integer> res = new LinkedHashMap<>();

        res.put("performanceBoth", calcPerf(games));
        res.put("performanceWhite", calcPerf(whiteGames));
        res.put("performanceBlack", calcPerf(blackGames));

        // В конце метода performanceCalc перед return output:
        res.put("gamesPlayed", games.size());
        res.put("gamesWhite", whiteGames.size());
        res.put("gamesBlack", blackGames.size());


        res.forEach((k, v) -> System.out.println(k + ": " + v));
        return res;
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

    private int calcPerf(List<GameInfo> games) {
        // Отфильтровываем игры, где рейтинг соперника не указан (0)
        List<GameInfo> validGames = games.stream()
                .filter(g -> g.oppRating() > 0)
                .toList();

        if (validGames.isEmpty()) return 0;

        List<Float> ratings = validGames.stream()
                .map(g -> (float) g.oppRating())
                .toList();

        float totalScore = (float) validGames.stream()
                .mapToDouble(g -> g.score())
                .sum();

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

    record GameInfo(boolean isWhite, int oppRating, float score) {}
}
