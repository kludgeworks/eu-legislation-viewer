-- SPDX-License-Identifier: MIT
-- The glossary calls a subdivision as one text has it an occurrence, not a unit.
comment on column text_comparison.kept is
'The comparison as JSON (differences, closing), each occurrence''s opening once, each difference cut down as Difference.kept gives it.';
comment on table text_comparison_whole is
'The whole occurrence of each difference that is cut down in a comparison, by its eId, read only when the reader opens a fold.';
