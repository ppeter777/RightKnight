create table app_users (
    id bigserial primary key,
    username varchar(50) not null,
    email varchar(255),
    password_hash varchar(255) not null,
    display_name varchar(100),
    role varchar(30) not null default 'USER',
    enabled boolean not null default true,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create unique index ux_app_users_username_lower
    on app_users (lower(username));

create unique index ux_app_users_email_lower
    on app_users (lower(email))
    where email is not null;
