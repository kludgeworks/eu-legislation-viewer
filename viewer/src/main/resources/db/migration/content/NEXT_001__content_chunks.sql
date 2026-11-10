-- SPDX-License-Identifier: MIT
-- The texts as chunks (model.Chunker) instead of a row per node: reading a long act walked tens of
-- thousands of rows. The texts stored so far are dropped, not converted: ingest builds them again.
-- A text's root, the hash of its tree, is the same as before.
drop table outline, root_segment, edge, overlay, node;

create table chunk (
  digest bytea primary key,
  body bytea not null
);

-- compressed already, so Postgres neither compresses it nor moves it again
alter table chunk alter column body set storage external;

comment on table chunk is
'A run of a text''s nodes (model.Chunker). Nothing in it changes once written, and texts share chunks.';
comment on column chunk.digest is 'The SHA-256 of the chunk''s JSON.';
comment on column chunk.body is 'The chunk''s JSON, compressed with zstd by the app.';

create table manifest (
  root bytea primary key,
  chunks bytea not null,
  outline jsonb not null
);

comment on table manifest is
'Each stored text by its root, the hash of its tree. Written after its chunks, in the same transaction.';
comment on column manifest.chunks is 'The hashes of the text''s chunks, 32 bytes each, the top chunk first.';
comment on column manifest.outline is 'The text''s outline (Content.outline), as JSON.';
