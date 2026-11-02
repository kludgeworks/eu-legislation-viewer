-- SPDX-License-Identifier: MIT
-- The content module's tree (the design: Read model rewrite, the content model). Nothing in it
-- changes once written: every row is keyed by the SHA-256 of what it holds, so a write that finds
-- its key taken has nothing to do.

-- A leaf's JSON, or a container's name and attributes; weight, the bytes of its leaves' JSON
create table node (
  hash bytea primary key,
  kind text not null check (kind in ('LEAF', 'CONTAINER')),
  content jsonb not null,
  weight bigint not null,
  child_count int not null
);

-- A container's children in order, with the overlay of what was taken out of each, and
-- how much of the parent comes before it
create table edge (
  parent bytea not null references node,
  seq int not null,
  child bytea not null references node,
  overlay bytea,
  offset_weight bigint not null,
  primary key (parent, seq)
);

-- The facts taken out of a node (its eIds), as JSON
create table overlay (
  hash bytea primary key,
  facts jsonb not null
);

alter table edge add foreign key (overlay) references overlay;

-- Each stored text's segments in order, an index on its tree (not part of it): what a reader's page
-- loads, one window at a time, each a run of one container's children [from_seq, to_seq); hash, the
-- run's identity, from its children's
create table root_segment (
  root bytea not null references node,
  ordinal int not null,
  hash bytea not null,
  parent bytea not null references node,
  from_seq int not null,
  to_seq int not null,
  offset_weight bigint not null,
  weight bigint not null,
  primary key (root, ordinal)
);
