package dev.rightknight.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.Board;
import dev.rightknight.engine.ScoreBound;
import dev.rightknight.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.rightknight.analysis.CandidateMoveCategory.*;
import static dev.rightknight.analysis.ComplexityAssessment.UnknownReason.*;

class ComplexityAssessmentCalculatorTest {
    private final ComplexityAssessmentCalculator calculator = new ComplexityAssessmentCalculator(
            new PositionComplexityCalculator(), new MoveFeatureClassifier());
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @ParameterizedTest
    @CsvSource({"0, INACCURACY", "19, INACCURACY", "20, INACCURACY", "49, INACCURACY",
            "50, MISTAKE", "99, MISTAKE", "100, BLUNDER", "199, BLUNDER", "200, CRITICAL_BLUNDER", "1000, CRITICAL_BLUNDER"})
    void sharedLossPolicyHonorsEveryBoundary(long loss, LossCategory expected) {
        assertEquals(expected, LossPolicy.v1().classify(loss));
        assertEquals(loss < 50, LossPolicy.v1().acceptable(loss));
        var analysis = root();
        analysis.setLossCp((int) loss);
        assertEquals(expected, new MoveQualityClassifier().classify(new GameMoveEntity(), analysis, 1000L).lossCategory());
        var result = assess(analysis, List.of(c(1, "e2e4", 400), c(2, "d2d4", 400 - (int) loss)), START, 2);
        assertEquals(expected, result.candidates().get(1).lossCategory());
        assertEquals(loss, result.bestSecondGapCp());
    }

    @Test
    void sortsCandidatesAndReportsFeatureAndLossRanksWithinRequestedWindow() throws Exception {
        var first = c(1, "a2a3", 200);
        first.setGivesCheck(true); // unit-level supplied flags; real geometry is covered by persistence test
        var second = c(2, "b2b3", 151);
        second.setCapture(true);
        var third = c(3, "c2c3", 150);
        third.setGivesCheck(true);
        var fourth = c(4, "d2d3", 100);
        var fifth = c(5, "e2e3", 0);
        fifth.setGivesCheck(true);
        var result = assess(root(), List.of(fifth, second, fourth, first, third), null, 3);
        assertEquals(5, result.analyzedCandidateCount());
        assertEquals(2, result.acceptableCandidateCount());
        assertEquals(0.4, result.acceptableCandidateRatio());
        assertNull(result.candidateSetComplete());
        assertEquals(49L, result.bestSecondGapCp());
        assertTrue(result.window().ranksComplete());
        assertEquals(3, result.window().observedCandidateCount());
        assertEquals(List.of(1, 2), result.window().lossCategoryRanks().get(LossCategory.INACCURACY));
        assertEquals(List.of(3), result.window().lossCategoryRanks().get(LossCategory.MISTAKE));
        var checks = result.window().moveCategoryRanks().get(GIVES_CHECK);
        assertEquals(List.of(1, 3), checks.presentRanks());
        assertEquals(List.of(1), checks.acceptableRanks());
        assertTrue(checks.unknownRanks().isEmpty());
        assertEquals(1.0, result.complexityFactor());
        assertThrows(UnsupportedOperationException.class, () -> checks.presentRanks().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.window().lossCategoryRanks().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.candidates().clear());
        var mapper = new ObjectMapper();
        assertEquals(result, mapper.readValue(mapper.writeValueAsString(result), ComplexityAssessment.class));
    }

    @Test
    void fiveAcceptableCandidatesDoNotImplyFullCoverageAndLargerWindowIsIncomplete() {
        var candidates = List.of(c(1, "e2e4", 20), c(2, "d2d4", 15), c(3, "c2c4", 10),
                c(4, "g1f3", 5), c(5, "b1c3", 0));
        var result = assess(root(), candidates, START, 10);
        assertEquals(5, result.acceptableCandidateCount());
        assertEquals(1.0, result.acceptableCandidateRatio());
        assertFalse(result.candidateSetComplete());
        assertFalse(result.window().ranksComplete());
        assertEquals(5, result.window().observedCandidateCount());
    }

