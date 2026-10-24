-- NULL means not calculated; historical V16 data is preserved without backfill.
ALTER TABLE game_move_analysis ADD COLUMN attack_snapshot_before JSONB;
ALTER TABLE game_move_analysis ADD COLUMN played_attack_delta JSONB;
ALTER TABLE game_move_analysis ADD COLUMN attack_features_version INTEGER CHECK (attack_features_version > 0);
ALTER TABLE game_move_analysis_candidate ADD COLUMN attack_delta JSONB;
