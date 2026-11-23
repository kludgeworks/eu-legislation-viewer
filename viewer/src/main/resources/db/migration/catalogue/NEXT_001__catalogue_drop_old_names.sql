-- SPDX-License-Identifier: MIT
-- Contract: drops the old names that v2026.11.18 deprecated, and the triggers that kept them equal
-- to the new ones. v2026.11.18, which runs while this release deploys, reads and writes only the
-- new names (progress, for_part, preparation_entry).
drop trigger expression_keep_progress on expression;
drop trigger preparation_keep_progress on preparation;
drop trigger act_date_keep_for_part on act_date;
drop trigger preparation_step_copy on preparation_step;
drop trigger preparation_entry_copy on preparation_entry;

drop function catalogue_keep_progress();
drop function catalogue_keep_for_part();
drop function catalogue_copy_step();
drop function catalogue_copy_entry();

drop table preparation_step;
alter table expression drop column state;
alter table preparation drop column state;
alter table act_date drop column in_part;
