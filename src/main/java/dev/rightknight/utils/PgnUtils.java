package dev.rightknight.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PgnUtils {

    public static String extractMoveText(String pgn) {
        if (pgn == null || pgn.isBlank()) {
            return "";
        }

        String[] pgnParts = pgn.split("\\R\\s*\\R", 2);

        String moveText = pgnParts.length == 2
                ? pgnParts[1]
                : pgn;

        return moveText
                .replaceAll("\\s+(1-0|0-1|1/2-1/2|\\*)\\s*$", "")
                .trim();
    }
    private static final Pattern PGN_COMMENT_PATTERN =
            Pattern.compile("\\{[^}]*}");

    public static String removeComments(String moveText) {
        if (moveText == null || moveText.isBlank()) {
            return "";
        }

        return PGN_COMMENT_PATTERN.matcher(moveText)
                .replaceAll(" ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final Pattern CLK_PATTERN =
            Pattern.compile("\\[%clk\\s+(\\d+):(\\d{2}):(\\d{2})]");

    public static List<Long> extractClockAfterMs(String moveText) {
        if (moveText == null || moveText.isBlank()) {
            return List.of();
        }

        List<Long> clocks = new ArrayList<>();
        Matcher matcher = CLK_PATTERN.matcher(moveText);

        while (matcher.find()) {
            long hours = Long.parseLong(matcher.group(1));
            long minutes = Long.parseLong(matcher.group(2));
            long seconds = Long.parseLong(matcher.group(3));

            clocks.add(((hours * 60 + minutes) * 60 + seconds) * 1000);
        }

        return clocks;
    }

}
