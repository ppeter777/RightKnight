package dev.rightknight.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import dev.rightknight.model.GameMoveEntity;
import dev.rightknight.model.attack.AttackDelta;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import dev.rightknight.model.AttackTarget;

@Component
public class MoveContextCalculator {
    private final AttackGraphCalculator attacks = new AttackGraphCalculator();
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

        var snapshot = attacks.snapshot(board);
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
            List<AttackTarget> beforeTargets = DirectAttackDetector.targets(board, candidate.getFrom());
            board.doMove(candidate);
            boolean givesCheck;
            AttackDelta delta;
            List<AttackTarget> newTargets;
            try {
                // After the move, side to move is the opponent.
                givesCheck = board.isKingAttacked();
                delta = attacks.delta(snapshot, candidate, board);
                newTargets = DirectAttackDetector.targets(board, candidate.getTo()).stream()
                        .filter(target -> !beforeTargets.contains(target)).toList();
            } finally {
                board.undoMove();
            }
            features.put(candidate.toString(),
                    new CandidateMoveFeatures(capture, recapture, givesCheck, promotion, newTargets, delta));
        }
        return new MoveContext(previousCapture, previousCapture == null ? null : recaptures, snapshot, features);
    }
}
