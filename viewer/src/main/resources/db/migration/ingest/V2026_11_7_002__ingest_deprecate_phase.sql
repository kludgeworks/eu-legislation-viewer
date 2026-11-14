-- SPDX-License-Identifier: MIT
-- A job no longer says what it is doing while it runs: a job takes seconds, and nothing read it.
-- The column is deprecated, and dropped in the next release: the release that runs while this one
-- deploys still writes it.
comment on column ingest_job.phase is
'deprecated: no longer written, nothing reads what a running job is doing. To be dropped in the next release.';

-- The comments name the events and the request as the code now does.
comment on table ingest_job is
'Each ingest job''s state: one per expression (CELEX and language). The queue is Modulith''s event registry (ConversionQueued). A worker starts a job (IngestJobs.start).';
comment on column ingest_job.traceparent is
'The trace of the request that asked for the text, which the job''s work continues (W3C Trace Context).';
