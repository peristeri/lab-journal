create table if not exists account (
  id text not null primary key,
  username text unique not null,
  password text not null,
  role text not null,
  created_at datetime default current_timestamp,
  is_active boolean default true,
  check (role in ("researcher", "worker"))
);

create table if not exists research (
  id text not null primary key,
  leader text not null,
  title text not null,
  specification text not null,
  data_repository text not null,
  version text not null,
  created_at datetime default current_timestamp,
  is_active boolean default true,
  foreign key(leader) references account(id)
);
