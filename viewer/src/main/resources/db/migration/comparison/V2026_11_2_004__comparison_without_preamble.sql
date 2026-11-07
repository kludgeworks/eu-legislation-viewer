-- SPDX-License-Identifier: MIT
-- A preamble one text has and the other hasn't is no longer compared (the Publications Office
-- publishes some consolidations without it): without_preamble, EARLIER or LATER, says which text
-- has none. Those kept with it compared are dropped, to be worked out again when next asked for
delete from comparison;
alter table comparison add column without_preamble text;
