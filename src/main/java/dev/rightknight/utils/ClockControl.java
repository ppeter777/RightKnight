package dev.rightknight.utils;

public record ClockControl(long initialMs, long incrementMs) {

    public static ClockControl parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Clock limit is empty");
        }

        String[] parts = value.split("\\+");

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Unsupported clock limit format: " + value
            );
        }

        long initialMinutes = Long.parseLong(parts[0]);
        long incrementSeconds = Long.parseLong(parts[1]);

        return new ClockControl(
                initialMinutes * 60_000,
                incrementSeconds * 1_000
        );
    }
}
