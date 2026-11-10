-- SPDX-License-Identifier: MIT
-- breaking: columns named after SQL keywords are renamed in place
-- Renames the columns whose names are SQL keywords (SQLFluff's RF04), so that every name in the
-- schema is a plain identifier. Postgres reserves none of them, so they worked, but read as
-- keywords. language becomes language_code: it holds a code (ENG, FRA …).
alter table version rename column date to starts_on;
alter table expression rename column language to language_code;
alter table act_title rename column language to language_code;
alter table act_publication_title rename column language to language_code;
alter table preparation rename column language to language_code;
alter table preparation_step rename column language to language_code;
alter table preparation_step rename column at to happened_at;
alter index preparation_step_at rename to preparation_step_happened_at;
alter table act_date rename column position to ordinal;
alter table act_date rename column date to falls_on;
alter table act_date rename column partial to in_part;
alter table act_date rename column days to days_after;
alter table act_date rename column text to remark;
alter table act_amendment rename column position to ordinal;
alter table act_amendment rename column location to cellar_location;
alter table act_amendment rename column role to kind;
alter table step_count rename column unknown to unattributed;
