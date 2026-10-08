package dev.rightknight;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisSchemaMigrationTest {
    private static final String BASE = "V11_1__create_analysis_tables.sql";
    private static final String[] LATER = {
            "V12__drop_engine_version_from_game_analysis.sql",
            "V13__create_game_move_analysis_candidates.sql",
            "V14__add_move_context_features.sql",
            "V15__add_candidate_check_and_promotion.sql"
    };

    @TempDir Path migrations;

    @Test
    void upgradesDatabaseWithNoAnalysisTablesFromVersion11() throws Exception {
        String url = newDatabase();
        copyMigration(BASE);
        for (String name : LATER) copyMigration(name);

        assertEquals(5, flyway(url, false).migrate().migrationsExecuted);
        assertFinalSchema(url);
        assertEquals(0, flyway(url, false).migrate().migrationsExecuted);
    }

    @Test
    void adoptsExistingVersion15TablesWithoutLosingDataOrRestoringRemovedColumn() throws Exception {
        String url = newDatabase();
        // Simulate tables previously created outside Flyway, then V12-V15 applied.
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/" + BASE));
            connection.createStatement().execute("INSERT INTO games VALUES ('existing-game')");
            connection.createStatement().execute(
                    "INSERT INTO game_analysis (game_id, engine_name) VALUES ('existing-game', 'Stockfish')");
        }
        for (String name : LATER) copyMigration(name);
        assertEquals(4, flyway(url, false).migrate().migrationsExecuted);

        copyMigration(BASE);
        assertEquals(1, flyway(url, true).migrate().migrationsExecuted);
        assertFinalSchema(url);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             var rows = connection.createStatement().executeQuery("SELECT engine_name FROM game_analysis")) {
            assertTrue(rows.next());
            assertEquals("Stockfish", rows.getString(1));
            assertFalse(rows.next());
        }
        assertEquals(0, flyway(url, false).migrate().migrationsExecuted);
    }

    private String newDatabase() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            // Only parent keys are needed here; this is not a full V1-V11 schema test.
            connection.createStatement().execute("CREATE TABLE games (id VARCHAR(255) PRIMARY KEY)");
            connection.createStatement().execute("CREATE TABLE game_moves (id BIGINT PRIMARY KEY)");
        }
        return url;
    }

    private Flyway flyway(String url, boolean outOfOrder) {
        return Flyway.configure().dataSource(url, "sa", "")
                .locations("filesystem:" + migrations.toAbsolutePath())
                .baselineOnMigrate(true).baselineVersion("11")
                .outOfOrder(outOfOrder).load();
    }

    private void copyMigration(String name) throws Exception {
        try (var input = new ClassPathResource("db/migration/" + name).getInputStream()) {
            Files.copy(input, migrations.resolve(name));
        }
    }

    private void assertFinalSchema(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (var columns = connection.getMetaData().getColumns(null, "PUBLIC", "GAME_ANALYSIS", "ENGINE_VERSION")) {
                assertFalse(columns.next());
            }
            try (var statement = connection.createStatement()) {
                statement.executeQuery("SELECT in_check, previous_move_capture, recapture_moves_count FROM game_move_analysis").close();
                statement.executeQuery("SELECT capture, recapture, gives_check, promotion FROM game_move_analysis_candidate").close();
            }
        }
    }
}
