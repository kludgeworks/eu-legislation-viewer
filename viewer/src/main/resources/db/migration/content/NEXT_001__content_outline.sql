-- SPDX-License-Identifier: MIT
-- The outline of each text (Content.outline), as JSON, by the root of the text: worked out when the
-- text is stored, or the first time a text stored before it is outlined. Reading it back from the
-- text's skeleton walked every container, each row of a table too, on every page of a long act
create table outline (
  root bytea primary key references node (hash),
  outline jsonb not null
);
