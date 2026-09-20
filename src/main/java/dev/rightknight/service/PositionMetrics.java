package dev.rightknight.service;

public record PositionMetrics(
        int legalMovesCount,
        int captureMovesCount,
        int checkMovesCount,
        int promotionMovesCount
) {}
