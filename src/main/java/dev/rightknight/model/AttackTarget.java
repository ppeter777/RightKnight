package dev.rightknight.model;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import java.util.Objects;

/** A geometric attack target, not a claim that capturing it is legal or profitable. */
public record AttackTarget(String square, Piece piece) {
    public AttackTarget {
        if (square == null || !square.matches("[a-h][1-8]")) {
            throw new IllegalArgumentException("Invalid target square: " + square);
        }
        Objects.requireNonNull(piece, "piece");
        if (piece == Piece.NONE || piece.getPieceType() == PieceType.KING) {
            throw new IllegalArgumentException("Attack targets must be non-king pieces");
        }
    }
}
