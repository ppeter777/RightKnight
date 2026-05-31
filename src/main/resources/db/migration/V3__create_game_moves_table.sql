create table game_moves (
                            id bigserial primary key,
                            game_id varchar(255) not null references games(id) on delete cascade,

                            ply int not null,
                            move_number int not null,
                            white_move boolean not null,

                            san varchar(30),
                            uci varchar(10),

                            fen_before text,
                            fen_after text,

                            clock_before_ms bigint,
                            clock_after_ms bigint,
                            move_time_ms bigint,

                            constraint uk_game_moves_game_ply unique (game_id, ply)
);

create index idx_game_moves_game_id on game_moves(game_id);