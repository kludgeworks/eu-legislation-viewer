-- SPDX-License-Identifier: MIT
-- An annex inserted whole is kept apart from its comparison, one row each, by its eId: one annex
-- can be tens of MB, and a comparison is read for every page of it, the annex only when the reader
-- opens its fold. The comparison keeps the annex's opening and its size (whole). Differences are
-- also now paired by likeness. So this drops the kept comparisons. The viewer works them out again
-- when they are next requested.
delete from comparison;
create table comparison_annex (
  from_root bytea not null,
  to_root bytea not null,
  e_id text not null,
  shown jsonb not null,
  primary key (from_root, to_root, e_id),
  foreign key (from_root, to_root) references comparison (from_root, to_root) on delete cascade
);
