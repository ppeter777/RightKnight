package dev.rightknight.service;

import dev.rightknight.engine.EngineAnalysisSettings;
import dev.rightknight.engine.EngineCandidate;
import dev.rightknight.engine.StockfishSession;
import dev.rightknight.model.GameAnalysisEntity;
import dev.rightknight.model.GameEntity;
import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.GameMoveEntity;
import dev.rightknight.repository.GameAnalysisRepository;
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
    private MoveAnalysisPersistenceService moveAnalysisPersistenceService;

    @Mock
    private MoveAnalysis moveAnalysis;

    @Mock
    private StockfishService stockfishService;

    @Mock
    private StockfishSession stockfishSession;

    private GameAnalysisService gameAnalysisService;

    @BeforeEach
    void setUp() {
        gameAnalysisService = new GameAnalysisService(
                gameRepository,
                gameMoveRepository,
                gameAnalysisRepository,
                moveAnalysisPersistenceService,
                moveAnalysis,
                stockfishService
        );
    }

    @Test
    void analyzeGameReusesAnalysisOfAdjacentPositions() {

        EngineAnalysisSettings settings =
                new EngineAnalysisSettings(20, 5, 4);

        List<EngineCandidate> p0 = List.of(new EngineCandidate());
        List<EngineCandidate> p1 = List.of(new EngineCandidate());
        List<EngineCandidate> p2 = List.of(new EngineCandidate());
        List<EngineCandidate> p3 = List.of(new EngineCandidate());

        when(stockfishService.defaultSettings())
                .thenReturn(settings);

        when(stockfishService.openSession(settings))
                .thenReturn(stockfishSession);

        when(stockfishSession.getEngineName())
                .thenReturn("Stockfish 18");

        when(stockfishSession.analyze("P0")).thenReturn(p0);
        when(stockfishSession.analyze("P1")).thenReturn(p1);
        when(stockfishSession.analyze("P2")).thenReturn(p2);
        when(stockfishSession.analyze("P3")).thenReturn(p3);

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

        when(moveAnalysisPersistenceService.save(any(GameMoveAnalysisEntity.class), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        gameAnalysisService.analyzeGame(gameId);

        verify(stockfishService).defaultSettings();
        verify(stockfishService).openSession(settings);
        verifyNoMoreInteractions(stockfishService);

        verify(stockfishSession).getEngineName();
        verify(stockfishSession).analyze("P0");
        verify(stockfishSession).analyze("P1");
        verify(stockfishSession).analyze("P2");
        verify(stockfishSession).analyze("P3");

        verify(stockfishSession).close();

        verify(moveAnalysis).analyzeMove(move1, p0, p1);
        verify(moveAnalysis).analyzeMove(move2, p1, p2);
        verify(moveAnalysis).analyzeMove(move3, p2, p3);
        verify(moveAnalysisPersistenceService).save(any(GameMoveAnalysisEntity.class), same(p0));
        verify(moveAnalysisPersistenceService).save(any(GameMoveAnalysisEntity.class), same(p1));
        verify(moveAnalysisPersistenceService).save(any(GameMoveAnalysisEntity.class), same(p2));
        verifyNoMoreInteractions(moveAnalysisPersistenceService);
    }

}
