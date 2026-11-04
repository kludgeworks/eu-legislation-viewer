-- SPDX-License-Identifier: MIT
-- A failure for Formex that Cellar files under another document's CELEX number (Failure.MISFILED)
alter table ingest_job drop constraint ingest_job_failure_check;
alter table ingest_job add constraint ingest_job_failure_check
  check (failure in ('NO_FORMEX', 'OUT_OF_SCOPE', 'MISFILED', 'UNSUPPORTED', 'ERROR'));
