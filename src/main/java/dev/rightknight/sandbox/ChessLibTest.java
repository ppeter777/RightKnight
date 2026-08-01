package dev.rightknight.sandbox;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.game.Game;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;
import com.github.bhlangonijr.chesslib.pgn.PgnIterator;
import dev.rightknight.model.GameEntity;
import dev.rightknight.repository.GameRepository;
import dev.rightknight.utils.PgnUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class ChessLibTest {

    private final GameRepository gameRepository;

    public ChessLibTest(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    public void chessLibTest() throws Exception {
        String gameId = "trubnik:DzMMDonN";

        GameEntity game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found: " + gameId));

        String pgn = game.getPgn();

        String pgnMoves = PgnUtils.extractMoveText(pgn);

        System.out.println(pgnMoves);

        MoveList list = new MoveList();
        list.loadFromSan(pgnMoves);

        Board board = new Board();

        int ply = 1;
        for (Move move : list) {
            String fenBefore = board.getFen();

            board.doMove(move);

            String fenAfter = board.getFen();

            System.out.printf(
                    "ply=%d uci=%s%nfenBefore=%s%nfenAfter=%s%n%n",
                    ply++,
                    move,
                    fenBefore,
                    fenAfter
            );
        }
    }
}
