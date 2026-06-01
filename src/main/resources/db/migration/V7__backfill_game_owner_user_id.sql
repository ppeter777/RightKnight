update games g
set owner_user_id = u.id
    from app_users u
where g.owner_user_id is null
  and u.lichess_username is not null
  and lower(g.user_id) = lower(u.lichess_username);