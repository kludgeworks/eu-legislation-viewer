-- SPDX-License-Identifier: MIT
-- Contract: drops the table expression, which v2026.11.19 deprecated, and the triggers that copied
-- it to text_progress and back. v2026.11.19, which runs while this release deploys, reads and
-- writes only text_progress.
drop trigger expression_copy on expression;
drop trigger text_progress_copy on text_progress;

drop function catalogue_copy_expression();
drop function catalogue_copy_text_progress();

drop table expression;
