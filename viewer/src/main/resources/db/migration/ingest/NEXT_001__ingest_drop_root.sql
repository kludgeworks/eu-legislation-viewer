-- SPDX-License-Identifier: MIT
-- Contract: drops the root and converter of a job, which v2026.11.6 deprecated: the content module
-- keeps the text by its version and language, with its converter. v2026.11.6, which runs while this
-- release deploys, no longer writes them.
alter table ingest_job drop column root, drop column converter;
