create table game_sync_state (
                                 id bigserial primary key,
                                 app_user_id bigint not null references app_users(id),
                                 lichess_username varchar(50) not null,

                                 sync_from timestamp with time zone not null,
                                 last_success_at timestamp with time zone,
                                 newest_game_created_at timestamp with time zone,
                                 newest_game_id varchar(255),

                                 status varchar(30) not null default 'IDLE',
                                 last_error text,

                                 created_at timestamp with time zone not null default now(),
                                 updated_at timestamp with time zone not null default now(),

                                 constraint uq_game_sync_state_app_user unique (app_user_id)
);

create index ix_game_sync_state_lichess_username
    on game_sync_state (lichess_username);

create index ix_games_user_id_created_at
    on games (user_id, created_at desc);