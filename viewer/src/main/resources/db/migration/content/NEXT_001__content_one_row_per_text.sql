-- SPDX-License-Identifier: MIT
-- Each text is one row, its whole JSON (model.Encoder) compressed with zstd by the app, in place of
-- chunks that versions share: sharing saved disk, but its chunks, manifests and hashes were too
-- complex to keep. A text is known by an identity that is never used again, not by a hash.
-- The chunks and manifests are deprecated, and dropped in the next release: the release that
-- runs while this one deploys still writes them. The texts are not converted: the catalogue
-- prepares them again (catalogue NEXT_001), and ingest stores them here.

create table text (
  id bigint generated always as identity primary key,
  celex text not null,
  language_code text not null,
  digest bytea not null,
  converter text not null,
  stored_at timestamptz not null,
  outline jsonb not null,
  body bytea not null,
  unique (celex, language_code)
);

-- compressed already, so Postgres neither compresses it nor moves it again
alter table text alter column body set storage external;

comment on table text is
'The text of a version in a language (Content): the converter''s output, whole. A text stored again with another JSON replaces the row, with a new id (TextReplaced).';
comment on column text.id is 'The text''s identity (TextId). It is never used again.';
comment on column text.celex is 'The CELEX of the version, for example 02015R1998-20260322.';
comment on column text.digest is 'The SHA-256 of the text''s JSON, before it is compressed.';
comment on column text.converter is 'The version of the converter that made the text.';
comment on column text.outline is 'The text''s outline (Content.outline), as JSON.';
comment on column text.body is
'The text''s element tree as JSON (model.Encoder), with its eIds and note references, compressed with zstd by the app.';
comment on table chunk is
'deprecated: the texts are one row each in text. To be dropped in the next release.';
comment on table manifest is
'deprecated: the texts are one row each in text. To be dropped in the next release.';
