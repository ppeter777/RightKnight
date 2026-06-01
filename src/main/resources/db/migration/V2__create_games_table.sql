create table games (
                       id varchar(255) primary key,
                       user_id varchar(255),
                       opponent_id varchar(255),
                       user_rating integer not null,
                       opponent_rating integer not null,
                       score real not null,
                       mode varchar(255),
                       white boolean not null,
                       rated boolean not null,
                       created_at timestamp with time zone,
                       pgn text,
                       opening_name varchar(255),
                       opening_eco varchar(255),
                       clock_limit varchar(255)
);

create index ix_games_user_id
    on games (user_id);

create index ix_games_created_at
    on games (created_at);

create index ix_games_opening_name
    on games (opening_name);