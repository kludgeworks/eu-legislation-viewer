-- SPDX-License-Identifier: MIT
-- A deleted unit's difference now folds the units that stood before it, which the next difference's
-- no longer does (Difference.folded): those kept with the old runs are dropped, to be worked out
-- again when next asked for
delete from comparison;
