package dev.rightknight.service;

import dev.rightknight.engine.EngineCandidate;
import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.GameMoveEntity;
import dev.rightknight.repository.GameMoveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MoveAnalysisTest {

    @Mock
    private GameMoveRepository gameMoveRepository;

    @Mock
    private StockfishService stockfishService;

    private PositionMetricsCalculator positionMetricsCalculator;

    private MoveAnalysis moveAnalysis;


    @BeforeEach
    void setUp() {
        positionMetricsCalculator = new PositionMetricsCalculator();
        moveAnalysis = new MoveAnalysis(
                gameMoveRepository,
                stockfishService,
                positionMetricsCalculator
        );
    }

    @Test
    void blackLoses50Cp() {

        String fenBefore =
                "r3k2r/pppq1ppp/1b1p1n2/4p3/2N1P3/2PP1Q1P/PP3PP1/R1B1R1K1 b kq - 0 11";

        String fenAfter =
                "r3k2r/pp1q1ppp/1bpp1n2/4p3/2N1P3/2PP1Q1P/PP3PP1/R1B1R1K1 w kq - 0 11";

        GameMoveEntity move = new GameMoveEntity();
        move.setWhiteMove(false);
        move.setUci("c7c6");
        move.setFenBefore(fenBefore);
        move.setFenAfter(fenAfter);

        EngineCandidate before = new EngineCandidate();
        before.setEvalCp(-50);
        before.setPv("a7a6");
        before.setDepth(20);
        before.setSelDepth(30);
        before.setNodes(100000);
        before.setTimeMs(500);

        EngineCandidate after = new EngineCandidate();
        after.setEvalCp(100);

        when(stockfishService.analyze(fenBefore))
                .thenReturn(List.of(before));

        when(stockfishService.analyze(fenAfter))
                .thenReturn(List.of(after));

        GameMoveAnalysisEntity result =
                moveAnalysis.analyzeMove(move);

        assertEquals(50, result.getLossCp());
    }

    @Test
    void whiteLoses80Cp() {

        String fenBefore =
                "r3k2r/pppq1ppp/1b1p1n2/4p3/2N1P3/2PP1Q1P/PP3PP1/R1B2RK1 w kq - 0 11";

        String fenAfter =
                "r3k2r/pppq1ppp/1b1p1n2/4p3/2N1P3/2PP1Q1P/PP3PP1/R1B1R1K1 b kq - 0 11";

        GameMoveEntity move = new GameMoveEntity();
        move.setWhiteMove(true);
        move.setUci("f1e1");
        move.setFenBefore(fenBefore);
        move.setFenAfter(fenAfter);

        EngineCandidate before = new EngineCandidate();
        before.setEvalCp(130);
        before.setPv("c1g5");
        before.setDepth(20);
        before.setSelDepth(30);
        before.setNodes(100000);
        before.setTimeMs(500);

        EngineCandidate after = new EngineCandidate();
        after.setEvalCp(-50);

        when(stockfishService.analyze(fenBefore))
                .thenReturn(List.of(before));

        when(stockfishService.analyze(fenAfter))
                .thenReturn(List.of(after));

        GameMoveAnalysisEntity result =
                moveAnalysis.analyzeMove(move);

        assertEquals(80, result.getLossCp());
    }

    @Test
    void bestMovePlayed() {

        String fenBefore =
                "r3k2r/pppq1ppp/1b1p1n2/4p3/P1N1P3/2PP1N1P/3Q1PP1/R3R1K1 w kq - 0 11";

        String fenAfter =
                "r3k2r/pppq1ppp/1b1p1n2/P3p3/2N1P3/2PP1N1P/3Q1PP1/R3R1K1 b kq - 0 11";

        GameMoveEntity move = new GameMoveEntity();
        move.setWhiteMove(true);
        move.setUci("a4a5");
        move.setFenBefore(fenBefore);
        move.setFenAfter(fenAfter);

        EngineCandidate before = new EngineCandidate();
        before.setEvalCp(460);
        before.setPv("a4a5");
        before.setDepth(20);
        before.setSelDepth(30);
        before.setNodes(100000);
        before.setTimeMs(500);

        EngineCandidate after = new EngineCandidate();
        after.setEvalCp(-459);

        when(stockfishService.analyze(fenBefore))
                .thenReturn(List.of(before));

        when(stockfishService.analyze(fenAfter))
                .thenReturn(List.of(after));

        GameMoveAnalysisEntity result =
                moveAnalysis.analyzeMove(move);

        assertEquals(0, result.getLossCp());
    }

    @Test
    void blackBlunderLoses489Cp() {

        String fenBefore =
                "2Rn1rk1/p3b1pp/1p1qp3/1Q1p4/P2P2P1/1N5P/1P1B1P2/6K1 b - a3 0 30";

        String fenAfter =
                "2R2rk1/p3b1pp/1pnqp3/1Q1p4/P2P2P1/1N5P/1P1B1P2/6K1 w - - 1 31";

        GameMoveEntity move = new GameMoveEntity();
        move.setWhiteMove(false);
        move.setUci("d8c6");
        move.setFenBefore(fenBefore);
        move.setFenAfter(fenAfter);

        EngineCandidate before = new EngineCandidate();
        before.setEvalCp(0);
        before.setPv("e6e5");
        before.setDepth(20);
        before.setSelDepth(30);
        before.setNodes(100000);
        before.setTimeMs(500);

        EngineCandidate after = new EngineCandidate();
        after.setEvalCp(489);

        when(stockfishService.analyze(fenBefore))
                .thenReturn(List.of(before));

        when(stockfishService.analyze(fenAfter))
                .thenReturn(List.of(after));

        GameMoveAnalysisEntity result =
                moveAnalysis.analyzeMove(move);

        assertEquals(489, result.getLossCp());
    }

    @Test
    void analyzeMoveWithPrecomputedAnalysisDoesNotCallStockfish() {

        String fenBefore =
                "r3k2r/pppq1ppp/1b1p1n2/4p3/2N1P3/2PP1Q1P/PP3PP1/R1B2RK1 w kq - 0 11";

        String fenAfter =
                "r3k2r/pppq1ppp/1b1p1n2/4p3/2N1P3/2PP1Q1P/PP3PP1/R1B1R1K1 b kq - 0 11";

        GameMoveEntity move = new GameMoveEntity();
        move.setWhiteMove(true);
        move.setUci("f1e1");
        move.setFenBefore(fenBefore);
        move.setFenAfter(fenAfter);

        EngineCandidate before = new EngineCandidate();
        before.setEvalCp(130);
        before.setPv("c1g5");
        before.setDepth(20);
        before.setSelDepth(30);
        before.setNodes(100000);
        before.setTimeMs(500);

        EngineCandidate after = new EngineCandidate();
        after.setEvalCp(-50);

        GameMoveAnalysisEntity result =
                moveAnalysis.analyzeMove(
                        move,
                        List.of(before),
                        List.of(after)
                );

        assertEquals(130, result.getBestEvalCp());
        assertEquals(50, result.getPlayedMoveEvalCp());
        assertEquals(80, result.getLossCp());

        verifyNoInteractions(stockfishService);
    }

}
