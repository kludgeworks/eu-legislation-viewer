-- SPDX-License-Identifier: MIT
-- A comparison is kept by the identities of its two texts in the content module (TextId), not by
-- their roots. There is no foreign key to the content module's table, so that the schemas of the
-- two modules stay apart: the comparisons of a text that is replaced are deleted on TextReplaced.
-- differ_version: the version of the rules that computed it (model.DifferVersion, from their
-- sources at build time). A comparison of another version is computed again when it is read, so a
-- change to the rules needs no migration that deletes the comparisons.
-- text_comparison and text_comparison_whole replace comparison and comparison_whole, which are
-- kept by roots. Those are deprecated, and dropped in the next release: the release that runs
-- while this one deploys still reads and writes them. The comparisons are not converted: they
-- are computed again.
create table text_comparison (
  from_text bigint not null,
  to_text bigint not null,
  differ_version text not null,
  kept bytea not null,
  without_preamble text check (without_preamble in ('EARLIER', 'LATER')),
  compared_at timestamptz not null,
  primary key (from_text, to_text)
);

alter table text_comparison alter column kept set compression lz4;

-- the comparisons to a text that is replaced, which are deleted
create index text_comparison_to_text on text_comparison (to_text);

create table text_comparison_whole (
  from_text bigint not null,
  to_text bigint not null,
  e_id text not null,
  shown bytea not null,
  primary key (from_text, to_text, e_id),
  foreign key (from_text, to_text) references text_comparison (from_text, to_text)
  on delete cascade
);

alter table text_comparison_whole alter column shown set compression lz4;

comment on table text_comparison is
'How the text from_text became the text to_text (Comparisons), by their identities in the content module (TextId), computed once and kept. The comparisons of a text are deleted when it is replaced (TextReplaced).';
comment on column text_comparison.from_text is 'The earlier text, by its identity in the content module (TextId).';
comment on column text_comparison.to_text is 'The later text, by its identity in the content module (TextId).';
comment on column text_comparison.differ_version is
'The version of the rules that computed it (model.DifferVersion). A comparison of another version is computed again when it is read.';
comment on column text_comparison.kept is
'The comparison as JSON (differences, closing), each unit''s opening once, each difference cut down as Difference.kept gives it.';
comment on column text_comparison.without_preamble is
'EARLIER or LATER: the text that has no preamble, which is then not compared. Null if both have one or neither.';
comment on index text_comparison_to_text is 'The comparisons to a text that is replaced, which are deleted.';
comment on table text_comparison_whole is
'The whole unit of each difference that is cut down in a comparison, by its eId, read only when the reader opens a fold.';
comment on table comparison is
'deprecated: text_comparison keeps the comparisons by the identities of their texts. To be dropped in the next release.';
comment on table comparison_whole is
'deprecated: text_comparison_whole keeps them by the identities of their texts. To be dropped in the next release.';
