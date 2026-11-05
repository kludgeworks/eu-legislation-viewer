-- SPDX-License-Identifier: MIT
-- The comparison module's comparisons (Comparisons): how the text at from_root became the one at
-- to_root, by their roots in the content module (bytea, as it keeps them), worked out once and
-- kept. differences: the comparison's differences as JSON, in the later text's order
create table comparison (
  from_root bytea not null,
  to_root bytea not null,
  differences jsonb not null,
  compared_at timestamptz not null,
  primary key (from_root, to_root)
);
