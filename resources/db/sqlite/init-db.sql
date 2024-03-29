create table if not exists account (
  id text not null primary key,
  username text unique not null,
  password text not null,
  role text not null,
  created_at datetime default current_timestamp,
  is_active boolean default true,
  check (role in ("researcher", "worker"))
);

-- The definition of a research project.
create table if not exists research (
  id text not null primary key,
  -- The person leading the research
  leader text not null,
  title text not null,
  -- A json object with the specification of the data corpus
  specification text not null,
  -- A git repository of the code that will build the research models
  application_repository text not null,
  created_at datetime default current_timestamp,
  is_active boolean default true,
  foreign key(leader) references account(id)
);

create table if not exists dataset (
  id text not null primary key,
  title text not null,
  authors text not null,
  license text not null,
  -- The url where the original dataset description resides
  url text not null,
  -- The dataset identifier
  doi text not null,
  -- The version of the dataset that is currently in the lab's storage
  version text not null,
  created_at datetime default current_timestamp,
  is_active boolean default true,
  unique(url, version)
);


create table if not exists permissions (
  id text not null primary key,
  account_id text not null,
  dataset_id text not null,
  permissions text not null,
  unique(account_id, dataset_id),
  check (permissions in ("read", "denied"))
)

create table if not exists experiment (
  research_id text not null,
  dataset_id text not null,
  -- A research can have multiple experiments. There's no constraints, we want to record retry also.
  application_version text not null default "HEAD",
  results text,
  status text default "pending",
  foreign key(research_id) references research(id),
  foreign key(dataset_id) references dataset(id),
  check(status in ("pending", "in progress", "completed", "failed"))
);

create table if not exists image (
  id text not null primary key,
  url text unique not null,
  anatomy text,
  is_labelled boolean default false,
  -- The orientation of point of origin [0 0 0]
  orientation text,
  -- size of the image in mm
  size text,
  -- dimension in pixel count
  dimension text,
  details text,
  created_at datetime default current_timestamp,
  dataset_id text not null,
  foreign key(dataset_id) references dataset(id)
);
