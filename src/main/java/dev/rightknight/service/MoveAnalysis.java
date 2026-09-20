package dev.rightknight.service;

import dev.rightknight.engine.EngineCandidate;
import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.GameMoveEntity;
import dev.rightknight.repository.GameMoveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
        GameMoveAnalysisEntity analysis = new GameMoveAnalysisEntity();

        var analysisResultBeforeMove =
                stockfishService.analyze(move.getFenBefore());

        var analysisResultAfterMove =
                stockfishService.analyze(move.getFenAfter());

        var bestBefore = analysisResultBeforeMove.getFirst();
        var bestAfter = analysisResultAfterMove.getFirst();

        PositionMetrics positionMetrics =
                positionMetricsCalculator.calculate(move.getFenBefore());

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

    private int calculateLossCp(GameMoveEntity move,
                                EngineCandidate bestBefore,
                                EngineCandidate bestAfter) {

        if (move.getUci().equals(bestBefore.getBestMove())) {
            return 0;
        }

        int bestMoveEvalCp = bestBefore.getEvalCp();
        int playedMoveEvalCp = flipPerspective(bestAfter.getEvalCp());

        int lossCp = move.isWhiteMove()
                ? bestMoveEvalCp - playedMoveEvalCp
                : playedMoveEvalCp - bestMoveEvalCp;

        // Из-за эффекта горизонта и разных деревьев поиска движок может
        // немного по-разному оценивать один и тот же ход до и после его выполнения.
        // Для аналитики отрицательную потерю считаем нулевой.
        return Math.max(lossCp, 0);
    }

    private int flipPerspective(int evalCp) {
        return -evalCp;
    }
}
