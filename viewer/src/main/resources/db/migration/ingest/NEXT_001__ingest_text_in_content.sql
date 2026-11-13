-- SPDX-License-Identifier: MIT
-- A job no longer keeps the root of the text it built, nor its converter: the content module keeps
-- the text by its version and language, with the converter that made it. The columns are
-- deprecated, and dropped in the next release: the release that runs while this one deploys
-- still writes them. The jobs are deleted, because the catalogue prepares every text again
-- (catalogue NEXT_001).
delete from ingest_job;

comment on column ingest_job.root is
'deprecated: the content module keeps the text by its version and language. To be dropped in the next release.';
comment on column ingest_job.converter is
'deprecated: the content module keeps the converter of each text. To be dropped in the next release.';
