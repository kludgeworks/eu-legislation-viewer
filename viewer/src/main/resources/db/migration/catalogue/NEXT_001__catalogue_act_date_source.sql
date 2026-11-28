-- SPDX-License-Identifier: MIT
-- Record which source settled each ActDate (Cellar, clause and Journal, text, or not confirmed).
alter table act_date
add column source text not null default 'CELLAR'
check (source in ('CELLAR', 'CLAUSE_AND_JOURNAL', 'TEXT', 'NOT_CONFIRMED'));

comment on column act_date.source is
'Which source settled act_date.date: CELLAR, CLAUSE_AND_JOURNAL, TEXT, or NOT_CONFIRMED.';

-- Read every held act in the act table again (every row in act, because any act or its amending
-- acts may carry a publication-anchored date or missing own publication). Wait one hour so any
-- previous release still running stops before refresh reads them, without delaying acts due
-- sooner.
insert into act_change (act, first_seen, due_at)
select
  celex as act,
  now() as first_seen,
  now() + interval '1 hour' as due_at
from act
on conflict (act) do update set
  first_seen = coalesce(act_change.first_seen, excluded.first_seen),
  due_at = least(act_change.due_at, excluded.due_at);
