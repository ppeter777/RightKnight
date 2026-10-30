-- Support schema 3 battery controls in the existing diagnostic views. No stored data is rewritten.
-- Resolve added edges in the after-position, removed edges in the before-position.
CREATE OR REPLACE FUNCTION attack_delta_display(snapshot JSONB, delta JSONB)
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
    through_pieces JSONB;
    through_id TEXT;
    through_piece JSONB;
    field_name TEXT;
    result JSONB;
BEGIN
    IF snapshot->>'schemaVersion' IS DISTINCT FROM delta->>'schemaVersion'
       OR COALESCE(snapshot->>'schemaVersion', '') NOT IN ('1', '2', '3') THEN
        RAISE EXCEPTION 'Attack display requires matching snapshot/delta schema 1, 2 or 3';
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
    result := jsonb_build_object('formatVersion', CASE WHEN delta->>'schemaVersion' = '3' THEN 2 ELSE 1 END,
        'sourceSchemaVersion', delta->'schemaVersion',
        'moveUci', delta->'moveUci', 'pieceChanges', delta->'pieceChanges');
    FOREACH field_name IN ARRAY ARRAY['addedRelations', 'removedRelations', 'addedControls', 'removedControls', 'addedXrayControls', 'removedXrayControls'] LOOP
        -- Missing historical control fields remain unknown, not calculated empty arrays.
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
            IF field_name LIKE '%XrayControls' THEN
                through_pieces := '[]'::jsonb;
                FOR through_id IN SELECT value FROM jsonb_array_elements_text(edge->'throughIds') LOOP
                    through_piece := states->through_id;
                    IF through_piece IS NULL THEN
                        RAISE EXCEPTION 'Unknown intermediate in %: %', field_name, through_id;
                    END IF;
                    through_pieces := through_pieces || jsonb_build_array(
                        through_piece || jsonb_build_object('id', through_id));
                END LOOP;
                edge := edge || jsonb_build_object('through', through_pieces);
            END IF;
            entries := entries || jsonb_build_array(edge);
        END LOOP;
        result := result || jsonb_build_object(field_name, entries);
    END LOOP;
    RETURN result;
END;
$$;
