package dev.rightknight.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.move.Move;

final class CaptureDetector {
    private CaptureDetector() {}

    /** The caller must supply a legal move in this board position. */
    static boolean isCapture(Board board, Move move) {
        return board.getPiece(move.getTo()) != Piece.NONE
                || (board.getPiece(move.getFrom()).getPieceType() == PieceType.PAWN
                && move.getFrom().getFile() != move.getTo().getFile()
                && move.getTo() == board.getEnPassant());
    }
}
