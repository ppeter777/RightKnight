package dev.rightknight.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.Board;
import dev.rightknight.engine.ScoreBound;
import dev.rightknight.model.GameMoveAnalysisCandidateEntity;
import dev.rightknight.model.GameMoveAnalysisEntity;
import dev.rightknight.model.GameMoveEntity;
import dev.rightknight.service.MoveContext;
import dev.rightknight.service.MoveContextCalculator;
import dev.rightknight.service.PositionMetricsCalculator;
import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.rightknight.analysis.ComplexityExplanations.Rule.*;
import static dev.rightknight.analysis.ComplexityExplanations.Status.*;
import static org.junit.jupiter.api.Assertions.*;

/** User FENs, legal illustrative predecessor captures, synthetic scores (not a Stockfish run). */
class RecaptureExplanationTest {
    private final ComplexityAssessmentCalculator calculator = new ComplexityAssessmentCalculator(
            new PositionComplexityCalculator(), new MoveFeatureClassifier());
    private static final String FIRST = "2r2rk1/pp2bppp/1q2p2n/3pP3/3P2P1/2B2N1P/PPRQbP2/2R3K1 w - - 0 20";
    private static final String SECOND = "5rk1/pp2b1pp/1qr1pP1n/3p4/3P2P1/1NB4P/PPR1QP2/2R3K1 b - - 0 23";
    private static final String THIRD = "2r2rk1/pp1bbppp/1q2p2n/3pP3/3P2P1/2B2N1P/PPnQBP2/R1R3K1 w - - 0 18";
    private static final String OPENING = "r1bqkb1r/ppp1nppp/4p3/3pP3/1n1P4/5N2/PPP1BPPP/RNBQK2R b KQkq - 4 6";

    @Test
    void oneLegalRecaptureAndOnlyAcceptableObservedMove() throws Exception {
        var f = first();
        assertEquals(Set.of("d2e2"), recaptures(f));
        var candidates = candidates(f, Map.of("d2e2", 0), true);
        var result = assess(f, candidates);
        assertStatus(result, ONLY_ONE_ACCEPTABLE_OBSERVED_MOVE, PRESENT);
        assertStatus(result, ONLY_ACCEPTABLE_OBSERVED_MOVE_IS_RECAPTURE, PRESENT);
        assertStatus(result, SINGLE_LEGAL_RECAPTURE_IS_ONLY_ACCEPTABLE_OBSERVED_MOVE, PRESENT);
        assertStatus(result, MULTIPLE_LEGAL_RECAPTURES_ONLY_ONE_ACCEPTABLE_OBSERVED_MOVE, ABSENT);
        assertStatus(result, MULTIPLE_ACCEPTABLE_OBSERVED_MOVES_ALL_RECAPTURES, ABSENT);
        assertTrue(result.explanations().recapturesFullyAnalyzed());
        assertFalse(result.candidateSetComplete());
        var mapper = new ObjectMapper();
        assertEquals(result, mapper.readValue(mapper.writeValueAsString(result), ComplexityAssessment.class));
    }

    @Test
    void threeLegalRecapturesOnlyOneAcceptableRequiresAllRecapturesObserved() {
        var f = second();
        assertEquals(Set.of("g7f6", "e7f6", "f8f6"), recaptures(f));
        var candidates = candidates(f, Map.of("g7f6", 60, "e7f6", -30, "f8f6", -40), true);
        var result = assess(f, candidates);
        assertStatus(result, SINGLE_LEGAL_RECAPTURE_IS_ONLY_ACCEPTABLE_OBSERVED_MOVE, ABSENT);
        assertStatus(result, MULTIPLE_LEGAL_RECAPTURES_ONLY_ONE_ACCEPTABLE_OBSERVED_MOVE, PRESENT);
        assertEquals(3, result.explanations().recaptureMovesCount());
        assertEquals(List.of(1, 2, 3), result.explanations().recaptureRanks());
        assertEquals(List.of(1), result.explanations().acceptableRecaptureRanks());

        var partial = assess(f, candidates.stream().filter(c -> !c.getMoveUci().equals("f8f6")).toList());
        assertFalse(partial.explanations().recapturesFullyAnalyzed());
        assertStatus(partial, ONLY_ACCEPTABLE_OBSERVED_MOVE_IS_RECAPTURE, PRESENT);
        assertStatus(partial, MULTIPLE_LEGAL_RECAPTURES_ONLY_ONE_ACCEPTABLE_OBSERVED_MOVE, UNKNOWN);
        candidates.get(1).setRecapture(null);
        var unknown = assess(f, candidates);
        assertNull(unknown.explanations().recapturesFullyAnalyzed());
        assertEquals(List.of(2), unknown.explanations().unknownRecaptureRanks());
        assertStatus(unknown, MULTIPLE_LEGAL_RECAPTURES_ONLY_ONE_ACCEPTABLE_OBSERVED_MOVE, UNKNOWN);
    }

