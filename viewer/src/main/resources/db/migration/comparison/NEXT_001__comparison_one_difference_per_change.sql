-- SPDX-License-Identifier: MIT
-- A changed provision and the provisions in it that the same acts changed are now one difference.
-- A num that only gained a full stop is now no difference. So this drops the kept comparisons. The
-- viewer works them out again when they are next requested.
delete from comparison;
