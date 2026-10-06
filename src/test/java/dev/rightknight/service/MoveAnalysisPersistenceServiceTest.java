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
@Import({MoveAnalysisPersistenceService.class, MoveContextCalculator.class})
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
        applyContextMigration();
        GameEntity game = new GameEntity();
        game.setId(UUID.randomUUID().toString());
        game = games.save(game);
        move = new GameMoveEntity();
        move.setGame(game);
        move.setPly(1);
        move.setFenBefore("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        move = moves.save(move);
        run = new GameAnalysisEntity();
        run.setGame(game);
        run = runs.save(run);
    }

    @Test
    void roundTripKeepsRankPerspectiveMateBoundAndPvAcrossRuns() {
        move.setFenBefore("7k/4P3/8/8/8/8/8/K7 w - - 0 1");
        move = moves.save(move);
        EngineCandidate second = candidate(2, "e7e8n", -80);
        second.setScoreBound(ScoreBound.UPPER);
        EngineCandidate first = candidate(1, "e7e8q h8g7", null);
        first.setMateIn(3);
        GameMoveAnalysisEntity saved = persistence.save(analysis(), List.of(second, first), null);
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
        persistence.save(analysis(), List.of(first, second), null);
        assertEquals(4, candidates.count());
    }

    @Test
    void duplicateRankRollsBackParentAndCandidates() {
        long previousCount = analyses.count();
        assertThrows(DataIntegrityViolationException.class, () -> persistence.save(analysis(),
                List.of(candidate(1, "e2e4", 20), candidate(1, "d2d4", 10)), null));
        assertEquals(previousCount, analyses.count());
        assertEquals(0, candidates.count());
    }

    @Test
    void duplicateRootAndInvalidScoreAreRejectedAtomically() {
        long previousCount = analyses.count();
        assertThrows(DataIntegrityViolationException.class, () -> persistence.save(analysis(),
                List.of(candidate(1, "e2e4", 20), candidate(2, "e2e4", 10)), null));
        EngineCandidate invalid = candidate(1, "e2e4", 20);
        invalid.setMateIn(2);
        assertThrows(DataIntegrityViolationException.class,
                () -> persistence.save(analysis(), List.of(invalid), null));
        assertThrows(DataIntegrityViolationException.class,
                () -> persistence.save(analysis(), List.of(candidate(1, "e2e4", null)), null));
        assertEquals(previousCount, analyses.count());
        assertEquals(0, candidates.count());
    }

    @Test
    void roundTripPersistsCaptureAndRecaptureContext() {
        GameMoveEntity previous = new GameMoveEntity();
        previous.setPly(38);
        previous.setUci("b5e2");
        previous.setFenBefore("2r2rk1/pp2bppp/1q2p2n/1b1pP3/3P2P1/2B2N1P/PPRQBP2/2R3K1 b - - 2 19");
        previous.setFenAfter("2r2rk1/pp2bppp/1q2p2n/3pP3/3P2P1/2B2N1P/PPRQbP2/2R3K1 w - - 0 20");
        move.setFenBefore(previous.getFenAfter());
        move.setPly(39);
        move = moves.save(move);
        var saved = persistence.save(analysis(),
                List.of(candidate(1, "d2e2", 76), candidate(2, "c3a5", -334)), previous);
        var parent = analyses.findById(saved.getId()).orElseThrow();
        assertEquals(false, parent.getInCheck());
        assertEquals(true, parent.getPreviousMoveCapture());
        assertEquals(1, parent.getRecaptureMovesCount());
        var loaded = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(saved.getId());
        assertEquals(true, loaded.getFirst().getCapture());
        assertEquals(true, loaded.getFirst().getRecapture());
        assertEquals(false, loaded.getLast().getCapture());
        assertEquals(false, loaded.getLast().getRecapture());
    }

    @Test
    void missingHistoryKeepsRecaptureUnknownButCheckAndCaptureKnown() {
        move.setFenBefore("2R3k1/pp2bnpp/3qpr2/B2p4/3P2P1/1N5P/PP2QP2/6K1 b - - 2 27");
        move.setPly(54);
        move = moves.save(move);
        var saved = persistence.save(analysis(), List.of(candidate(1, "e7f8", -30)), null);
        var parent = analyses.findById(saved.getId()).orElseThrow();
        assertEquals(true, parent.getInCheck());
        assertNull(parent.getPreviousMoveCapture());
        assertNull(parent.getRecaptureMovesCount());
        var child = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(saved.getId()).getFirst();
        assertEquals(false, child.getCapture());
        assertNull(child.getRecapture());
    }

    @Test
    void migrationLeavesHistoricalRowsUnknown() {
        var saved = persistence.save(analysis(), List.of(candidate(1, "e2e4", 36)), null);
        // Remove the new fields to simulate a pre-V14 schema with existing rows.
        applyContextMigration();
        var parent = analyses.findById(saved.getId()).orElseThrow();
        assertNull(parent.getInCheck());
        assertNull(parent.getPreviousMoveCapture());
        assertNull(parent.getRecaptureMovesCount());
        var child = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(saved.getId()).getFirst();
        assertNull(child.getCapture());
        assertNull(child.getRecapture());
        assertEquals(36, child.getEvalCp());
    }

    private void applyContextMigration() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER TABLE game_move_analysis DROP COLUMN IF EXISTS in_check");
        jdbc.execute("ALTER TABLE game_move_analysis DROP COLUMN IF EXISTS previous_move_capture");
        jdbc.execute("ALTER TABLE game_move_analysis DROP COLUMN IF EXISTS recapture_moves_count");
        jdbc.execute("ALTER TABLE game_move_analysis_candidate DROP COLUMN IF EXISTS capture");
        jdbc.execute("ALTER TABLE game_move_analysis_candidate DROP COLUMN IF EXISTS recapture");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V14__add_move_context_features.sql")).execute(dataSource);
    }

    private GameMoveAnalysisEntity analysis() {
        GameMoveAnalysisEntity result = new GameMoveAnalysisEntity();
        result.setGameAnalysis(run);
        result.setGameMove(move);
        result.setInCheck(new PositionMetricsCalculator().calculate(move.getFenBefore()).inCheck());
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
