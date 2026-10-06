package dev.rightknight.service;

import com.github.bhlangonijr.chesslib.Board;
import dev.rightknight.engine.EngineCandidate;
import dev.rightknight.engine.ScoreBound;
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
        EngineCandidate bestAfter = analysisAfter.isEmpty()
                ? terminalEvaluation(move.getFenAfter()) : analysisAfter.getFirst();

        PositionMetrics positionMetrics =
                positionMetricsCalculator.calculate(move.getFenBefore());

        GameMoveAnalysisEntity analysis = new GameMoveAnalysisEntity();

        analysis.setBestEvalCp(bestBefore.getEvalCp());
        analysis.setBestMateIn(bestBefore.getMateIn());
        analysis.setPlayedMoveMateIn(flipPerspective(bestAfter.getMateIn()));
        analysis.setPlayedMoveEvalCp(flipPerspective(bestAfter.getEvalCp()));
        analysis.setLossCp(calculateLossCp(move, bestBefore, bestAfter));

        analysis.setDepth(bestBefore.getDepth());
        analysis.setSelectiveDepth(bestBefore.getSelDepth());
        analysis.setNodes(bestBefore.getNodes());
        analysis.setEngineTimeMs(bestBefore.getTimeMs());

        analysis.setInCheck(positionMetrics.inCheck());
        analysis.setLegalMovesCount(positionMetrics.legalMovesCount());
        analysis.setCaptureMovesCount(positionMetrics.captureMovesCount());
        analysis.setCheckMovesCount(positionMetrics.checkMovesCount());
        analysis.setPromotionMovesCount(positionMetrics.promotionMovesCount());

        return analysis;
    }

    private Integer calculateLossCp(
            GameMoveEntity move,
            EngineCandidate bestBefore,
            EngineCandidate bestAfter) {

        if (move.getUci().equals(bestBefore.getBestMove())) {
            return 0;
        }

        if (bestBefore.getEvalCp() == null || bestAfter.getEvalCp() == null
                || bestBefore.getScoreBound() != ScoreBound.EXACT
                || bestAfter.getScoreBound() != ScoreBound.EXACT) {
            return null;
        }

        int bestMoveEvalCp = bestBefore.getEvalCp();
        int playedMoveEvalCp = flipPerspective(bestAfter.getEvalCp());

        return Math.max(
                bestMoveEvalCp - playedMoveEvalCp,
                0
        );
    }

    private Integer flipPerspective(Integer score) {
        return score == null ? null : -score;
    }

    private EngineCandidate terminalEvaluation(String fen) {
        Board board = new Board();
        board.loadFromFen(fen);
        if (!board.legalMoves().isEmpty()) {
            throw new IllegalStateException("Missing analysis for a non-terminal position");
        }
        EngineCandidate result = new EngineCandidate();
        if (board.isKingAttacked()) {
            result.setMateIn(0);
        } else {
            result.setEvalCp(0);
        }
        return result;
    }
}
