-- SPDX-License-Identifier: MIT
-- Expand: the compare page has no chips of acts with counts now, so this release does not read or
-- write the counts of the differences that each act made in a comparison. The release that runs
-- while this one deploys still does. The next release drops the two tables.
comment on table text_step_count is
'deprecated: nothing reads or writes the step counts now. To be dropped in the next release.';
comment on table text_step_act_count is
'deprecated: nothing reads or writes the step counts now. To be dropped in the next release.';
