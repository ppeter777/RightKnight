package dev.rightknight.service;

import com.github.bhlangonijr.chesslib.*;
import dev.rightknight.model.AttackTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Direct geometric attacks by one piece. Pins and tactical soundness are deliberately not evaluated. */
final class DirectAttackDetector {
    private DirectAttackDetector() {}

    static List<AttackTarget> targets(Board board, Square from) {
        Piece attacker = board.getPiece(from);
        if (attacker == Piece.NONE) {
            throw new IllegalArgumentException("No attacker at " + from);
        }
        List<AttackTarget> result = new ArrayList<>();
        for (Square to : Square.values()) {
            if (to == Square.NONE) continue;
            Piece target = board.getPiece(to);
            if (target == Piece.NONE || target.getPieceSide() == attacker.getPieceSide()
                    || target.getPieceType() == PieceType.KING) continue;
            if (attacks(board, from, to, attacker)) {
                result.add(new AttackTarget(to.name().toLowerCase(Locale.ROOT), target));
            }
        }
        return List.copyOf(result);
    }

    static boolean attacks(Board board, Square from, Square to, Piece piece) {
        if (from == to) return false;
        int fx = from.getFile().ordinal(), fy = from.getRank().ordinal();
        int dx = to.getFile().ordinal() - fx, dy = to.getRank().ordinal() - fy;
        int ax = Math.abs(dx), ay = Math.abs(dy);
        return switch (piece.getPieceType()) {
            case PAWN -> ax == 1 && dy == (piece.getPieceSide() == Side.WHITE ? 1 : -1);
            case KNIGHT -> ax * ay == 2;
            case KING -> Math.max(ax, ay) == 1;
            case BISHOP -> ax == ay && clearLine(board, fx, fy, dx, dy);
            case ROOK -> (dx == 0 || dy == 0) && clearLine(board, fx, fy, dx, dy);
            case QUEEN -> (ax == ay || dx == 0 || dy == 0) && clearLine(board, fx, fy, dx, dy);
            default -> false;
        };
    }

    private static boolean clearLine(Board board, int x, int y, int dx, int dy) {
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        for (int step = 1; step < steps; step++) {
            String square = "" + (char) ('A' + x + step * Integer.signum(dx))
                    + (y + step * Integer.signum(dy) + 1);
            if (board.getPiece(Square.valueOf(square)) != Piece.NONE) return false;
        }
        return true;
    }
}
