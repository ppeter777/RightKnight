package dev.rightknight.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;
import dev.rightknight.model.GameEntity;
import dev.rightknight.model.GameMoveEntity;
import dev.rightknight.repository.GameMoveRepository;
import dev.rightknight.repository.GameRepository;
import dev.rightknight.utils.ClockControl;
import dev.rightknight.utils.PgnUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class GameMoveImportService {

    private final GameRepository gameRepository;
    private final GameMoveRepository gameMoveRepository;

    @Transactional
    public void importMoves(String gameId) {

        GameEntity game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found: " + gameId));

        String pgn = game.getPgn();
        if (pgn == null || pgn.isBlank()) {
            return;
        }
        String moveTextWithComments = PgnUtils.extractMoveText(pgn);

        List<Long> clocks = PgnUtils.extractClockAfterMs(moveTextWithComments);

        String sanMoveText = PgnUtils.removeComments(moveTextWithComments);

        MoveList list = new MoveList();
        try {
            list.loadFromSan(sanMoveText);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse PGN moves for game " + gameId, e);
        }

        boolean hasClocks = clocks.size() == list.size();

        if (!clocks.isEmpty() && !hasClocks) {
            throw new IllegalStateException(
                    "Clock count (%d) doesn't match move count (%d)"
                            .formatted(clocks.size(), list.size())
            );
        }

        gameMoveRepository.deleteByGame_Id(gameId);
        gameMoveRepository.flush();

        List<GameMoveEntity> moves = new ArrayList<>();
        String[] sanMoves = list.toSanArray();

        if (sanMoves.length != list.size()) {
            throw new IllegalStateException(
                    "SAN array size doesn't match move list size");
        }

        Board board = new Board();

        Long whiteClockAfter = null;
        Long blackClockAfter = null;

        ClockControl clockControl = ClockControl.parse(game.getClockLimit());

        int ply = 1;

        for (Move move : list) {
            int index = ply - 1;
            boolean whiteMove = ply % 2 == 1;

            String fenBefore = board.getFen();
            board.doMove(move);

            long clockAfterMs = clocks.get(index);

            long clockBeforeMs = whiteMove
                    ? whiteClockAfter != null
                      ? whiteClockAfter
                      : clockControl.initialMs()
                    : blackClockAfter != null
                      ? blackClockAfter
                      : clockControl.initialMs();

            long moveTimeMs =
                    clockBeforeMs
                            + clockControl.incrementMs()
                            - clockAfterMs;

            if (moveTimeMs < -2000) {
                throw new IllegalStateException(
                        "Negative move time detected: game=%s ply=%d moveTime=%d"
                                .formatted(gameId, ply, moveTimeMs)
                );
            }

            moveTimeMs = Math.max(0, moveTimeMs);

            GameMoveEntity entity = new GameMoveEntity();
            entity.setGame(game);
            entity.setPly(ply);
            entity.setMoveNumber((ply + 1) / 2);
            entity.setWhiteMove(whiteMove);
            entity.setSan(sanMoves[index]);
            entity.setUci(move.toString());
            entity.setFenBefore(fenBefore);
            entity.setFenAfter(board.getFen());
            entity.setClockBeforeMs(clockBeforeMs);
            entity.setClockAfterMs(clockAfterMs);
            entity.setMoveTimeMs(moveTimeMs);

            if (whiteMove) {
                whiteClockAfter = clockAfterMs;
            } else {
                blackClockAfter = clockAfterMs;
            }

            moves.add(entity);
            ply++;
        }
        gameMoveRepository.saveAll(moves);
    }
}
