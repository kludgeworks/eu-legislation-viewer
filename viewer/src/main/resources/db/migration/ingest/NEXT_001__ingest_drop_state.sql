-- SPDX-License-Identifier: MIT
-- Contract: drops ingest_job.state and its index, which v2026.11.19 deprecated, and the trigger
-- that kept state equal to progress. v2026.11.19, which runs while this release deploys, reads and
-- writes only progress, so every job has one. A job starts queued, so progress has that default:
-- with it, progress can be not null while v2026.11.19 still runs.
drop trigger ingest_job_keep_progress on ingest_job;
drop function ingest_keep_progress();

drop index ingest_job_queued;
alter table ingest_job drop column state;
alter table ingest_job alter column progress set default 'QUEUED';
alter table ingest_job alter column progress set not null;
