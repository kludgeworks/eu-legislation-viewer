-- SPDX-License-Identifier: MIT
-- The type of a column that holds the CELEX of an act (ActIdentifier), and of a column that holds
-- the code of a language (Language): jOOQ generates each column of these types as an
-- ActIdentifier or a Language, so a new column needs no entry in jooq.xml. A domain without
-- constraints reads and writes as text, so the release that runs while this one deploys sees no
-- change, and no table is written again. The CELEX of a version (version.celex) is not an act's,
-- so it stays text.
create domain act_identifier as text;
comment on domain act_identifier is 'The CELEX of an act, for example 32015R1998 (ActIdentifier).';

alter table act alter column celex type act_identifier;
alter table version alter column act type act_identifier;
alter table version_formex alter column act type act_identifier;
alter table act_change alter column act type act_identifier;
alter table act_date
alter column act type act_identifier,
alter column subject type act_identifier;
alter table act_title alter column act type act_identifier;
alter table act_amendment
alter column act type act_identifier,
alter column amending type act_identifier;
alter table act_publication
alter column act type act_identifier,
alter column subject type act_identifier;
alter table act_publication_title
alter column act type act_identifier,
alter column subject type act_identifier;
alter table preparation alter column act type act_identifier;
alter table preparation_entry alter column act type act_identifier;

-- A generated column stops a change of the type of the column it is made from: the two numbers of
-- the index of acts are made again, with their index.
alter table catalogue_search_work drop column act_year, drop column natural_number;
alter table catalogue_search_work alter column celex type act_identifier;
alter table catalogue_search_work
add column act_year integer generated always as (substring(celex, 2, 4)::integer) stored,
add column natural_number integer generated always as (right(celex, 4)::integer) stored;
create index catalogue_search_work_number on catalogue_search_work (act_year, natural_number);
alter table catalogue_search_stage_work alter column celex type act_identifier;
alter table catalogue_search_expression alter column celex type act_identifier;
alter table catalogue_search_stage_expression alter column celex type act_identifier;
alter table catalogue_search_amendment
alter column act type act_identifier,
alter column amends type act_identifier;
alter table catalogue_search_stage_amendment
alter column act type act_identifier,
alter column amends type act_identifier;

create domain language_code as text;
comment on domain language_code is 'The code of a language, for example ENG (language.Language).';

alter table expression alter column language_code type language_code;
alter table version_formex alter column language_code type language_code;
alter table act_title alter column language_code type language_code;
alter table act_publication_title alter column language_code type language_code;
alter table preparation alter column language_code type language_code;
alter table preparation_entry alter column language_code type language_code;
alter table catalogue_search_expression alter column language_code type language_code;
alter table catalogue_search_stage_expression alter column language_code type language_code;
