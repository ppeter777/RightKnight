-- No defaults: historical rows remain unknown until their move features are recalculated.
ALTER TABLE game_move_analysis ADD COLUMN played_capture BOOLEAN;
ALTER TABLE game_move_analysis ADD COLUMN played_recapture BOOLEAN;
ALTER TABLE game_move_analysis ADD COLUMN played_gives_check BOOLEAN;
ALTER TABLE game_move_analysis ADD COLUMN played_promotion BOOLEAN;
ALTER TABLE game_move_analysis ADD COLUMN played_new_attack_targets JSONB;

ALTER TABLE game_move_analysis_candidate ADD COLUMN new_attack_targets JSONB;
