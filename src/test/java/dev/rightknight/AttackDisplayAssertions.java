package dev.rightknight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.Board;
import dev.rightknight.service.AttackGraphCalculator;
import java.sql.Connection;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL-only assertions, invoked after Flyway V18 by PostgresAttackMigrationTest. */
final class AttackDisplayAssertions {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static void verify(Connection connection, String legacySnapshot, String legacyDelta) throws Exception {
        var legacy = display(connection, legacySnapshot, legacyDelta);
        assertEquals(1, legacy.path("sourceSchemaVersion").asInt());
        assertFalse(legacy.has("addedControls"));
        assertFalse(legacy.has("removedControls"));
        assertTrue(legacy.path("addedRelations").isEmpty());
        assertEquals("a1", legacy.at("/removedRelations/0/source/square").asText());
        assertEquals("KING", legacy.at("/removedRelations/0/target/type").asText());
        try (var sql = connection.createStatement(); var row = sql.executeQuery(
                "SELECT attack_delta_display(NULL, '{}'::jsonb), attack_delta_display('{}'::jsonb, NULL)")) {
            assertTrue(row.next());
            assertNull(row.getObject(1));
            assertNull(row.getObject(2));
        }
        assertThrows(java.sql.SQLException.class, () -> display(connection,
                legacySnapshot, legacyDelta.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")));

        for (String[] example : new String[][] {
                {"2R2rk1/p3b1pp/1pnqp3/1Q1p4/P2P2P1/1N5P/1P1B1P2/6K1 w - - 1 31", "b5c6"},
                {"7k/8/8/R2pP2r/8/8/8/6K1 w - d6 0 1", "e5d6"},
                {"r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1g1"},
                {"r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1", "e8c8"},
                {"k6r/6P1/8/8/8/8/8/K7 w - - 0 1", "g7h8q"},
                {"7k/8/8/8/8/8/8/K7 w - - 0 1", "a1a2"}
        }) {
            var board = new Board();
            board.loadFromFen(example[0]);
            var calculator = new AttackGraphCalculator();
            var snapshot = calculator.snapshot(board);
            var move = board.legalMoves().stream().filter(m -> m.toString().equals(example[1])).findFirst().orElseThrow();
            board.doMove(move);
            var delta = calculator.delta(snapshot, move, board);
            String snapshotJson = MAPPER.writeValueAsString(snapshot);
            String deltaJson = MAPPER.writeValueAsString(delta);
            var view = display(connection, snapshotJson, deltaJson);
            var raw = MAPPER.readTree(deltaJson);
            assertEquals(1, view.path("formatVersion").asInt());
            assertEquals(2, view.path("sourceSchemaVersion").asInt());
            assertEquals(example[1], view.path("moveUci").asText());
            assertEquals(raw.get("pieceChanges"), view.get("pieceChanges"));
            for (String field : List.of("addedRelations", "removedRelations", "addedControls", "removedControls")) {
                assertEquals(raw.get(field).size(), view.get(field).size(), example[1] + " " + field);
                // Compare actual squares/colors/types to an independently built position snapshot.
                var position = field.startsWith("added") ? calculator.snapshot(board) : snapshot;
                for (int i = 0; i < view.get(field).size(); i++) {
                    var edge = view.get(field).get(i);
                    var rawEdge = raw.get(field).get(i);
                    for (String endpoint : field.endsWith("Relations") ? List.of("source", "target") : List.of("source")) {
                        var piece = edge.get(endpoint);
                        String id = rawEdge.get(endpoint + "Id").asText();
                        assertEquals(id, piece.get("id").asText());
                        var expected = snapshot.pieces().get(id);
                        if (field.startsWith("added")) {
                            for (var change : delta.pieceChanges()) {
                                if (change.pieceId().equals(id)) expected = change.after();
                            }
                        }
                        assertNotNull(expected);
                        assertEquals(expected.square(), piece.get("square").asText());
                        assertEquals(expected, position.pieces().get(piece.get("square").asText()));
                        assertEquals(expected.type().name(), piece.get("type").asText());
                        assertEquals(expected.color().name(), piece.get("color").asText());
                    }
                    var withoutDisplay = edge.deepCopy();
                    ((com.fasterxml.jackson.databind.node.ObjectNode) withoutDisplay).remove(List.of("source", "target"));
                    assertEquals(rawEdge, withoutDisplay);
                }
            }
            if (example[1].equals("b5c6")) {
                var defence = java.util.stream.StreamSupport.stream(view.get("addedRelations").spliterator(), false)
                        .filter(r -> r.path("sourceId").asText().equals("c8") && r.path("targetId").asText().equals("b5"))
                        .findFirst().orElseThrow();
                assertEquals("DEFENCE", defence.path("kind").asText());
                assertEquals("c8", defence.at("/source/square").asText());
                assertEquals("QUEEN", defence.at("/target/type").asText());
                assertEquals("c6", defence.at("/target/square").asText());
            }
            try (var update = connection.prepareStatement("UPDATE game_move_analysis SET attack_snapshot_before = ?::jsonb, played_attack_delta = ?::jsonb, attack_features_version = 2")) {
                update.setString(1, snapshotJson);
                update.setString(2, deltaJson);
                update.executeUpdate();
            }
            try (var update = connection.prepareStatement("UPDATE game_move_analysis_candidate SET attack_delta = ?::jsonb, move_uci = ?, pv_uci = ?")) {
                update.setString(1, deltaJson);
                update.setString(2, example[1]);
                update.setString(3, example[1]);
                update.executeUpdate();
            }
            try (var sql = connection.createStatement(); var row = sql.executeQuery("SELECT p.played_attack_delta_display, c.attack_delta_display, a.played_attack_delta, a.attack_snapshot_before FROM game_move_analysis_attack_display p JOIN game_move_analysis_candidate_attack_display c USING (game_move_analysis_id) JOIN game_move_analysis a ON a.id = p.game_move_analysis_id")) {
                assertTrue(row.next());
                assertEquals(view, MAPPER.readTree(row.getString(1)));
                assertEquals(view, MAPPER.readTree(row.getString(2)));
                assertEquals(raw, MAPPER.readTree(row.getString(3)));
                assertEquals(MAPPER.readTree(snapshotJson), MAPPER.readTree(row.getString(4)));
            }
        }
    }

    private static JsonNode display(Connection connection, String snapshot, String delta) throws Exception {
        try (var query = connection.prepareStatement("SELECT attack_delta_display(?::jsonb, ?::jsonb)")) {
            query.setString(1, snapshot);
            query.setString(2, delta);
            try (var row = query.executeQuery()) {
                assertTrue(row.next());
                return MAPPER.readTree(row.getString(1));
            }
        }
    }
}
