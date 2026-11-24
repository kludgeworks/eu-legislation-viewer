-- SPDX-License-Identifier: MIT
-- Expand: the later acts that a date's comment names are of two kinds. An act that amends the
-- act of the date amended the date (2020/910 for 2019/103). Another act set the day that the
-- clause left to it (Decision 2023/201 for 2018/1862). amended_by keeps the first kind, and set_by
-- the second. The release that runs while this one deploys does not write set_by, so it has a
-- default.
alter table act_date add column set_by text[] not null default '{}';

comment on column act_date.amended_by is
'The CELEX of the later acts that amended the date: they amend the act of the date.';
comment on column act_date.set_by is
'The CELEX of the later acts that set the day that the clause left to them, for example a decision that sets the day from which a regulation applies.';
