-- SPDX-License-Identifier: MIT
create extension if not exists pg_trgm;
create extension if not exists unaccent;

create table catalogue_search_work (
  celex text primary key check (celex ~ '^3[0-9]{4}[RLD][0-9]{4}$'),
  year integer generated always as (substring(celex,2,4)::integer) stored,
  natural_number integer generated always as (right(celex,4)::integer) stored,
  eli text,
  resource_type text,
  in_force boolean,
  citation_count integer not null check (citation_count >= 0)
);
create index catalogue_search_work_number on catalogue_search_work(year,natural_number);
create index catalogue_search_work_eli on catalogue_search_work(eli);

create table catalogue_search_expression (
  celex text references catalogue_search_work on delete cascade,
  language_code text,
  title text not null,
  substantive_title text not null,
  short_titles_norm text[] not null,
  search_tsv tsvector not null,
  primary key (celex,language_code)
);
create index catalogue_search_words on catalogue_search_expression using gin(search_tsv);
create index catalogue_search_typos on catalogue_search_expression using gin(substantive_title gin_trgm_ops);
create index catalogue_search_short_titles on catalogue_search_expression using gin(short_titles_norm);

create table catalogue_search_amendment (
  act text references catalogue_search_work on delete cascade,
  amends text,
  amends_eli text,
  primary key (act,amends)
);
create index catalogue_search_amended on catalogue_search_amendment(amends);

-- Curated independently of the nightly inventory so aliases survive withdrawals and reappearances.
create table catalogue_search_alias (
  alias_norm text primary key,
  celex text not null check (celex ~ '^3[0-9]{4}[RLD][0-9]{4}$'),
  is_exact_redirect boolean not null
);
insert into catalogue_search_alias values
  ('gdpr','32016R0679',true),
  ('general data protection regulation','32016R0679',true),
  ('ai act','32024R1689',true),
  ('artificial intelligence act','32024R1689',true),
  ('dsa','32022R2065',true),
  ('digital services act','32022R2065',true),
  ('dma','32022R1925',true),
  ('digital markets act','32022R1925',true),
  ('dora','32022R2554',true),
  ('digital operational resilience act','32022R2554',true),
  ('mica','32023R1114',true),
  ('markets in crypto-assets','32023R1114',true),
  ('nis2','32022L2555',true),
  ('nis 2','32022L2555',true),
  ('nis 2 directive','32022L2555',true),
  ('psd2','32015L2366',true),
  ('reach','32006R1907',true),
  ('emir','32012R0648',true),
  ('cbam','32023R0956',true),
  ('csrd','32022L2464',true),
  ('cra','32024R2847',true),
  ('cyber resilience act','32024R2847',true),
  ('data act','32023R2854',true),
  ('eidas','32014R0910',true);

create table catalogue_search_rebuild (
  only_row boolean primary key default true check (only_row),
  claimed_at timestamptz not null default '-infinity',
  completed_at timestamptz
);
insert into catalogue_search_rebuild default values;

comment on table catalogue_search_work is 'English-searchable sector 3 regulations, directives and decisions.';
comment on column catalogue_search_work.in_force is 'Null means Cellar gives no status.';
comment on column catalogue_search_work.citation_count is 'Distinct works citing this act.';
comment on table catalogue_search_expression is 'Searchable words and display title by act and language.';
comment on column catalogue_search_expression.search_tsv is 'Weights A for identifiers and names, B for substantive title, C for title, amendments and vocabulary.';
comment on table catalogue_search_amendment is 'Acts amended by each search result, including their ELI when known.';
comment on table catalogue_search_alias is 'Reviewed names and identifiers maintained by catalogue maintainers through migrations.';
comment on column catalogue_search_alias.is_exact_redirect is 'The complete name identifies one act directly.';
comment on table catalogue_search_rebuild is 'A single viewer builds the index on startup and nightly.';
comment on column catalogue_search_rebuild.completed_at is 'Time the complete index became visible. Null until first successful publication.';
