-- SPDX-License-Identifier: MIT
-- The queue is Modulith's event registry now (ExpressionRequested): ingest_job keeps each job's
-- state, a worker starting it (IngestJobs.start) in place of claiming it, and no heartbeat
alter table ingest_job drop column owner;
alter table ingest_job drop column heartbeat_at;
-- when the attempt running began: one running longer than the takeover is taken over
alter table ingest_job add column started_at timestamptz;
-- once built, the converter that made it, as ExpressionBuilt says
alter table ingest_job add column converter text;
drop index ingest_job_open;
-- a page's place in the queue: the jobs queued before its own
create index ingest_job_queued on ingest_job (created_at) where state = 'QUEUED';
