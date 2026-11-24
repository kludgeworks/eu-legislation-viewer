-- SPDX-License-Identifier: MIT
-- Expand: the glossary (2026-11-24). "state" is an act's legal state, and how far a text is, is
-- its "progress". So ingest_job.state is ingest_job.progress, with the same values.
--
-- The release that runs while this one deploys still reads and writes state. So state stays,
-- deprecated, and a trigger keeps the two columns equal, whichever release writes. The next
-- release drops state, its index and the trigger.
alter table ingest_job
add column progress text check (progress in ('QUEUED', 'RUNNING', 'BUILT', 'FAILED'));
update ingest_job set progress = state;
alter table ingest_job alter column state drop not null;

-- a page's place in the queue: the jobs queued before its own
create index ingest_job_queued_by_progress on ingest_job (created_at) where progress = 'QUEUED';

-- Keeps the deprecated column and its new column equal, whichever of the two releases writes.
create function ingest_keep_progress() returns trigger language plpgsql as $$
begin
  if tg_op = 'INSERT' then
    new.progress := coalesce(new.progress, new.state);
    new.state := coalesce(new.state, new.progress);
  elsif new.state is distinct from old.state then
    new.progress := new.state;
  elsif new.progress is distinct from old.progress then
    new.state := new.progress;
  end if;
  return new;
end $$;

create trigger ingest_job_keep_progress before insert or update on ingest_job
for each row execute function ingest_keep_progress();

comment on table ingest_job is
'Each ingest job''s progress: one per text (CELEX and language). The queue is Modulith''s event registry (ConversionQueued). A worker starts a job (IngestJobs.start).';
comment on column ingest_job.progress is
'How far the job is: queued, running (started_at), built, or failed (failure).';
comment on column ingest_job.state is
'deprecated: ingest_job.progress keeps it. To be dropped in the next release.';
comment on index ingest_job_queued is
'deprecated: ingest_job_queued_by_progress replaces it. To be dropped in the next release.';
comment on function ingest_keep_progress() is
'deprecated: keeps ingest_job.state equal to ingest_job.progress until the next release drops state.';