    @Test
    void comparesCandidateUcisToAllLegalMovesAndHandlesTerminalEmptyData() {
        var board = new Board();
        board.loadFromFen(START);
        var candidates = new ArrayList<GameMoveAnalysisCandidateEntity>();
        int rank = 1;
        for (var move : board.legalMoves()) candidates.add(c(rank++, move.toString(), 0));
        var complete = assess(root(), candidates, START, 30);
        assertTrue(complete.candidateSetComplete());
        assertTrue(complete.window().ranksComplete());
        var empty = assess(root(), List.of(), START, 5);
        assertNull(empty.acceptableCandidateCount());
        assertNull(empty.acceptableCandidateRatio());
        assertTrue(empty.unknownReasons().contains(NO_CANDIDATES));
        assertFalse(empty.window().ranksComplete());
        var terminal = root();
        terminal.setLegalMovesCount(0);
        terminal.setInCheck(true);
        var end = assess(terminal, List.of(), "7k/6Q1/5K2/8/8/8/8/8 b - - 0 1", 5);
        assertTrue(end.candidateSetComplete());
        assertNull(end.acceptableCandidateCount());
        assertFalse(end.position().features().contains(PositionFeature.SINGLE_LEGAL_MOVE));
    }

    @Test
    void unknownScoreDoesNotBecomeBadOrDisappearFromDenominatorAndGapUsesOnlyFirstTwo() {
        var unknown = c(3, "c2c4", null);
        unknown.setGivesCheck(null);
        var result = assess(root(), List.of(c(1, "e2e4", 50), c(2, "d2d4", 0), unknown), START, 3);
        assertEquals(50L, result.bestSecondGapCp());
        assertNull(result.acceptableCandidateCount());
        assertNull(result.acceptableCandidateRatio());
        assertNull(result.window().acceptableCandidateCount());
        assertEquals(3, result.analyzedCandidateCount());
        assertEquals(List.of(3), result.window().lossCategoryRanks().get(LossCategory.UNKNOWN));
        assertEquals(List.of(3), result.window().moveCategoryRanks().get(GIVES_CHECK).unknownRanks());
        assertTrue(result.unknownReasons().contains(MISSING_CP_SCORE));
        assertEquals(1, assess(root(), List.of(c(1, "e2e4", 50), c(2, "d2d4", 0), unknown), START, 2)
                .window().acceptableCandidateCount());
    }

    @Test
    void mateBoundsDepthAndMisorderedScoresRemainExplicitlyUnknown() {
        var mate = c(1, "e2e4", null);
        mate.setMateIn(3);
        assertUnknown(mate, c(2, "d2d4", 0), MATE_SCORE);
        var bounded = c(2, "d2d4", 0);
        bounded.setScoreBound(ScoreBound.LOWER);
        assertUnknown(c(1, "e2e4", 50), bounded, NON_EXACT_SCORE);
        bounded.setScoreBound(ScoreBound.UPPER);
        assertUnknown(c(1, "e2e4", 50), bounded, NON_EXACT_SCORE);
        var shallow = c(2, "d2d4", 0);
        shallow.setDepth(19);
        assertUnknown(c(1, "e2e4", 50), shallow, DEPTH_MISMATCH);
        shallow.setDepth(null);
        assertUnknown(c(1, "e2e4", 50), shallow, MISSING_DEPTH);
        assertUnknown(c(1, "e2e4", 0), c(2, "d2d4", 50), INCONSISTENT_RANKING);
        var internal = assess(root(), List.of(c(1, "e2e4", 100), c(2, "d2d4", 0), c(3, "c2c4", 30)), START, 3);
        assertEquals(100L, internal.bestSecondGapCp());
        assertNull(internal.acceptableCandidateCount());
        assertTrue(internal.unknownReasons().contains(INCONSISTENT_RANKING));
    }

    @Test
    void missingRanksAndHistoricalFlagsAreNotInvented() {
        var second = c(2, "d2d4", 0);
        second.setNewAttackTargets(null);
        var missing = assess(root(), List.of(second), START, 2);
        assertTrue(missing.unknownReasons().contains(MISSING_RANK_ONE));
        assertNull(missing.bestSecondGapCp());
        assertFalse(missing.window().ranksComplete());
        assertTrue(missing.candidates().getFirst().features().unknownCategories().contains(NEW_ATTACK_ON_MORE_VALUABLE_PIECE));
        var single = assess(root(), List.of(c(1, "e2e4", 0)), START, 1);
        assertEquals(1, single.acceptableCandidateCount());
        assertTrue(single.gapUnknownReasons().contains(MISSING_RANK_TWO));
        assertFalse(single.candidateSetComplete());
        assertFalse(single.position().features().contains(PositionFeature.SINGLE_LEGAL_MOVE));
    }

