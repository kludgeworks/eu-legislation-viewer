-- SPDX-License-Identifier: MIT
-- A version's text in a language as it stands, from when its act is first prepared in the language
-- (Texts.prepare), not only once built: queued, being built (attempt, the job's), built, or failed
-- (failure, why), as ingest's events say. Those kept so far are built.
alter table expression
  add column state text not null default 'BUILT'
    check (state in ('QUEUED', 'BUILDING', 'BUILT', 'FAILED')),
  add column attempt integer not null default 0,
  add column failure text,
  alter column root drop not null,
  alter column converter drop not null,
  alter column built_at drop not null,
  add check ((state = 'BUILT') = (root is not null and converter is not null)),
  add check ((state = 'FAILED') = (failure is not null));

alter table expression alter column state drop default;
