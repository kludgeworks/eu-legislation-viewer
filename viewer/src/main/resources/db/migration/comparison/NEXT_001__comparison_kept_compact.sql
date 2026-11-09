-- SPDX-License-Identifier: MIT
-- A comparison is kept compact, because it is read for every page of it and one can be 150 MB as
-- jsonb (2005/396). kept: the comparison as JSON (differences, closing), with each unit's opening
-- once rather than in every difference in the unit, and each difference cut down as
-- Difference.kept gives it: an annex inserted whole to its opening, and the rows of its tables that
-- are the same in both texts folded. Postgres compresses it with lz4. It is bytea, not jsonb,
-- because the viewer reads it whole and Postgres need not parse it.
-- comparison_whole: the whole unit of each difference that is cut down, one row each, by its eId,
-- read only when the reader opens a fold. It replaces comparison_annex, which held only annexes.
-- The kept comparisons are dropped. The viewer works them out again when they are next requested.
delete from comparison;
drop table comparison_annex;
alter table comparison drop column differences, drop column closing;
alter table comparison add column kept bytea compression lz4 not null;
create table comparison_whole (
  from_root bytea not null,
  to_root bytea not null,
  e_id text not null,
  shown bytea compression lz4 not null,
  primary key (from_root, to_root, e_id),
  foreign key (from_root, to_root) references comparison (from_root, to_root) on delete cascade
);
