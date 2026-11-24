-- SPDX-License-Identifier: MIT
-- Expand: an amendment keeps its place in words, as EUR-Lex names it: each code of Cellar's
-- location in its words in Cellar's table of subdivisions (fd_370), and each other word as Cellar
-- writes it. The words come from Cellar with the location. The release that runs while this one
-- deploys does not write place, so it has a default. R__catalogue_emptied empties the catalogue, so
-- that every amendment is read again with its place.
alter table act_amendment add column place text not null default '';

comment on column act_amendment.place is
'The place in words, as EUR-Lex names it (annex point 1.3.1.1 point (g)): each code of cellar_location in the words that Cellar gives for it, and each other word as Cellar writes it.';
