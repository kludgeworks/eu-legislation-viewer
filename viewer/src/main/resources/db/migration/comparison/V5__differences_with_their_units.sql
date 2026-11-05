-- SPDX-License-Identifier: MIT
-- A comparison's differences now carry their own units as a fold names them (Difference.unit),
-- which an act filter folds the others' by: those kept without them are dropped, to be worked out
-- again when next asked for
delete from comparison;
