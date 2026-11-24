-- SPDX-License-Identifier: MIT
-- Emptied for: an amendment keeps its place in words, as EUR-Lex names it, which only Cellar
-- gives (2026-11-24).
-- Flyway runs this again each time its text changes. To empty the catalogue again, change the line
-- above to say why. Every act is then looked up again, and every text converted again. The
-- sequences go on, so no identity is used again (the HTML cache keys on the identities of texts).
-- It truncates only the tables that exist, so a migration that drops a table does not have to
-- change this script, and does not empty the catalogue. The two rows that the catalogue needs are
-- added again, as their migrations first added them.
do $$
declare
  existing text;
begin
  select string_agg(format('%I', name), ', ' order by name)
  into existing
  from unnest(array[
      'act',
      'act_amendment',
      'act_change',
      'act_date',
      'act_publication',
      'act_publication_title',
      'act_title',
      'catalogue_search_amendment',
      'catalogue_search_expression',
      'catalogue_search_extract_amendment',
      'catalogue_search_extract_citation',
      'catalogue_search_extract_concept',
      'catalogue_search_extract_label',
      'catalogue_search_extract_short_title',
      'catalogue_search_extract_title',
      'catalogue_search_extract_vocabulary',
      'catalogue_search_extract_work',
      'catalogue_search_rebuild',
      'catalogue_search_stage_amendment',
      'catalogue_search_stage_expression',
      'catalogue_search_stage_work',
      'catalogue_search_work',
      'change_scan',
      'expression',
      'preparation',
      'preparation_entry',
      'version',
      'version_formex'
  ]) as name
  where to_regclass(name) is not null;
  if existing is not null then
    execute format('truncate table %s cascade', existing);
  end if;
end $$;

insert into catalogue_search_rebuild default values;
-- the first scan reads from the time of this migration, less the overlap
insert into change_scan (watermark, claimed_at) values (now(), '-infinity');
