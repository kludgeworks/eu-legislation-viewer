-- SPDX-License-Identifier: MIT
-- Contract: drops what a running job is doing, which v2026.11.7 deprecated: a job takes seconds,
-- and nothing read it. v2026.11.7, which runs while this release deploys, no longer writes it.
alter table ingest_job drop column phase;
