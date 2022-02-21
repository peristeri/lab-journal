create table account (
  id text not null primary key,
  username text unique not null,
  password text not null,
  role text not null,
  check (role in ("researcher", "worker"))
);

create table research (
  id text not null primary key,
  leader text not null,
  title text not null,
  hypothesis text not null,
  foreign key(leader) references account(id)
);
