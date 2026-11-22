-- SPDX-License-Identifier: MIT
-- Emptied for: the converter's format, a tag's action is now its modification (2026-11-22).
-- Flyway runs this again each time its text changes. To empty the jobs again, change the line above
-- to say why. The lookups of the catalogue queue the conversions again.
truncate table ingest_job cascade;
