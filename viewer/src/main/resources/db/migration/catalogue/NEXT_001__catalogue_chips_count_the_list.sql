-- SPDX-License-Identifier: MIT
-- The chips now count the comparison the page lists, not each step between its versions, and a
-- changed provision with those in it that the same acts changed is one difference: the counts kept
-- are dropped, to be counted again when next asked for
delete from step_count;
