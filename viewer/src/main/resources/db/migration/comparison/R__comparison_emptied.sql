-- SPDX-License-Identifier: MIT
-- Emptied for: a converted text saves the locations of its image files (2026-11-27).
-- Flyway runs this again each time its text changes. To empty the comparisons again, change the
-- line above to say why. They are computed again from the texts.
truncate table text_comparison, text_comparison_whole cascade;
