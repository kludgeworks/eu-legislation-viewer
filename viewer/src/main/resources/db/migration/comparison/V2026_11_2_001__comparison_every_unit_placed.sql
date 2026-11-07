-- SPDX-License-Identifier: MIT
-- Each of the later text's units is now placed once, between the differences (Difference.context)
-- or after the last (closing), where a difference folded only the units before it in its own unit:
-- those kept with the old runs are dropped, to be worked out again when next asked for
delete from comparison;
alter table comparison add column closing jsonb not null;
