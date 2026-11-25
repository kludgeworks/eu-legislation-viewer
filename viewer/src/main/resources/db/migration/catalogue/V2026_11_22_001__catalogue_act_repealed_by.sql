-- SPDX-License-Identifier: MIT
-- The acts that repeal an act, by their CELEX, as Cellar says (Act.repealedBy). Empty if none does.
alter table act add column repealed_by text[] not null default '{}';

-- Read again, an hour from now, each held act that is no longer in force: an act read before this
-- release has no acts that repeal it
insert into act_change (act, first_seen, due_at)
select
  celex as act,
  now() as first_seen,
  now() + interval '1 hour' as due_at
from act
where not in_force
on conflict (act) do update set
  first_seen = coalesce(act_change.first_seen, excluded.first_seen),
  due_at = excluded.due_at;
