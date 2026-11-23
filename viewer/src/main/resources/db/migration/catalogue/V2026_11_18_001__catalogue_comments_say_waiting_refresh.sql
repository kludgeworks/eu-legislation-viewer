-- SPDX-License-Identifier: MIT
-- The record of a held act that changed in Cellar is now WaitingRefresh: "pending" names a pending
-- amendment only.
comment on table act_change is
'Each held act that the scan saw change (WaitingRefresh). It is read again (ActChanged) after 10 quiet minutes, at most 60 minutes after the first change.';
