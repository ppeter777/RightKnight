package dev.c2.chesstools.calc;

import chariot.Client;
import chariot.model.Game;
import chariot.model.Player;

import java.time.ZonedDateTime;
import java.util.*;

public class Performance {

    public static Map<String, Integer> performanceCalc(String player, ZonedDateTime from, ZonedDateTime until) {
        var client = Client.basic();
        // Используем параметры вместо хардкода
        List<GameInfo> games = client.games().byUserId(player, p -> p.since(from).until(until))
                .stream()
                .map(game -> parseGame(game, player))
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

    private static GameInfo parseGame(Game game, String userId) {
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

    private static int calcPerf(List<GameInfo> games) {
        if (games.isEmpty()) return 0;
        List<Float> ratings = games.stream().map(g -> (float) g.oppRating).toList();
        float totalScore = (float) games.stream().mapToDouble(g -> g.score).sum();
        return performanceRating(ratings, totalScore);
    }

    public static int performanceRating(List<Float> opponentRatings, float score) {
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
