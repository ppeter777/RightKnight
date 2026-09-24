package dev.rightknight.service;

import dev.rightknight.engine.EngineCandidate;
import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.GameMoveEntity;
import dev.rightknight.repository.GameMoveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MoveAnalysis {
    private final GameMoveRepository gameMoveRepository;
    private final StockfishService stockfishService;
    private final PositionMetricsCalculator positionMetricsCalculator;

    public GameMoveAnalysisEntity analyzeMove(Long moveId) {
        GameMoveEntity move = gameMoveRepository.findById(moveId).get();
        return analyzeMove(move);
    }
    
    public GameMoveAnalysisEntity analyzeMove(GameMoveEntity move) {

        var analysisBefore =
                stockfishService.analyze(move.getFenBefore());

        var analysisAfter =
                stockfishService.analyze(move.getFenAfter());

        return analyzeMove(
                move,
                analysisBefore,
                analysisAfter
        );
    }

    public GameMoveAnalysisEntity analyzeMove(
            GameMoveEntity move,
            List<EngineCandidate> analysisBefore,
            List<EngineCandidate> analysisAfter) {

        EngineCandidate bestBefore = analysisBefore.getFirst();
        EngineCandidate bestAfter = analysisAfter.getFirst();

        PositionMetrics positionMetrics =
                positionMetricsCalculator.calculate(move.getFenBefore());

        GameMoveAnalysisEntity analysis = new GameMoveAnalysisEntity();

        analysis.setBestEvalCp(bestBefore.getEvalCp());
        analysis.setPlayedMoveEvalCp(flipPerspective(bestAfter.getEvalCp()));
        analysis.setLossCp(calculateLossCp(move, bestBefore, bestAfter));

        analysis.setDepth(bestBefore.getDepth());
        analysis.setSelectiveDepth(bestBefore.getSelDepth());
        analysis.setNodes(bestBefore.getNodes());
        analysis.setEngineTimeMs(bestBefore.getTimeMs());

        analysis.setLegalMovesCount(positionMetrics.legalMovesCount());
        analysis.setCaptureMovesCount(positionMetrics.captureMovesCount());
        analysis.setCheckMovesCount(positionMetrics.checkMovesCount());
        analysis.setPromotionMovesCount(positionMetrics.promotionMovesCount());

        return analysis;
    }

    private int calculateLossCp(
            GameMoveEntity move,
            EngineCandidate bestBefore,
            EngineCandidate bestAfter) {

        if (move.getUci().equals(bestBefore.getBestMove())) {
            return 0;
        }

        int bestMoveEvalCp = bestBefore.getEvalCp();
        int playedMoveEvalCp = flipPerspective(bestAfter.getEvalCp());

        return Math.max(
                bestMoveEvalCp - playedMoveEvalCp,
                0
        );
    }

    private int flipPerspective(int evalCp) {
        return -evalCp;
    }
}
