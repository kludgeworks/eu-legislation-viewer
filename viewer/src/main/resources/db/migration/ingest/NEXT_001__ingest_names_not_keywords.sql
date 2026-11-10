-- SPDX-License-Identifier: MIT
-- Renames the columns whose names are SQL keywords (SQLFluff's RF04), so that every name in the
-- schema is a plain identifier. language becomes language_code: it holds a code (ENG, FRA …).
-- revision is the job's optimistic lock: each update adds one.
alter table ingest_job rename column language to language_code;
alter table ingest_job rename column stage to phase;
alter table ingest_job rename column version to revision;
