package dev.rightknight.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import dev.rightknight.model.GameMoveEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MoveContextCalculator {
    public MoveContext calculate(GameMoveEntity move, GameMoveEntity previousMove) {
        Board board = new Board();
        board.loadFromFen(move.getFenBefore());

        // ply 1 has no predecessor in the imported game. Missing later history is unknown.
        Boolean previousCapture = move.getPly() == 1 ? Boolean.FALSE : null;
        Square recaptureSquare = Square.NONE;
        if (previousMove != null) {
            if (previousMove.getPly() + 1 != move.getPly()
                    || !move.getFenBefore().equals(previousMove.getFenAfter())) {
                throw new IllegalArgumentException("Previous move does not precede ply " + move.getPly());
            }
            Board previousBoard = new Board();
            previousBoard.loadFromFen(previousMove.getFenBefore());
            Move previous = previousBoard.legalMoves().stream()
                    .filter(candidate -> candidate.toString().equals(previousMove.getUci()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Illegal previous move"));
            previousCapture = CaptureDetector.isCapture(previousBoard, previous);
            if (previousCapture) {
                recaptureSquare = previous.getTo();
            }
        }

        int recaptures = 0;
        Map<String, CandidateMoveFeatures> features = new LinkedHashMap<>();
        for (Move candidate : board.legalMoves()) {
            boolean capture = CaptureDetector.isCapture(board, candidate);
            Boolean recapture = previousCapture == null ? null
                    : previousCapture && capture && candidate.getTo() == recaptureSquare;
            if (Boolean.TRUE.equals(recapture)) {
                recaptures++;
            }
            boolean promotion = candidate.getPromotion() != Piece.NONE;
            board.doMove(candidate);
            boolean givesCheck;
            try {
                // After the move, side to move is the opponent.
                givesCheck = board.isKingAttacked();
            } finally {
                board.undoMove();
            }
            features.put(candidate.toString(),
                    new CandidateMoveFeatures(capture, recapture, givesCheck, promotion));
        }
        return new MoveContext(previousCapture, previousCapture == null ? null : recaptures, features);
    }
}
