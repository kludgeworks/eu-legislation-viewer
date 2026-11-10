-- SPDX-License-Identifier: MIT
-- Describes the tables in the database itself, for tools that read the schema (mise run
-- db:diagram). The text is from the comments of the migrations that made them.
comment on table ingest_job is
'Each ingest job''s state: one per expression (CELEX and language). The queue is Modulith''s event registry (ExpressionRequested). A worker starts a job (IngestJobs.start).';
comment on column ingest_job.traceparent is
'The trace of the request that scheduled the job, which the job''s work continues (W3C Trace Context).';
comment on column ingest_job.started_at is
'When the running attempt began. A job that runs longer than the takeover is taken over.';
comment on column ingest_job.converter is
'Once built, the converter that made the text, as ExpressionBuilt says.';
comment on column ingest_job.revision is
'The job''s optimistic lock: each update adds one.';
comment on column ingest_job.failure is
'Why the job failed (Failure): NO_FORMEX, OUT_OF_SCOPE, MISFILED (Formex that Cellar files under another document''s CELEX), UNSUPPORTED, UNAVAILABLE (the Publications Office did not answer on any attempt) or ERROR.';
comment on index ingest_job_queued is
'A page''s place in the queue: the jobs queued before its own.';
