-- SPDX-License-Identifier: MIT
-- The chips now count the comparison that the page lists, not each step between its versions.
-- Also, a changed provision and the provisions in it that the same acts changed are now one
-- difference. So this drops the kept counts. The viewer counts them again when they are next
-- requested.
delete from step_count;
