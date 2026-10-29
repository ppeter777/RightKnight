package dev.rightknight;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Runs on the disposable PostgreSQL service in CI; never touches application schemas. */
@EnabledIfEnvironmentVariable(named = "RIGHTKNIGHT_TEST_PG_URL", matches = ".+")
class PostgresAttackMigrationTest {
    @TempDir Path migrations;

    @Test
    void migratesExistingRowsToNullableFeaturesAndStoresRealJsonArrays() throws Exception {
        String url = System.getenv("RIGHTKNIGHT_TEST_PG_URL");
        String user = System.getenv("RIGHTKNIGHT_TEST_PG_USER");
        String password = System.getenv("RIGHTKNIGHT_TEST_PG_PASSWORD");
        String schema = "attack_test_" + UUID.randomUUID().toString().replace("-", "");
        for (String name : new String[]{"V11_1__create_analysis_tables.sql",
                "V12__drop_engine_version_from_game_analysis.sql", "V13__create_game_move_analysis_candidates.sql",
                "V14__add_move_context_features.sql", "V15__add_candidate_check_and_promotion.sql",
                "V16__add_played_features_and_attack_targets.sql",
                "V17__add_attack_snapshots_and_deltas.sql",
                "V18__add_attack_display_views.sql"}) {
            try (var input = new ClassPathResource("db/migration/" + name).getInputStream()) {
                Files.copy(input, migrations.resolve(name));
            }
        }
        try (var connection = DriverManager.getConnection(url, user, password);
             var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                sql.execute("SET search_path TO " + schema);
                sql.execute("CREATE TABLE games (id VARCHAR(255) PRIMARY KEY)");
                sql.execute("CREATE TABLE game_moves (id BIGINT PRIMARY KEY)");
                var config = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                        .locations("filesystem:" + migrations.toAbsolutePath()).baselineOnMigrate(true).baselineVersion("11");
                assertEquals(5, config.target("15").load().migrate().migrationsExecuted);
                sql.execute("INSERT INTO games VALUES ('game')");
                sql.execute("INSERT INTO game_moves VALUES (1)");
                sql.execute("INSERT INTO game_analysis (id, game_id) VALUES (1, 'game')");
                sql.execute("INSERT INTO game_move_analysis (id, game_analysis_id, game_move_id) VALUES (1, 1, 1)");
                sql.execute("INSERT INTO game_move_analysis_candidate (game_move_analysis_id, pv_rank, move_uci, eval_cp, score_bound, depth, pv_uci) VALUES (1, 1, 'g2g4', 84, 'EXACT', 20, 'g2g4')");
                var upgraded = config.target("16").load();
                assertEquals(1, upgraded.migrate().migrationsExecuted);
                try (var row = sql.executeQuery("SELECT played_capture, played_recapture, played_gives_check, played_promotion, played_new_attack_targets FROM game_move_analysis")) {
                    assertTrue(row.next());
                    for (int column = 1; column <= 5; column++) assertNull(row.getObject(column));
                }
                try (var row = sql.executeQuery("SELECT new_attack_targets, eval_cp FROM game_move_analysis_candidate")) {
                    assertTrue(row.next());
                    assertNull(row.getObject(1));
                    assertEquals(84, row.getInt(2));
                }
                sql.execute("UPDATE game_move_analysis SET played_new_attack_targets = '[]'::jsonb");
                sql.execute("UPDATE game_move_analysis_candidate SET new_attack_targets = '[{\"square\":\"f5\",\"piece\":\"BLACK_KNIGHT\"}]'::jsonb");
                try (var row = sql.executeQuery("SELECT pg_typeof(played_new_attack_targets)::text, jsonb_array_length(played_new_attack_targets) FROM game_move_analysis")) {
                    assertTrue(row.next());
                    assertEquals("jsonb", row.getString(1));
                    assertEquals(0, row.getInt(2));
                }
                try (var row = sql.executeQuery("SELECT jsonb_typeof(new_attack_targets), new_attack_targets->0->>'piece' FROM game_move_analysis_candidate")) {
                    assertTrue(row.next());
                    assertEquals("array", row.getString(1));
                    assertEquals("BLACK_KNIGHT", row.getString(2));
                }
                assertEquals(0, upgraded.migrate().migrationsExecuted);
                var v17 = config.target("17").load();
                assertEquals(1, v17.migrate().migrationsExecuted);
                try (var row = sql.executeQuery("SELECT attack_snapshot_before, played_attack_delta, attack_features_version, played_new_attack_targets::text FROM game_move_analysis")) {
                    assertTrue(row.next());
                    for (int column = 1; column <= 3; column++) assertNull(row.getObject(column));
                    assertEquals("[]", row.getString(4));
                }
                try (var row = sql.executeQuery("SELECT attack_delta, eval_cp FROM game_move_analysis_candidate")) {
                    assertTrue(row.next());
                    assertNull(row.getObject(1));
                    assertEquals(84, row.getInt(2));
                }
                var board = new com.github.bhlangonijr.chesslib.Board();
                board.loadFromFen("r6k/8/8/8/8/8/8/R6K w - - 0 1");
                var calculator = new dev.rightknight.service.AttackGraphCalculator();
                var snapshot = calculator.snapshot(board);
                var move = board.legalMoves().stream().filter(m -> m.toString().equals("a1a8")).findFirst().orElseThrow();
                board.doMove(move);
                var delta = calculator.delta(snapshot, move, board);
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String snapshotJson = mapper.writeValueAsString(snapshot);
                String deltaJson = mapper.writeValueAsString(delta);
                try (var update = connection.prepareStatement("UPDATE game_move_analysis SET attack_snapshot_before = ?::jsonb, played_attack_delta = ?::jsonb, attack_features_version = 2")) {
                    update.setString(1, snapshotJson);
                    update.setString(2, deltaJson);
                    update.executeUpdate();
                }
                try (var update = connection.prepareStatement("UPDATE game_move_analysis_candidate SET attack_delta = ?::jsonb")) {
                    update.setString(1, deltaJson);
                    update.executeUpdate();
                }
                try (var row = sql.executeQuery("SELECT pg_typeof(attack_snapshot_before)::text, attack_snapshot_before, played_attack_delta, attack_features_version FROM game_move_analysis")) {
                    assertTrue(row.next());
                    assertEquals("jsonb", row.getString(1));
                    assertEquals(snapshot, mapper.readValue(row.getString(2), dev.rightknight.model.attack.AttackSnapshot.class));
                    assertEquals(delta, mapper.readValue(row.getString(3), dev.rightknight.model.attack.AttackDelta.class));
                    assertEquals(2, row.getInt(4));
                }
                try (var row = sql.executeQuery("SELECT jsonb_typeof(attack_delta), attack_delta->>'moveUci', attack_delta FROM game_move_analysis_candidate")) {
                    assertTrue(row.next());
                    assertEquals("object", row.getString(1));
                    assertEquals("a1a8", row.getString(2));
                    assertEquals(delta, mapper.readValue(row.getString(3), dev.rightknight.model.attack.AttackDelta.class));
                }
                try (var row = sql.executeQuery("SELECT attack_snapshot_before->'squareControl'->'a2'->'WHITE'->>0, played_attack_delta->'addedControls', played_attack_delta->'removedControls' FROM game_move_analysis")) {
                    assertTrue(row.next());
                    assertEquals("a1", row.getString(1));
                    assertTrue(mapper.readTree(row.getString(2)).isArray());
                    assertTrue(mapper.readTree(row.getString(3)).isArray());
                }
                // Old persisted JSON lacks the control fields. Reading it must not fabricate empty data.
                String legacySnapshot = new String(new ClassPathResource("attack-features/v1-snapshot.json").getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                String legacyDelta = new String(new ClassPathResource("attack-features/v1-delta.json").getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                try (var update = connection.prepareStatement("UPDATE game_move_analysis SET attack_snapshot_before = ?::jsonb, played_attack_delta = ?::jsonb, attack_features_version = 1")) {
                    update.setString(1, legacySnapshot);
                    update.setString(2, legacyDelta);
                    update.executeUpdate();
                }
                try (var update = connection.prepareStatement("UPDATE game_move_analysis_candidate SET attack_delta = ?::jsonb")) {
                    update.setString(1, legacyDelta);
                    update.executeUpdate();
                }
                try (var row = sql.executeQuery("SELECT attack_snapshot_before, played_attack_delta, attack_features_version FROM game_move_analysis")) {
                    assertTrue(row.next());
                    var historical = mapper.readValue(row.getString(1), dev.rightknight.model.attack.AttackSnapshot.class);
                    assertEquals(1, historical.schemaVersion());
                    assertNull(historical.squareControl());
                    assertTrue(historical.relations().stream().anyMatch(r -> r.kind() == dev.rightknight.model.attack.AttackSnapshot.RelationKind.DEFENCE && r.targetId().equals("h1")));
                    assertNull(mapper.readValue(row.getString(2), dev.rightknight.model.attack.AttackDelta.class).addedControls());
                    assertEquals(1, row.getInt(3));
                }
                try (var row = sql.executeQuery("SELECT attack_delta FROM game_move_analysis_candidate")) {
                    assertTrue(row.next());
                    assertNull(mapper.readValue(row.getString(1), dev.rightknight.model.attack.AttackDelta.class).removedControls());
                }
                assertEquals(0, v17.migrate().migrationsExecuted);
                var v18 = config.target("18").load();
                assertEquals(1, v18.migrate().migrationsExecuted);
                AttackDisplayAssertions.verify(connection, legacySnapshot, legacyDelta);
                assertEquals(0, v18.migrate().migrationsExecuted);
            } finally {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