    @Test
    void bothRecapturesAreTheOnlyAcceptableMovesAndCanOverlapNarrowChoiceRule() {
        var f = third();
        assertEquals(Set.of("c1c2", "d2c2"), recaptures(f));
        var candidates = candidates(f, Map.of("c1c2", 100, "d2c2", 90), true);
        var result = assess(f, candidates);
        assertStatus(result, MULTIPLE_ACCEPTABLE_OBSERVED_MOVES_ALL_RECAPTURES, PRESENT);
        assertStatus(result, TWO_OR_THREE_ACCEPTABLE_WITH_WORSE_ALTERNATIVES, PRESENT);
        assertStatus(result, ONLY_ONE_ACCEPTABLE_OBSERVED_MOVE, ABSENT);
        assertTrue(result.explanations().recapturesFullyAnalyzed());
        assertEquals(List.of(1, 2), result.explanations().acceptableRecaptureRanks());
        // Even one acceptable non-recapture disproves "all recaptures".
        candidates.getLast().setEvalCp(80);
        assertStatus(assess(f, candidates), MULTIPLE_ACCEPTABLE_OBSERVED_MOVES_ALL_RECAPTURES, ABSENT);
        candidates.getLast().setEvalCp(null);
        assertStatus(assess(f, candidates), MULTIPLE_ACCEPTABLE_OBSERVED_MOVES_ALL_RECAPTURES, UNKNOWN);
    }

    @Test
    void c5IsSoleAcceptableWithoutBeingAnActiveMoveOrGivingBlackAnAdvantage() {
        var f = fixture(OPENING, null, null);
        var scores = new LinkedHashMap<String, Integer>();
        scores.put("c7c5", -60); // +0.60 for White => -60 cp for the side to move (Black).
        f.context.legalMoveFeatures().keySet().stream().filter(u -> !u.equals("c7c5")).sorted().limit(4)
                .forEach(u -> scores.put(u, -150));
        var result = assess(f, candidates(f, scores, false));
        assertEquals(5, result.analyzedCandidateCount());
        assertEquals(1, result.acceptableCandidateCount());
        assertEquals(90L, result.bestSecondGapCp());
        assertStatus(result, ONLY_ONE_ACCEPTABLE_OBSERVED_MOVE, PRESENT);
        assertStatus(result, ONLY_ONE_ACTIVE_CONTINUATION_HAS_ADVANTAGE, ABSENT);
        assertFalse(result.explanations().activeRanks().contains(1));
        assertTrue(result.explanations().advantageousActiveRanks().isEmpty());
    }

    @Test
    void missingHistoryOrCountsDoesNotInventRecaptureSubtypes() {
        var noHistory = fixture(FIRST, null, null);
        var result = assess(noHistory, candidates(noHistory, Map.of("d2e2", 0), true));
        assertStatus(result, ONLY_ACCEPTABLE_OBSERVED_MOVE_IS_RECAPTURE, UNKNOWN);
        assertStatus(result, SINGLE_LEGAL_RECAPTURE_IS_ONLY_ACCEPTABLE_OBSERVED_MOVE, UNKNOWN);
        assertNull(result.explanations().recapturesFullyAnalyzed());
        var f = first();
        var candidates = candidates(f, Map.of("d2e2", 0), true);
        f.analysis.setRecaptureMovesCount(null);
        assertStatus(assess(f, candidates), ONLY_ACCEPTABLE_OBSERVED_MOVE_IS_RECAPTURE, PRESENT);
        assertStatus(assess(f, candidates), SINGLE_LEGAL_RECAPTURE_IS_ONLY_ACCEPTABLE_OBSERVED_MOVE, UNKNOWN);
        f.analysis.setPreviousMoveCapture(false);
        candidates.forEach(c -> c.setRecapture(false));
        assertStatus(assess(f, candidates), SINGLE_LEGAL_RECAPTURE_IS_ONLY_ACCEPTABLE_OBSERVED_MOVE, ABSENT);
        assertStatus(assess(f, List.of()), ONLY_ONE_ACCEPTABLE_OBSERVED_MOVE, UNKNOWN);
    }

