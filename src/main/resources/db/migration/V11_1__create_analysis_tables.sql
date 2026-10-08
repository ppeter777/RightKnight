-- These tables were previously created by Hibernate's ddl-auto=update in dev.
-- Production must have them before V12 and V13 run.
-- IF NOT EXISTS also permits one-time out-of-order adoption by existing dev DBs.
-- Do not add engine_version to an existing table: V12 may already have removed it.
CREATE TABLE IF NOT EXISTS game_analysis (
    id BIGSERIAL PRIMARY KEY,
    game_id VARCHAR(255) NOT NULL REFERENCES games(id),
    engine_name VARCHAR(255),
    engine_version VARCHAR(255), -- Historical column removed by the unchanged V12.
    requested_depth INTEGER,
    multi_pv INTEGER,
    threads INTEGER,
    created_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS game_move_analysis (
    id BIGSERIAL PRIMARY KEY,
    game_analysis_id BIGINT NOT NULL REFERENCES game_analysis(id),
    game_move_id BIGINT NOT NULL REFERENCES game_moves(id),
    legal_moves_count INTEGER,
    capture_moves_count INTEGER,
    check_moves_count INTEGER,
    promotion_moves_count INTEGER,
    depth INTEGER,
    selective_depth INTEGER,
    nodes BIGINT,
    engine_time_ms BIGINT,
    best_eval_cp INTEGER,
    best_mate_in INTEGER,
    played_move_eval_cp INTEGER,
    played_move_mate_in INTEGER,
    loss_cp INTEGER,
    CONSTRAINT uk_game_move_analysis_run_move UNIQUE (game_analysis_id, game_move_id)
);
