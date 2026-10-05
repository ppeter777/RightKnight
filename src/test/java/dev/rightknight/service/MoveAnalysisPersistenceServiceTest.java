package dev.rightknight.service;

import dev.rightknight.engine.EngineCandidate;
import dev.rightknight.engine.ScoreBound;
import dev.rightknight.model.*;
import dev.rightknight.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MoveAnalysisPersistenceService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MoveAnalysisPersistenceServiceTest {
    @Autowired private MoveAnalysisPersistenceService persistence;
    @Autowired private GameRepository games;
    @Autowired private GameMoveRepository moves;
    @Autowired private GameAnalysisRepository runs;
    @Autowired private GameMoveAnalysisRepository analyses;
    @Autowired private GameMoveAnalysisCandidateRepository candidates;
    @Autowired private DataSource dataSource;
    private GameMoveEntity move;
    private GameAnalysisEntity run;

    @BeforeEach
    void prepareSchemaAndParents() {
        // Exercise the actual V13 constraints, not only Hibernate-generated DDL.
        new JdbcTemplate(dataSource).execute("DROP TABLE IF EXISTS game_move_analysis_candidate");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V13__create_game_move_analysis_candidates.sql")).execute(dataSource);
        GameEntity game = new GameEntity();
        game.setId(UUID.randomUUID().toString());
        game = games.save(game);
        move = new GameMoveEntity();
        move.setGame(game);
        move.setPly(1);
        move = moves.save(move);
        run = new GameAnalysisEntity();
        run.setGame(game);
        run = runs.save(run);
    }

    @Test
    void roundTripKeepsRankPerspectiveMateBoundAndPvAcrossRuns() {
        EngineCandidate second = candidate(2, "e7e8n", -80);
        second.setScoreBound(ScoreBound.UPPER);
        EngineCandidate first = candidate(1, "e7e8q h8g7", null);
        first.setMateIn(3);
        GameMoveAnalysisEntity saved = persistence.save(analysis(), List.of(second, first));
        var loaded = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(saved.getId());
        assertEquals(2, loaded.size());
        assertEquals(1, loaded.getFirst().getPvRank());
        assertEquals("e7e8q", loaded.getFirst().getMoveUci());
        assertEquals("e7e8q h8g7", loaded.getFirst().getPvUci());
        assertEquals(20, loaded.getFirst().getDepth());
        assertEquals(28, loaded.getFirst().getSelectiveDepth());
        assertNull(loaded.getFirst().getEvalCp());
        assertEquals(3, loaded.getFirst().getMateIn());
        assertEquals(-80, loaded.getLast().getEvalCp());
        assertEquals(ScoreBound.UPPER, loaded.getLast().getScoreBound());

        GameAnalysisEntity secondRun = new GameAnalysisEntity();
        secondRun.setGame(move.getGame());
        run = runs.save(secondRun);
        persistence.save(analysis(), List.of(first, second));
        assertEquals(4, candidates.count());
    }

    @Test
    void duplicateRankRollsBackParentAndCandidates() {
        long previousCount = analyses.count();
        assertThrows(DataIntegrityViolationException.class, () -> persistence.save(analysis(),
                List.of(candidate(1, "e2e4", 20), candidate(1, "d2d4", 10))));
        assertEquals(previousCount, analyses.count());
        assertEquals(0, candidates.count());
    }

    @Test
    void duplicateRootAndInvalidScoreAreRejectedAtomically() {
        long previousCount = analyses.count();
        assertThrows(DataIntegrityViolationException.class, () -> persistence.save(analysis(),
                List.of(candidate(1, "e2e4", 20), candidate(2, "e2e4", 10))));
        EngineCandidate invalid = candidate(1, "e2e4", 20);
        invalid.setMateIn(2);
        assertThrows(DataIntegrityViolationException.class,
                () -> persistence.save(analysis(), List.of(invalid)));
        assertThrows(DataIntegrityViolationException.class,
                () -> persistence.save(analysis(), List.of(candidate(1, "e2e4", null))));
        assertEquals(previousCount, analyses.count());
        assertEquals(0, candidates.count());
    }

    private GameMoveAnalysisEntity analysis() {
        GameMoveAnalysisEntity result = new GameMoveAnalysisEntity();
        result.setGameAnalysis(run);
        result.setGameMove(move);
        return result;
    }

    private EngineCandidate candidate(int rank, String pv, Integer cp) {
        EngineCandidate candidate = new EngineCandidate();
        candidate.setRank(rank);
        candidate.setPv(pv);
        candidate.setEvalCp(cp);
        candidate.setDepth(20);
        candidate.setSelDepth(28);
        return candidate;
    }
}