    private Fixture first() {
        return fixture(FIRST, "2r2rk1/pp2bppp/1q2p2n/3pP3/3P2P1/2Bb1N1P/PPRQBP2/2R3K1 b - - 0 19", "d3e2");
    }
    private Fixture second() {
        return fixture(SECOND, "5rk1/pp2b1pp/1qr1pn1n/3pP3/3P2P1/1NB4P/PPR1QP2/2R3K1 w - - 0 23", "e5f6");
    }
    private Fixture third() {
        return fixture(THIRD, "2r2rk1/pp1bbppp/1q2p2n/3pP3/1n1P2P1/2B2N1P/PPPQBP2/R1R3K1 b - - 0 17", "b4c2");
    }

    private Fixture fixture(String fen, String previousFen, String previousUci) {
        String[] fields = fen.split(" ");
        int ply = Integer.parseInt(fields[5]) * 2 - (fields[1].equals("w") ? 1 : 0);
        var move = new GameMoveEntity();
        move.setFenBefore(fen);
        move.setPly(ply);
        GameMoveEntity previous = null;
        if (previousFen != null) {
            var board = new Board();
            board.loadFromFen(previousFen);
            var capture = board.legalMoves().stream().filter(m -> m.toString().equals(previousUci)).findFirst().orElseThrow();
            assertTrue(board.doMove(capture));
            assertEquals(fen, board.getFen(), "Illustrative predecessor must produce the supplied FEN");
            previous = new GameMoveEntity();
            previous.setPly(ply - 1);
            previous.setFenBefore(previousFen);
            previous.setFenAfter(board.getFen());
            previous.setUci(previousUci);
        }
        var context = new MoveContextCalculator().calculate(move, previous);
        var metrics = new PositionMetricsCalculator().calculate(fen);
        var analysis = new GameMoveAnalysisEntity();
        analysis.setLegalMovesCount(metrics.legalMovesCount());
        analysis.setCaptureMovesCount(metrics.captureMovesCount());
        analysis.setInCheck(metrics.inCheck());
        analysis.setPreviousMoveCapture(context.previousMoveCapture());
        analysis.setRecaptureMovesCount(context.recaptureMovesCount());
        return new Fixture(fen, analysis, context);
    }

    private Set<String> recaptures(Fixture f) {
        Set<String> result = new HashSet<>();
        f.context.legalMoveFeatures().forEach((uci, features) -> { if (Boolean.TRUE.equals(features.recapture())) result.add(uci); });
        return result;
    }

    private List<GameMoveAnalysisCandidateEntity> candidates(Fixture f, Map<String, Integer> scores, boolean addWorseAlternative) {
        Map<String, Integer> all = new HashMap<>(scores);
        if (addWorseAlternative) {
            String quiet = f.context.legalMoveFeatures().keySet().stream().sorted()
                    .filter(u -> !scores.containsKey(u) && !Boolean.TRUE.equals(f.context.legalMoveFeatures().get(u).recapture()))
                    .findFirst().orElseThrow();
            all.put(quiet, Collections.min(scores.values()) - 90);
        }
        var sorted = all.entrySet().stream().sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                .reversed().thenComparing(Map.Entry::getKey)).toList();
        List<GameMoveAnalysisCandidateEntity> candidates = new ArrayList<>();
        for (var entry : sorted) {
            var features = f.context.legalMoveFeatures().get(entry.getKey());
            assertNotNull(features, entry.getKey());
            var c = new GameMoveAnalysisCandidateEntity();
            c.setPvRank(candidates.size() + 1);
            c.setMoveUci(entry.getKey());
            c.setEvalCp(entry.getValue());
            c.setDepth(20);
            c.setScoreBound(ScoreBound.EXACT);
            c.setCapture(features.capture());
            c.setRecapture(features.recapture());
            c.setGivesCheck(features.givesCheck());
            c.setPromotion(features.promotion());
            c.setNewAttackTargets(features.newAttackTargets());
            candidates.add(c);
        }
        return candidates;
    }

    private ComplexityAssessment assess(Fixture f, List<GameMoveAnalysisCandidateEntity> candidates) {
        return calculator.calculate(f.analysis, candidates, f.fen, 5, 5);
    }
    private void assertStatus(ComplexityAssessment a, ComplexityExplanations.Rule rule, ComplexityExplanations.Status status) {
        assertEquals(status, a.explanations().explanations().stream().filter(e -> e.rule() == rule).findFirst().orElseThrow().status(), rule.name());
    }
    private record Fixture(String fen, GameMoveAnalysisEntity analysis, MoveContext context) {}
}
