-- SPDX-License-Identifier: MIT
-- The ingest module's queue: one job per expression (CELEX and language), claimed by a worker with
-- SKIP LOCKED and held while its heartbeat is fresh (IngestJobs)
create table ingest_job (
  id bigint generated always as identity primary key,
  celex text not null,
  language text not null,
  state text not null check (state in ('QUEUED', 'RUNNING', 'BUILT', 'FAILED')),
  stage text check (stage in ('CONVERTING', 'STORING')),
  attempts int not null,
  owner text,
  heartbeat_at timestamptz,
  -- the trace of the request that scheduled it, which the job's work continues (W3C Trace Context)
  traceparent text,
  root text,
  failure text check (failure in ('NO_FORMEX', 'OUT_OF_SCOPE', 'UNSUPPORTED', 'ERROR')),
  detail text,
  created_at timestamptz not null,
  finished_at timestamptz,
  version bigint not null,
  unique (celex, language)
);

-- the claim's: the oldest open job first
create index ingest_job_open on ingest_job (created_at) where state in ('QUEUED', 'RUNNING');
