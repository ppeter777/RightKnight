-- No defaults/backfill: NULL means not calculated for existing analysis rows.
ALTER TABLE game_move_analysis ADD COLUMN in_check BOOLEAN;
ALTER TABLE game_move_analysis ADD COLUMN previous_move_capture BOOLEAN;
ALTER TABLE game_move_analysis ADD COLUMN recapture_moves_count INTEGER CHECK (recapture_moves_count >= 0);

ALTER TABLE game_move_analysis_candidate ADD COLUMN capture BOOLEAN;
ALTER TABLE game_move_analysis_candidate ADD COLUMN recapture BOOLEAN;
