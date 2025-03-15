package dev.c2.chesstools.calc;

import chariot.Client;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Performance {
    public static Map<String, Integer> performanceCalc() {
        var client = Client.basic();
        ZonedDateTime zonedDateTimeFrom = ZonedDateTime.parse("2025-03-11T00:00:00+03:00");
        ZonedDateTime zonedDateTimeUntil = ZonedDateTime.parse("2025-03-15T22:00:00+03:00");
        var player = "PetrPesotskiy";
        var result = client.games().byUserId(player,   params -> params.since(zonedDateTimeFrom).until(zonedDateTimeUntil)).stream().map((game) -> {
            var white = game.players().white();
            var ratingWhite = white.toString().split(",")[5].substring(8);
            var black = game.players().black();
            var ratingBlack = black.toString().split(",")[5].substring(8);
            return List.of(white.name(), black.name(), ratingWhite, ratingBlack, game.winner().toString());
        }).toList();

        List<Float> opponentRatings = new ArrayList<>();
        List<Float> opponentRatingsBlack = new ArrayList<>();
        List<Float> opponentRatingsWhite = new ArrayList<>();
        var points = 0F;
        var pointsBlack = 0F;
        var pointsWhite = 0F;
        for (var game : result) {
            if (game.get(0).equals(player) && game.get(4).contains("white")) {
                points++;
                pointsWhite++;
                opponentRatings.add(Float.parseFloat(game.get(3)));
                opponentRatingsWhite.add(Float.parseFloat(game.get(3)));
            } else if (game.get(1).equals(player) && game.get(4).contains("black")) {
                points++;
                pointsBlack++;
                opponentRatings.add(Float.parseFloat(game.get(2)));
                opponentRatingsBlack.add(Float.parseFloat(game.get(2)));
            } else if (game.get(4).contains("Empty")) {
                points = points + 0.5F;
                if (game.get(0).equals(player)) {
                    pointsWhite += 0.5F;
                    opponentRatings.add(Float.parseFloat(game.get(3)));
                    opponentRatingsWhite.add(Float.parseFloat(game.get(3)));
                } else {
                    pointsBlack += 0.5F;
                    opponentRatings.add(Float.parseFloat(game.get(2)));
                    opponentRatingsBlack.add(Float.parseFloat(game.get(2)));
                }
            } else if (game.get(1).equals(player) && game.get(4).contains("white")) {
                opponentRatings.add(Float.parseFloat(game.get(2)));
                opponentRatingsBlack.add(Float.parseFloat(game.get(2)));
            } else if (game.get(0).equals(player) && game.get(4).contains("black")) {
                opponentRatings.add(Float.parseFloat(game.get(3)));
                opponentRatingsWhite.add(Float.parseFloat(game.get(3)));
            }
        }
        var oppRatingsSumBlack = 0;
        for (var blackRating : opponentRatingsBlack) {
            oppRatingsSumBlack += blackRating;
        }
        var blackAvgOppRating = oppRatingsSumBlack / opponentRatingsBlack.size();

        var oppRatingsSumWhite = 0;
        for (var whiteRating : opponentRatingsWhite) {
            oppRatingsSumWhite += whiteRating;
        }
        var whiteAvgOppRating = oppRatingsSumWhite / opponentRatingsWhite.size();

        var oppRatingsSum = 0;
        for (var rating : opponentRatings) {
            oppRatingsSum += rating;
        }
        var avgOppRating = oppRatingsSum / opponentRatings.size();

        Map<String, Integer> output = new HashMap<>();

        System.out.println("games played: " + result.size());
        System.out.println("games white: " + opponentRatingsWhite.size());
        System.out.println("games black: " + opponentRatingsBlack.size());
        System.out.println("opponents rating: " + avgOppRating);
        System.out.println("opponents white rating: " + whiteAvgOppRating);
        System.out.println("opponents black rating: " + blackAvgOppRating);
        System.out.println("overall: " + performance_rating(opponentRatings, points));
        System.out.println("white: " + performance_rating(opponentRatingsWhite, pointsWhite));
        System.out.println("black: " + performance_rating(opponentRatingsBlack, pointsBlack));
        output.put("games played: ", result.size());
        output.put("games white: " , opponentRatingsWhite.size());
        output.put("games black: ", opponentRatingsBlack.size());
        output.put("opponents rating: ", avgOppRating);
        output.put("opponents white rating: ", whiteAvgOppRating);
        output.put("opponents black rating: ", blackAvgOppRating);
        output.put("performance overall", performance_rating(opponentRatings, points));
        output.put("performance white", performance_rating(opponentRatingsWhite, pointsWhite));
        output.put("performance black", performance_rating(opponentRatingsBlack, pointsBlack));
        return output;
    }

    public static Float expected_score(List<Float> opponent_ratings, Float own_rating) {
        var output = 0F;
        for (var opponent_rating : opponent_ratings) {
            output += 1 / (1 + Math.pow(10, ((opponent_rating - own_rating) / 400)));
        }
        return output;
    }

    public static Integer performance_rating(List<Float> opponent_ratings, Float score) {
        var lo = 0F;
        var hi = 4000F;
        var mid = 0F;
        while (hi - lo > 0.001) {
            mid = (lo + hi) / 2;
            var expected = expected_score(opponent_ratings, mid);
            if (expected < score) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return Math.round(mid);
    }


}
