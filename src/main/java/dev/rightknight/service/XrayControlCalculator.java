package dev.rightknight.service;

import com.github.bhlangonijr.chesslib.PieceType;
import dev.rightknight.model.attack.AttackSnapshot.PieceState;
import dev.rightknight.model.attack.XrayControl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded geometric batteries only; no enemy/non-compatible blockers and no legality inference. */
final class XrayControlCalculator {
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    static List<XrayControl> calculate(Map<String, PieceState> pieces) {
        Map<String, String> idsBySquare = new HashMap<>();
        pieces.forEach((id, piece) -> idsBySquare.put(piece.square(), id));
        List<XrayControl> result = new ArrayList<>();
        for (var source : pieces.entrySet()) {
            PieceState piece = source.getValue();
            for (int[] direction : DIRECTIONS) {
                boolean diagonal = direction[0] != 0 && direction[1] != 0;
                if (!compatible(piece.type(), diagonal)) continue;
                List<String> through = new ArrayList<>(2);
                int file = piece.square().charAt(0) - 'a' + direction[0];
                int rank = piece.square().charAt(1) - '1' + direction[1];
                while (file >= 0 && file < 8 && rank >= 0 && rank < 8) {
                    String square = "" + (char) ('a' + file) + (char) ('1' + rank);
                    // Include the blocker square, but never count the endpoint as intermediate.
                    if (!through.isEmpty()) result.add(new XrayControl(source.getKey(), square, through));
                    String blockerId = idsBySquare.get(square);
                    if (blockerId != null) {
                        PieceState blocker = pieces.get(blockerId);
                        if (blocker.color() != piece.color() || !compatible(blocker.type(), diagonal)
                                || through.size() == 2) break;
                        through.add(blockerId);
                    }
                    file += direction[0];
                    rank += direction[1];
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean compatible(PieceType type, boolean diagonal) {
        return type == PieceType.QUEEN || type == (diagonal ? PieceType.BISHOP : PieceType.ROOK);
    }
}
