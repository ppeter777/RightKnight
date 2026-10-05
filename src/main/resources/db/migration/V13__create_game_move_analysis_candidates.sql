-- Existing Stage 1/2 installations already have game_move_analysis.
CREATE TABLE game_move_analysis_candidate (
    id BIGSERIAL PRIMARY KEY,
    game_move_analysis_id BIGINT NOT NULL REFERENCES game_move_analysis(id) ON DELETE CASCADE,
    pv_rank INTEGER NOT NULL CHECK (pv_rank > 0),
    move_uci VARCHAR(5) NOT NULL,
    eval_cp INTEGER,
    mate_in INTEGER,
    score_bound VARCHAR(5) NOT NULL CHECK (score_bound IN ('EXACT', 'LOWER', 'UPPER')),
    depth INTEGER NOT NULL CHECK (depth >= 0),
    selective_depth INTEGER,
    pv_uci TEXT NOT NULL,
    CONSTRAINT ck_move_candidate_score CHECK ((eval_cp IS NULL) <> (mate_in IS NULL)),
    CONSTRAINT uk_move_candidate_rank UNIQUE (game_move_analysis_id, pv_rank),
    CONSTRAINT uk_move_candidate_move UNIQUE (game_move_analysis_id, move_uci)
);
-- The rank uniqueness index also covers lookups by parent analysis id.
