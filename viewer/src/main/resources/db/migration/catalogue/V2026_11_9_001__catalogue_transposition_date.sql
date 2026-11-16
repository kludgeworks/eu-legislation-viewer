-- SPDX-License-Identifier: MIT
-- A date from which the Member States apply a directive (ActDate.Kind.TRANSPOSITION)
alter table act_date drop constraint act_date_kind_check;
alter table act_date add constraint act_date_kind_check
check (kind in ('ENTRY_INTO_FORCE', 'APPLICATION', 'TAKING_EFFECT', 'TRANSPOSITION'));

-- Read every held act again, an hour from now, when the release that runs while this one deploys
-- has stopped: an act read before this release has no dates of transposition, and no dates and
-- publications of the acts that amend it but that no version includes yet
insert into act_change (act, first_seen, due_at)
select
  celex as act,
  now() as first_seen,
  now() + interval '1 hour' as due_at
from act
on conflict (act) do update set
  first_seen = coalesce(act_change.first_seen, excluded.first_seen),
  due_at = excluded.due_at;
