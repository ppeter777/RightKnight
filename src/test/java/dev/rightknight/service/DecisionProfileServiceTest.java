package dev.rightknight.service;

import dev.rightknight.analysis.*;
import dev.rightknight.analysis.DecisionProfile.*;
import dev.rightknight.engine.EngineCandidate;
import dev.rightknight.model.*;
import dev.rightknight.repository.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DecisionProfileService.class, MoveAnalysisPersistenceService.class, MoveContextCalculator.class,
        ComplexityAssessmentCalculator.class, PositionComplexityCalculator.class, MoveFeatureClassifier.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DecisionProfileServiceTest {
    @Autowired DecisionProfileService service;
    @Autowired MoveAnalysisPersistenceService persistence;
    @Autowired GameRepository games;
    @Autowired GameMoveRepository moves;
    @Autowired GameAnalysisRepository runs;
    @Autowired GameMoveAnalysisRepository analyses;
    @Autowired EntityManagerFactory emf;

    @Test
    void exportsOneRunAcrossBothSidesAndKeepsThePlayedChoiceOutsideTheDecision() {
        var game = new GameEntity();
        game.setId(UUID.randomUUID().toString());
        games.save(game);
        String start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        String next = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        var first = move(game, 1, "e2e4", start, next);
        var second = move(game, 2, "e7e5", next, null);
        var run = run(game);
        var other = run(game);
        var a = new GameMoveAnalysisEntity();
        a.setGameAnalysis(run);
        a.setGameMove(first);
        a.setLegalMovesCount(20);
        a = persistence.save(a, List.of(candidate(2, "d2d4", 11), candidate(1, "e2e4", 26)), null);
        var b = new GameMoveAnalysisEntity();
        b.setGameAnalysis(other);
        b.setGameMove(second);
        persistence.save(b, List.of(candidate(1, "e7e5", 20)), first);

        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var report = service.exportGame(game.getId(), run.getId(), 1).orElseThrow();
        assertEquals(4, statistics.getPrepareStatementCount());
        assertEquals(2, report.positions().size());
        assertEquals(1, report.metadata().missingAnalysisCount());
        var row = report.positions().getFirst();
        var decision = row.decision();
        assertEquals(2, decision.assessment().analyzedCandidateCount());
        assertEquals(1, decision.assessment().window().observedCandidateCount());
        assertEquals(false, decision.assessment().candidateSetComplete());
        assertEquals(1.0, decision.assessment().complexityFactor());
        assertEquals(List.of("e2e4", "d2d4"), decision.candidateEvidence().stream().map(CandidateEvidence::moveUci).toList());
        assertEquals(row.played().playedEvents(), decision.candidateEvidence().getFirst().events());
        assertEquals(3, decision.source().snapshotSchemaVersion());
        var missing = report.positions().getLast().decision();
        assertNull(missing.assessment());
        assertEquals(HistoryStatus.AVAILABLE, missing.root().previousMove().status());
        assertTrue(missing.issues().stream().anyMatch(i -> i.code() == IssueCode.MISSING_MOVE_ANALYSIS));
        assertNull(row.played().moveTimeMs());
        assertTrue(service.exportGame("some-other-game", run.getId(), 1).isEmpty());
        var expanded = service.exportGame(game.getId(), run.getId(), 5).orElseThrow().positions().getFirst().decision();
        assertEquals(decision.candidateEvidence(), expanded.candidateEvidence());
        assertEquals(decision.assessment().explanations(), expanded.assessment().explanations());

        first.setUci("c2c4");
        first.setMoveTimeMs(12345L);
        first.setClockAfterMs(10000L);
        moves.save(first);
        a.setLossCp(200);
        a.setPlayedMoveEvalCp(-300);
        a.setPlayedAttackDelta(null);
        analyses.save(a);
        game.setScore(1);
        games.save(game);
        var changed = service.exportGame(game.getId(), run.getId(), 1).orElseThrow().positions().getFirst();
        assertEquals(decision, changed.decision());
        assertEquals(LossCategory.CRITICAL_BLUNDER, changed.played().lossCategory());
        assertEquals(DirectRelationEvents.Status.MISSING_DATA, changed.played().playedEvents().status());

        a.setLegalMovesCount(19);
        analyses.save(a);
        var invalid = service.exportGame(game.getId(), run.getId(), 1).orElseThrow();
        assertEquals(1, invalid.metadata().invalidInputCount());
        assertTrue(invalid.positions().getFirst().decision().issues().stream().anyMatch(i -> i.code() == IssueCode.INVALID_INPUT));
    }

    private GameAnalysisEntity run(GameEntity game) {
        var run = new GameAnalysisEntity();
        run.setGame(game);
        run.setMultiPv(5);
        return runs.save(run);
    }

    private GameMoveEntity move(GameEntity game, int ply, String uci, String before, String after) {
        var m = new GameMoveEntity();
        m.setGame(game);
        m.setPly(ply);
        m.setMoveNumber(1);
        m.setWhiteMove(ply == 1);
        m.setUci(uci);
        m.setFenBefore(before);
        m.setFenAfter(after);
        return moves.save(m);
    }

    private EngineCandidate candidate(int rank, String move, int cp) {
        var c = new EngineCandidate();
        c.setRank(rank);
        c.setPv(move);
        c.setEvalCp(cp);
        c.setDepth(20);
        return c;
    }
}
