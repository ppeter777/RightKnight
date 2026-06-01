alter table app_users
    add column lichess_username varchar(50);

create unique index ux_app_users_lichess_username_lower
    on app_users (lower(lichess_username))
    where lichess_username is not null;