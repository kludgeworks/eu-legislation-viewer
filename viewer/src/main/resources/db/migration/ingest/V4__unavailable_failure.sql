-- SPDX-License-Identifier: MIT
-- A failure for the Publications Office not answering, on every attempt (Failure.UNAVAILABLE)
alter table ingest_job drop constraint ingest_job_failure_check;
alter table ingest_job add constraint ingest_job_failure_check
  check (failure in ('NO_FORMEX', 'OUT_OF_SCOPE', 'MISFILED', 'UNSUPPORTED', 'UNAVAILABLE', 'ERROR'));
