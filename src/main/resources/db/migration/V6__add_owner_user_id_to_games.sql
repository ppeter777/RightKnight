alter table games
    add column owner_user_id bigint references app_users(id);