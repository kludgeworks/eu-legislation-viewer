-- SPDX-License-Identifier: MIT
-- Contract: drops what v2026.11.6 deprecated, which it no longer reads or writes and which runs
-- while this release deploys:
--   - the step counts kept by the roots of their texts: text_step_count and text_step_act_count
--     keep them by the identities of their texts;
--   - the root and converter of an expression: the content module keeps the text by its version
--     and language, with its converter;
--   - when a version's Formex was modified: version_formex keeps the Formex of each language.
drop table step_act_count, step_count;
alter table expression drop column root, drop column converter;
alter table version drop column formex_modified;
