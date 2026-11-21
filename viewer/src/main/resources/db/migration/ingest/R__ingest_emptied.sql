-- SPDX-License-Identifier: MIT
-- Emptied for: the converter's format, unit is now occurrence (2026-11-21).
-- Flyway runs this again each time its text changes. To empty the jobs again, change the line above
-- to say why. The lookups of the catalogue queue the conversions again.
truncate table ingest_job cascade;
