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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GameAnalysisServiceTest {
    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameMoveRepository gameMoveRepository;

    @Mock
    private GameAnalysisRepository gameAnalysisRepository;

    @Mock
    private GameMoveAnalysisRepository gameMoveAnalysisRepository;

    @Mock
    private MoveAnalysis moveAnalysis;

    @Mock
    private StockfishService stockfishService;

    private GameAnalysisService gameAnalysisService;

    @BeforeEach
    void setUp() {
        gameAnalysisService = new GameAnalysisService(
                gameRepository,
                gameMoveRepository,
                gameAnalysisRepository,
                gameMoveAnalysisRepository,
                moveAnalysis,
                stockfishService
        );
    }

    @Test
    void analyzeGameReusesAnalysisOfAdjacentPositions() {
        List<EngineCandidate> p0 = List.of(new EngineCandidate());
        List<EngineCandidate> p1 = List.of(new EngineCandidate());
        List<EngineCandidate> p2 = List.of(new EngineCandidate());
        List<EngineCandidate> p3 = List.of(new EngineCandidate());

        when(stockfishService.analyze("P0")).thenReturn(p0);
        when(stockfishService.analyze("P1")).thenReturn(p1);
        when(stockfishService.analyze("P2")).thenReturn(p2);
        when(stockfishService.analyze("P3")).thenReturn(p3);

        GameMoveEntity move1 = new GameMoveEntity();
        GameMoveEntity move2 = new GameMoveEntity();
        GameMoveEntity move3 = new GameMoveEntity();

        move1.setFenBefore("P0");
        move1.setFenAfter("P1");
        move2.setFenBefore("P1");
        move2.setFenAfter("P2");
        move3.setFenBefore("P2");
        move3.setFenAfter("P3");

        String gameId = "game1Id";

        GameEntity game = new GameEntity();

        when(gameRepository.findById(gameId))
                .thenReturn(Optional.of(game));

        when(gameMoveRepository.findByGame_IdOrderByPlyAsc(gameId))
                .thenReturn(List.of(move1, move2, move3));

        when(moveAnalysis.analyzeMove(any(), anyList(), anyList()))
                .thenReturn(
                        new GameMoveAnalysisEntity(),
                        new GameMoveAnalysisEntity(),
                        new GameMoveAnalysisEntity()
                );

        when(gameAnalysisRepository.save(any(GameAnalysisEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(gameMoveAnalysisRepository.save(any(GameMoveAnalysisEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        gameAnalysisService.analyzeGame(gameId);

        verify(stockfishService).analyze("P0");
        verify(stockfishService).analyze("P1");
        verify(stockfishService).analyze("P2");
        verify(stockfishService).analyze("P3");

        verifyNoMoreInteractions(stockfishService);

        verify(moveAnalysis).analyzeMove(move1, p0, p1);
        verify(moveAnalysis).analyzeMove(move2, p1, p2);
        verify(moveAnalysis).analyzeMove(move3, p2, p3);

    }

}
