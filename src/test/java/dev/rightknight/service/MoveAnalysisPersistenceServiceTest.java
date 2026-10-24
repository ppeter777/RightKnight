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
        applyCandidateMigration();
        applyAttackMigration();
        applyGraphMigration();
        GameEntity game = new GameEntity();
        game.setId(UUID.randomUUID().toString());
        game = games.save(game);
        move = new GameMoveEntity();
        move.setGame(game);
        move.setPly(1);
        move.setUci("e2e4");
        move.setFenBefore("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        move = moves.save(move);
        run = new GameAnalysisEntity();
        run.setGame(game);
        run = runs.save(run);
    }

    @Test
    void roundTripKeepsRankPerspectiveMateBoundAndPvAcrossRuns() {
        move.setFenBefore("7k/4P3/8/8/8/8/8/K7 w - - 0 1");
        move.setUci("e7e8q");
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
        assertEquals(true, loaded.getFirst().getGivesCheck()); // e8=Q checks h8.
        assertEquals(false, loaded.getLast().getGivesCheck()); // e8=N does not.
        assertEquals(true, loaded.getFirst().getPromotion());
        assertEquals(true, loaded.getLast().getPromotion());

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
        move.setUci("d2e2");
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
        move.setUci("e7f8");
        move = moves.save(move);
        var saved = persistence.save(analysis(), List.of(candidate(1, "e7f8", -30)), null);
        var parent = analyses.findById(saved.getId()).orElseThrow();
        assertEquals(true, parent.getInCheck());
        assertNull(parent.getPreviousMoveCapture());
        assertNull(parent.getRecaptureMovesCount());
        var child = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(saved.getId()).getFirst();
        assertEquals(false, child.getCapture());
        assertNull(child.getRecapture());
        assertEquals(false, child.getGivesCheck());
        assertEquals(false, child.getPromotion());
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

    @Test
    void candidateMigrationLeavesHistoricalFlagsUnknown() {
        var saved = persistence.save(analysis(), List.of(candidate(1, "e2e4", 36)), null);
        applyCandidateMigration();
        var child = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(saved.getId()).getFirst();
        assertNull(child.getGivesCheck());
        assertNull(child.getPromotion());
        assertEquals(false, child.getCapture());
        assertEquals(36, child.getEvalCp());
    }

    @Test
    void playedMoveOutsideMultiPvAndCandidateTargetsSurviveDatabaseRoundTrip() {
        move.setFenBefore("r1bqkb1r/pp3ppp/n3p3/3pPn2/3p4/N1P2N2/PP2BPPP/R1BQ1RK1 w kq - 0 10");
        move.setPly(19);
        move.setUci("g2g4");
        move = moves.save(move);
        var saved = persistence.save(analysis(), List.of(candidate(1, "c1g5", 95)), null);
        var parent = analyses.findById(saved.getId()).orElseThrow();
        var child = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(saved.getId()).getFirst();
        assertEquals(List.of(new AttackTarget("f5", com.github.bhlangonijr.chesslib.Piece.BLACK_KNIGHT)), parent.getPlayedNewAttackTargets());
        assertEquals(List.of(new AttackTarget("d8", com.github.bhlangonijr.chesslib.Piece.BLACK_QUEEN)), child.getNewAttackTargets());
        assertEquals(false, parent.getPlayedCapture());
        assertEquals(false, parent.getPlayedGivesCheck());
        assertEquals(false, parent.getPlayedPromotion());
        assertNull(parent.getPlayedRecapture());
    }

    @Test
    void alternativeCaptureAfterCxd4IsClassifiedForPlayedMoveAndCandidate() {
        GameMoveEntity previous = new GameMoveEntity();
        previous.setPly(18);
        previous.setUci("c5d4");
        previous.setFenBefore("r1bqkb1r/pp3ppp/n3p3/2ppPn2/3P4/N1P2N2/PP2BPPP/R1BQ1RK1 b kq - 0 9");
        previous.setFenAfter("r1bqkb1r/pp3ppp/n3p3/3pPn2/3p4/N1P2N2/PP2BPPP/R1BQ1RK1 w kq - 0 10");
        move.setFenBefore(previous.getFenAfter());
        move.setPly(19);
        move.setUci("e2a6");
        move = moves.save(move);
        var saved = persistence.save(analysis(), List.of(candidate(1, "f3d4", 98), candidate(2, "e2a6", 75)), previous);
        var parent = analyses.findById(saved.getId()).orElseThrow();
        var loaded = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(saved.getId());
        var classifier = new dev.rightknight.analysis.MoveFeatureClassifier();
        var played = classifier.classifyPlayed(parent, com.github.bhlangonijr.chesslib.Piece.WHITE_BISHOP);
        assertTrue(played.categories().contains(dev.rightknight.analysis.CandidateMoveCategory.OTHER_CAPTURE));
        assertEquals(played, classifier.classify(loaded.getLast(), true, com.github.bhlangonijr.chesslib.Piece.WHITE_BISHOP));
        assertTrue(loaded.getFirst().getRecapture());
    }

    @Test
    void sameMoveHasIdenticalPlayedAndCandidateFeaturesAndEmptyJsonList() {
        var saved = persistence.save(analysis(), List.of(candidate(1, "e2e4", 36)), null);
        var parent = analyses.findById(saved.getId()).orElseThrow();
        var child = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(saved.getId()).getFirst();
        assertEquals(List.of(), parent.getPlayedNewAttackTargets());
        assertEquals(parent.getPlayedNewAttackTargets(), child.getNewAttackTargets());
        assertEquals(parent.getPlayedCapture(), child.getCapture());
        assertEquals(parent.getPlayedRecapture(), child.getRecapture());
        assertEquals(parent.getPlayedGivesCheck(), child.getGivesCheck());
        assertEquals(parent.getPlayedPromotion(), child.getPromotion());
    }

    @Test
    void attackMigrationLeavesExistingRowsUnknownWithoutLosingScores() {
        var saved = persistence.save(analysis(), List.of(candidate(1, "e2e4", 36)), null);
        applyAttackMigration();
        var parent = analyses.findById(saved.getId()).orElseThrow();
        assertNull(parent.getPlayedCapture());
        assertNull(parent.getPlayedRecapture());
        assertNull(parent.getPlayedGivesCheck());
        assertNull(parent.getPlayedPromotion());
        assertNull(parent.getPlayedNewAttackTargets());
        var child = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(saved.getId()).getFirst();
        assertNull(child.getNewAttackTargets());
        assertEquals(36, child.getEvalCp());
        assertEquals(false, child.getCapture());
    }

    @Test
    void invalidPlayedMoveCannotSavePartialAnalysis() {
        move.setUci("e2e5");
        move = moves.save(move);
        long previousCount = analyses.count();
        assertThrows(IllegalArgumentException.class,
                () -> persistence.save(analysis(), List.of(candidate(1, "e2e4", 36)), null));
        assertEquals(previousCount, analyses.count());
        assertEquals(0, candidates.count());
    }

    @Test
    void snapshotAndDeltasRoundTripWithPlayedMoveOutsideMultiPv() {
        move.setFenBefore("7k/4r3/8/8/R7/2N5/8/7K w - - 0 1");
        move.setUci("c3d5");
        move = moves.save(move);
        var saved = persistence.save(analysis(), List.of(candidate(1, "a4a5", 10)), null);
        var loaded = analyses.findById(saved.getId()).orElseThrow();
        var child = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(saved.getId()).getFirst();
        assertEquals(1, loaded.getAttackFeaturesVersion());
        assertEquals(saved.getAttackSnapshotBefore(), loaded.getAttackSnapshotBefore());
        assertEquals(saved.getPlayedAttackDelta(), loaded.getPlayedAttackDelta());
        assertEquals("c3d5", loaded.getPlayedAttackDelta().moveUci());
        assertEquals("a4a5", child.getAttackDelta().moveUci());
        assertEquals(5, loaded.getAttackSnapshotBefore().pieces().size());
        assertEquals("e7", loaded.getPlayedAttackDelta().addedRelations().getFirst().targetId());
        assertEquals("a4", loaded.getPlayedAttackDelta().removedRelations().getFirst().targetId());
    }

    @Test
    void playedCandidateDeltaAndCapturedNullRoundTripIdentically() {
        move.setFenBefore("r6k/8/8/8/8/8/8/R6K w - - 0 1");
        move.setUci("a1a8");
        move = moves.save(move);
        var saved = persistence.save(analysis(), List.of(candidate(1, "a1a8", 100)), null);
        var parent = analyses.findById(saved.getId()).orElseThrow();
        var child = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(saved.getId()).getFirst();
        assertEquals(parent.getPlayedAttackDelta(), child.getAttackDelta());
        assertTrue(child.getAttackDelta().pieceChanges().stream().anyMatch(c -> c.pieceId().equals("a8") && c.after() == null));
        assertTrue(child.getAttackDelta().addedRelations().stream().anyMatch(r -> r.sourceId().equals("a1") && r.targetId().equals("h8")));
    }

    @Test
    void graphMigrationPreservesV16DataAndLeavesNewFieldsUnknown() {
        var saved = persistence.save(analysis(), List.of(candidate(1, "e2e4", 36)), null);
        applyGraphMigration();
        var parent = analyses.findById(saved.getId()).orElseThrow();
        var child = candidates.findByGameMoveAnalysis_IdOrderByPvRankAsc(saved.getId()).getFirst();
        assertNull(parent.getAttackSnapshotBefore());
        assertNull(parent.getPlayedAttackDelta());
        assertNull(parent.getAttackFeaturesVersion());
        assertNull(child.getAttackDelta());
        assertEquals(List.of(), parent.getPlayedNewAttackTargets());
        assertEquals(List.of(), child.getNewAttackTargets());
        assertEquals(36, child.getEvalCp());
    }

    private void applyGraphMigration() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String column : List.of("attack_snapshot_before", "played_attack_delta", "attack_features_version")) {
            jdbc.execute("ALTER TABLE game_move_analysis DROP COLUMN IF EXISTS " + column);
        }
        jdbc.execute("ALTER TABLE game_move_analysis_candidate DROP COLUMN IF EXISTS attack_delta");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V17__add_attack_snapshots_and_deltas.sql")).execute(dataSource);
    }

    private void applyAttackMigration() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String column : List.of("played_capture", "played_recapture", "played_gives_check",
                "played_promotion", "played_new_attack_targets")) {
            jdbc.execute("ALTER TABLE game_move_analysis DROP COLUMN IF EXISTS " + column);
        }
        jdbc.execute("ALTER TABLE game_move_analysis_candidate DROP COLUMN IF EXISTS new_attack_targets");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V16__add_played_features_and_attack_targets.sql")).execute(dataSource);
    }

    private void applyCandidateMigration() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER TABLE game_move_analysis_candidate DROP COLUMN IF EXISTS gives_check");
        jdbc.execute("ALTER TABLE game_move_analysis_candidate DROP COLUMN IF EXISTS promotion");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V15__add_candidate_check_and_promotion.sql")).execute(dataSource);
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
