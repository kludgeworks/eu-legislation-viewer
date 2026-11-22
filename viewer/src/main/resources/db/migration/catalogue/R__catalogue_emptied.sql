-- SPDX-License-Identifier: MIT
-- Emptied for: the converter's format, a tag's action is now its modification (2026-11-22).
-- Flyway runs this again each time its text changes. To empty the catalogue again, change the line
-- above to say why. Every act is then looked up again, and every text converted again. The
-- sequences go on, so no identity is used again (the HTML cache keys on the identities of texts).
-- The two rows that the catalogue needs are added again, as their migrations first added them.
truncate table
act,
act_amendment,
act_change,
act_date,
act_publication,
act_publication_title,
act_title,
catalogue_search_amendment,
catalogue_search_expression,
catalogue_search_extract_amendment,
catalogue_search_extract_citation,
catalogue_search_extract_concept,
catalogue_search_extract_label,
catalogue_search_extract_short_title,
catalogue_search_extract_title,
catalogue_search_extract_vocabulary,
catalogue_search_extract_work,
catalogue_search_rebuild,
catalogue_search_stage_amendment,
catalogue_search_stage_expression,
catalogue_search_stage_work,
catalogue_search_work,
change_scan,
expression,
preparation,
preparation_step,
version,
version_formex
cascade;

insert into catalogue_search_rebuild default values;
-- the first scan reads from the time of this migration, less the overlap
insert into change_scan (watermark, claimed_at) values (now(), '-infinity');
