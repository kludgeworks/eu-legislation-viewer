-- SPDX-License-Identifier: MIT
-- A unit a consolidation keeps struck under a deletion's marker is now deleted, not still in the
-- text, and a deleted unit names the act that deleted it (Difference.markers): those kept as they
-- were are dropped, to be worked out again when next asked for
delete from comparison;
