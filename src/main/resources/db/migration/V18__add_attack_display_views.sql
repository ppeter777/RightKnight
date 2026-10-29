-- Derived diagnostic output only: persisted piece IDs and extraction versions do not change.
-- Resolve added edges in the after-position, removed edges in the before-position.
CREATE FUNCTION attack_delta_display(snapshot JSONB, delta JSONB)
RETURNS JSONB LANGUAGE plpgsql IMMUTABLE STRICT AS $$
DECLARE
    before_pieces JSONB := snapshot->'pieces';
    after_pieces JSONB := before_pieces;
    states JSONB;
    change JSONB;
    edge JSONB;
    source_piece JSONB;
    target_piece JSONB;
    entries JSONB;
    field_name TEXT;
    result JSONB;
BEGIN
    IF snapshot->>'schemaVersion' IS DISTINCT FROM delta->>'schemaVersion'
       OR COALESCE(snapshot->>'schemaVersion', '') NOT IN ('1', '2') THEN
        RAISE EXCEPTION 'Attack display requires matching snapshot/delta schema 1 or 2';
    END IF;
    IF jsonb_typeof(before_pieces) IS DISTINCT FROM 'object' THEN
        RAISE EXCEPTION 'Attack display requires root pieces';
    END IF;
    FOR change IN SELECT value FROM jsonb_array_elements(delta->'pieceChanges') LOOP
        IF before_pieces->(change->>'pieceId') IS DISTINCT FROM change->'before' THEN
            RAISE EXCEPTION 'Piece change does not match root: %', change->>'pieceId';
        END IF;
        IF change->'after' = 'null'::jsonb THEN
            after_pieces := after_pieces - (change->>'pieceId');
        ELSE
            after_pieces := jsonb_set(after_pieces, ARRAY[change->>'pieceId'], change->'after');
        END IF;
    END LOOP;
    result := jsonb_build_object('formatVersion', 1,
        'sourceSchemaVersion', delta->'schemaVersion',
        'moveUci', delta->'moveUci', 'pieceChanges', delta->'pieceChanges');
    FOREACH field_name IN ARRAY ARRAY['addedRelations', 'removedRelations', 'addedControls', 'removedControls'] LOOP
        -- Missing v1 controls remain unknown, not calculated empty arrays.
        IF NOT delta ? field_name OR delta->field_name = 'null'::jsonb THEN
            CONTINUE;
        END IF;
        states := CASE WHEN field_name LIKE 'added%' THEN after_pieces ELSE before_pieces END;
        entries := '[]'::jsonb;
        FOR edge IN SELECT value FROM jsonb_array_elements(delta->field_name) LOOP
            source_piece := states->(edge->>'sourceId');
            IF source_piece IS NULL THEN
                RAISE EXCEPTION 'Unknown source in %: %', field_name, edge->>'sourceId';
            END IF;
            edge := edge || jsonb_build_object('source',
                source_piece || jsonb_build_object('id', edge->>'sourceId'));
            IF field_name LIKE '%Relations' THEN
                target_piece := states->(edge->>'targetId');
                IF target_piece IS NULL THEN
                    RAISE EXCEPTION 'Unknown target in %: %', field_name, edge->>'targetId';
                END IF;
                edge := edge || jsonb_build_object('target',
                    target_piece || jsonb_build_object('id', edge->>'targetId'));
            END IF;
            entries := entries || jsonb_build_array(edge);
        END LOOP;
        result := result || jsonb_build_object(field_name, entries);
    END LOOP;
    RETURN result;
END;
$$;

CREATE VIEW game_move_analysis_attack_display AS
SELECT id AS game_move_analysis_id, game_analysis_id, game_move_id, attack_features_version,
       attack_delta_display(attack_snapshot_before, played_attack_delta) AS played_attack_delta_display
FROM game_move_analysis;

CREATE VIEW game_move_analysis_candidate_attack_display AS
SELECT c.id AS candidate_id, c.game_move_analysis_id, c.pv_rank, c.move_uci,
       a.attack_features_version,
       attack_delta_display(a.attack_snapshot_before, c.attack_delta) AS attack_delta_display
FROM game_move_analysis_candidate c
JOIN game_move_analysis a ON a.id = c.game_move_analysis_id;
