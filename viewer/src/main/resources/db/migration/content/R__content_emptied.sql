-- SPDX-License-Identifier: MIT
-- Emptied for: a converted text saves the locations of its image files (2026-11-27).
-- Flyway runs this again each time its text changes. To empty the store of texts again, change the
-- line above to say why. The sequence goes on, so no text identity is used again (the HTML cache
-- keys on it).
truncate table text cascade;
