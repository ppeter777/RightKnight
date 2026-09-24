package dev.rightknight.service;

import dev.rightknight.engine.EngineCandidate;
import dev.rightknight.model.GameAnalysisEntity;
import dev.rightknight.model.GameEntity;
import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.GameMoveEntity;
import dev.rightknight.repository.GameAnalysisRepository;
import dev.rightknight.repository.GameMoveAnalysisRepository;
import dev.rightknight.repository.GameMoveRepository;
import dev.rightknight.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GameAnalysisService {

    private final GameRepository gameRepository;
    private final GameMoveRepository gameMoveRepository;
    private final GameAnalysisRepository gameAnalysisRepository;
    private final GameMoveAnalysisRepository gameMoveAnalysisRepository;
    private final MoveAnalysis moveAnalysis;
    private final StockfishService stockfishService;

    public GameAnalysisEntity analyzeGame(String gameId) {

        GameEntity game = gameRepository.findById(gameId).orElseThrow();

        GameAnalysisEntity gameAnalysis = new GameAnalysisEntity();
        gameAnalysis.setGame(game);
        gameAnalysis.setCreatedAt(ZonedDateTime.now());

        gameAnalysis = gameAnalysisRepository.save(gameAnalysis);

        var moves = gameMoveRepository.findByGame_IdOrderByPlyAsc(gameId);

        if (moves.isEmpty()) {
            return null;
        }

        List<EngineCandidate> analysisBefore =
                stockfishService.analyze(moves.getFirst().getFenBefore());

        GameMoveEntity previousMove = null;

        for (GameMoveEntity move : moves) {

            if (previousMove != null &&
                    !previousMove.getFenAfter().equals(move.getFenBefore())) {
                throw new IllegalStateException(
                        "Broken move sequence at ply " + move.getPly()
                );
            }

            List<EngineCandidate> analysisAfter =
                    stockfishService.analyze(move.getFenAfter());

            GameMoveAnalysisEntity moveResult =
                    moveAnalysis.analyzeMove(
                            move,
                            analysisBefore,
                            analysisAfter
                    );

            moveResult.setGameAnalysis(gameAnalysis);
            moveResult.setGameMove(move);

            gameMoveAnalysisRepository.save(moveResult);

            analysisBefore = analysisAfter;

            previousMove = move;
        }

        gameAnalysis.setCompletedAt(ZonedDateTime.now());

        return gameAnalysisRepository.save(gameAnalysis);
    }
}
