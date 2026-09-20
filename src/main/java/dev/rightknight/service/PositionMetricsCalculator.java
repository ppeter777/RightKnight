package dev.rightknight.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.move.Move;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PositionMetricsCalculator {

    public PositionMetrics calculate(String fen) {

        Board board = new Board();

        board.loadFromFen(fen);

        List<Move> legalMoves = board.legalMoves();
        int legalMovesCount = legalMoves.size();

        int captureMovesCount = 0;
        int checkMovesCount = 0;
        int promotionMovesCount = 0;

        for (Move move : legalMoves) {

            boolean normalCapture =
                    board.getPiece(move.getTo()) != Piece.NONE;

            boolean enPassantCapture =
                    move.getTo() == board.getEnPassant();

            if (normalCapture || enPassantCapture) {
                captureMovesCount++;
            }

            if (!move.getPromotion().value().equals("NONE")) {
                promotionMovesCount++;
            }

            board.doMove(move);

            if (board.isKingAttacked()) {
                checkMovesCount++;
            }

            board.undoMove();
        }

        return new PositionMetrics(legalMovesCount, captureMovesCount, checkMovesCount, promotionMovesCount);
    }
}
