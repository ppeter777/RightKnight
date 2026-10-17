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
                "V16__add_played_features_and_attack_targets.sql"}) {
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
            } finally {
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