    @Test
    void rejectsDuplicateIllegalAndCrossRootDataAndDoesNotDependOnPlayedChoice() {
        var root = root();
        var first = c(1, "e2e4", 0);
        assertThrows(IllegalArgumentException.class, () -> assess(root, List.of(first, c(1, "d2d4", 0)), START, 5));
        assertThrows(IllegalArgumentException.class, () -> assess(root, List.of(first, c(2, "e2e4", 0)), START, 5));
        assertThrows(IllegalArgumentException.class, () -> assess(root, List.of(c(1, "e2e5", 0)), START, 5));
        assertThrows(IllegalArgumentException.class, () -> assess(root, List.of(first), START, 0));
        var other = new GameMoveAnalysisEntity();
        other.setId(99L);
        first.setGameMoveAnalysis(other);
        assertThrows(IllegalArgumentException.class, () -> assess(root, List.of(first), START, 5));
        first.setGameMoveAnalysis(root);
        var before = assess(root, List.of(first), START, 5);
        root.setLossCp(500);
        root.setPlayedGivesCheck(true);
        root.setPlayedMoveEvalCp(-1000);
        var gameMove = new GameMoveEntity();
        gameMove.setUci("d2d4");
        gameMove.setMoveTimeMs(100000L);
        root.setGameMove(gameMove);
        assertEquals(before, assess(root, List.of(first), START, 5));
    }

    @Test
    void customPolicyIsExplicitAndLargeScoreDifferencesDoNotOverflow() {
        var candidates = List.of(c(1, "e2e4", 0), c(2, "d2d4", -49));
        var stricter = new LossPolicy("experiment-30", 30, 60, 120);
        var result = calculator.calculate(root(), candidates, START, 5, 5, stricter);
        assertEquals(stricter, result.lossPolicy());
        assertEquals(1, result.acceptableCandidateCount());
        var large = assess(root(), List.of(c(1, "e2e4", Integer.MAX_VALUE), c(2, "d2d4", Integer.MIN_VALUE)), START, 2);
        assertEquals(4294967295L, large.bestSecondGapCp());
        assertEquals(LossCategory.CRITICAL_BLUNDER, large.candidates().get(1).lossCategory());
        assertThrows(IllegalArgumentException.class, () -> LossPolicy.v1().classify(-1L));
        assertThrows(IllegalArgumentException.class, () -> new LossPolicy("bad", 50, 50, 200));
    }

    private void assertUnknown(GameMoveAnalysisCandidateEntity first, GameMoveAnalysisCandidateEntity second,
                               ComplexityAssessment.UnknownReason reason) {
        var result = assess(root(), List.of(first, second), START, 2);
        assertNull(result.bestSecondGapCp());
        assertNull(result.acceptableCandidateCount());
        assertTrue(result.unknownReasons().contains(reason));
        assertTrue(result.gapUnknownReasons().contains(reason));
    }

    private ComplexityAssessment assess(GameMoveAnalysisEntity root, List<GameMoveAnalysisCandidateEntity> candidates,
                                         String fen, int top) {
        return calculator.calculate(root, candidates, fen, 5, top);
    }

    private GameMoveAnalysisEntity root() {
        var root = new GameMoveAnalysisEntity();
        root.setId(1L);
        root.setLegalMovesCount(20);
        root.setCaptureMovesCount(0);
        root.setRecaptureMovesCount(0);
        root.setPreviousMoveCapture(false);
        root.setInCheck(false);
        return root;
    }

    private GameMoveAnalysisCandidateEntity c(int rank, String uci, Integer cp) {
        var c = new GameMoveAnalysisCandidateEntity();
        c.setPvRank(rank);
        c.setMoveUci(uci);
        c.setEvalCp(cp);
        c.setDepth(20);
        c.setScoreBound(ScoreBound.EXACT);
        c.setCapture(false);
        c.setRecapture(false);
        c.setGivesCheck(false);
        c.setPromotion(false);
        c.setNewAttackTargets(List.of());
        return c;
    }
}
