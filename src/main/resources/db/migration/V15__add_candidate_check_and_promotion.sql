-- Historical candidates remain NULL (not calculated).
ALTER TABLE game_move_analysis_candidate ADD COLUMN gives_check BOOLEAN;
ALTER TABLE game_move_analysis_candidate ADD COLUMN promotion BOOLEAN;
