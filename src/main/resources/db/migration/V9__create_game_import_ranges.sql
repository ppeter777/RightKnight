create table game_import_ranges (
                                    id bigserial primary key,
                                    app_user_id bigint not null references app_users(id),
                                    lichess_username varchar(50) not null,

                                    range_from timestamp with time zone not null,
                                    range_until timestamp with time zone not null,

                                    status varchar(30) not null default 'SUCCESS',
                                    games_imported integer not null default 0,
                                    last_error text,

                                    created_at timestamp with time zone not null default now(),
                                    updated_at timestamp with time zone not null default now()
);

create index ix_game_import_ranges_user_range
    on game_import_ranges (app_user_id, range_from, range_until);

create index ix_game_import_ranges_status
    on game_import_ranges (status);