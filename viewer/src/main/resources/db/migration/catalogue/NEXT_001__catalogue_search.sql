-- SPDX-License-Identifier: MIT
create extension if not exists pg_trgm;
create extension if not exists unaccent;

create table catalogue_search_work (
  celex text primary key check (celex ~ '^3[0-9]{4}[RLD][0-9]{4}$'),
  act_year integer generated always as (substring(celex, 2, 4)::integer) stored,
  natural_number integer generated always as (right(celex, 4)::integer) stored,
  eli text,
  resource_type text,
  in_force boolean,
  citation_count integer not null check (citation_count >= 0)
);
create index catalogue_search_work_number on catalogue_search_work (act_year, natural_number);
create index catalogue_search_work_eli on catalogue_search_work (eli);

create table catalogue_search_expression (
  celex text references catalogue_search_work on delete cascade,
  language_code text,
  title text not null,
  substantive_title text not null,
  short_titles_norm text[] not null,
  search_tsv tsvector not null,
  primary key (celex, language_code)
);
create index catalogue_search_words on catalogue_search_expression using gin (search_tsv);
create index catalogue_search_typos on catalogue_search_expression using gin (
  substantive_title gin_trgm_ops
);
create index catalogue_search_short_titles on catalogue_search_expression using gin (
  short_titles_norm
);

create table catalogue_search_amendment (
  act text references catalogue_search_work on delete cascade,
  amends text,
  amends_eli text,
  primary key (act, amends)
);
create index catalogue_search_amended on catalogue_search_amendment (amends);

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
comment on table catalogue_search_rebuild is 'A single viewer builds the index on startup and nightly.';
comment on column catalogue_search_rebuild.completed_at is 'Time the complete index became visible. Null until first successful publication.';

-- The build of the index stages Cellar's extracts and the rows of the next index in these tables.
-- They are unlogged: they hold only what one build needs, and a crash of Postgres empties them. One
-- viewer builds at a time (an advisory lock), and the build empties them when it starts and ends.
create unlogged table catalogue_search_extract_work (
  celex text,
  eli text,
  resource_type text,
  document_date text,
  in_force boolean
);
create unlogged table catalogue_search_extract_title (celex text, title text);
create unlogged table catalogue_search_extract_concept (celex text, concept text);
create unlogged table catalogue_search_extract_amendment (
  celex text,
  amends text,
  amends_eli text
);
create unlogged table catalogue_search_extract_citation (celex text, citations integer);
create unlogged table catalogue_search_extract_label (
  concept text,
  pref_label text,
  alt_label text
);
create unlogged table catalogue_search_extract_short_title (
  celex text,
  short_title text,
  alternative_title text
);
create unlogged table catalogue_search_extract_vocabulary (concept text, words text);
create unlogged table catalogue_search_stage_work (
  celex text,
  eli text,
  resource_type text,
  in_force boolean,
  citation_count integer
);
create unlogged table catalogue_search_stage_amendment (
  act text,
  amends text,
  amends_eli text
);
create unlogged table catalogue_search_stage_expression (
  celex text,
  language_code text,
  title text,
  substantive_title text,
  short_titles_norm text[],
  search_tsv tsvector
);

-- The words of each concept of the vocabularies, once the labels are staged.
create function catalogue_search_stage_vocabulary() returns void
language plpgsql as $$
begin
  insert into catalogue_search_extract_vocabulary (concept, words)
  select l.concept, string_agg(distinct w.word, ' ' order by w.word)
  from catalogue_search_extract_label as l
  cross join lateral unnest(array[l.pref_label, l.alt_label]) as w (word)
  where w.word <> ''
  group by l.concept;
  truncate catalogue_search_extract_label;
  analyze catalogue_search_extract_vocabulary;
  analyze catalogue_search_extract_citation;
end
$$;

-- The rows of the next index for the acts of one partition of years, once its extracts are
-- staged. It empties the extracts of the partition, so that the next partition can be staged.
create function catalogue_search_stage_partition() returns void
language plpgsql as $$
begin
  analyze catalogue_search_extract_work;
  analyze catalogue_search_extract_title;
  analyze catalogue_search_extract_concept;
  analyze catalogue_search_extract_amendment;

  insert into catalogue_search_stage_work (celex, eli, resource_type, in_force, citation_count)
  select
    w.celex,
    min(nullif(w.eli, '')),
    min(nullif(w.resource_type, '')),
    bool_or(w.in_force),
    coalesce(max(c.citations), 0)
  from catalogue_search_extract_work as w
  left join catalogue_search_extract_citation as c on c.celex = w.celex
  where exists (
    select 1 from catalogue_search_extract_title as t
    where t.celex = w.celex and t.title <> ''
  )
  group by w.celex;

  insert into catalogue_search_stage_amendment (act, amends, amends_eli)
  select a.celex, a.amends, min(nullif(a.amends_eli, ''))
  from catalogue_search_extract_amendment as a
  inner join catalogue_search_stage_work as w on w.celex = a.celex
  group by a.celex, a.amends;

  insert into catalogue_search_stage_expression (
    celex, language_code, title, substantive_title, short_titles_norm, search_tsv
  )
  with names as (
    select n.celex, array_agg(distinct lower(trim(n.name)) order by lower(trim(n.name))) as names
    from (
      select
        s.celex,
        regexp_split_to_table(concat_ws(';', s.short_title, s.alternative_title), ';') as name
      from catalogue_search_extract_short_title as s
    ) as n
    where trim(n.name) <> ''
    group by n.celex
  ),

  vocabulary as (
    select c.celex, string_agg(distinct v.words, ' ' order by v.words) as words
    from catalogue_search_extract_concept as c
    inner join catalogue_search_extract_vocabulary as v on v.concept = c.concept
    group by c.celex
  ),

  targets as (
    select
      a.act,
      string_agg(
        a.amends || ' ' || substring(a.amends, 2, 4) || '/' || right(a.amends, 4)::integer
        || ' ' || right(a.amends, 4)::integer || '/' || substring(a.amends, 2, 4),
        ' ' order by a.amends
      ) as numbers
    from catalogue_search_stage_amendment as a
    where a.act in (select t.celex from catalogue_search_extract_title as t)
    group by a.act
  ),

  english as (
    select t.celex, min(t.title) as title
    from catalogue_search_extract_title as t
    where t.title <> ''
    group by t.celex
  ),

  trimmed as (
    select
      e.celex,
      e.title,
      regexp_replace(
        regexp_replace(
          replace(e.title, chr(160), ' '),
          '^.*?\sof\s+[0-9]{1,2}\s+[A-Za-z]+\s+[0-9]{4}\s+', '', 'i'
        ),
        '\s*\(?Text with EEA relevance\.?\)?\s*$', '', 'i'
      ) as substantive_title
    from english as e
  )

  select
    w.celex,
    'ENG',
    t.title,
    t.substantive_title,
    coalesce(n.names, '{}'),
    setweight(to_tsvector('simple', concat_ws(' ', w.celex, w.year_number, w.number_year)), 'A')
    || setweight(
      to_tsvector('english', unaccent(coalesce(array_to_string(n.names, ' '), ''))), 'A'
    )
    || setweight(to_tsvector('english', unaccent(t.substantive_title)), 'B')
    || setweight(
      to_tsvector(
        'english',
        unaccent(
          concat_ws(
            ' ',
            t.title,
            v.words,
            a.numbers,
            (
              select string_agg(m.match[1], ' ')
              from regexp_matches(
                t.title, '\m([0-9]{1,4}/[0-9]{1,4})/(?:EC|EU|EEC|Euratom|JHA|CFSP)\M', 'gi'
              ) as m (match)
            )
          )
        )
      ),
      'C'
    )
  from (
    select
      s.celex,
      substring(s.celex, 2, 4) || '/' || right(s.celex, 4)::integer as year_number,
      right(s.celex, 4)::integer || '/' || substring(s.celex, 2, 4) as number_year
    from catalogue_search_stage_work as s
  ) as w
  inner join trimmed as t on t.celex = w.celex
  left join names as n on n.celex = w.celex
  left join vocabulary as v on v.celex = w.celex
  left join targets as a on a.act = w.celex;

  truncate
    catalogue_search_extract_work,
    catalogue_search_extract_title,
    catalogue_search_extract_concept,
    catalogue_search_extract_amendment;
end
$$;

comment on table catalogue_search_extract_work is 'Cellar extract: the acts of one partition of years, with their ELI, type and state.';
comment on table catalogue_search_extract_title is 'Cellar extract: the English titles of the acts of one partition of years.';
comment on table catalogue_search_extract_concept is 'Cellar extract: the concepts of the vocabularies that the acts of one partition of years are about.';
comment on table catalogue_search_extract_amendment is 'Cellar extract: the acts that the acts of one partition of years amend.';
comment on table catalogue_search_extract_citation is 'Cellar extract: how many works cite each act.';
comment on table catalogue_search_extract_label is 'Cellar extract: the English labels of the concepts of the vocabularies.';
comment on table catalogue_search_extract_short_title is 'Cellar extract: the English short and alternative titles of the acts.';
comment on table catalogue_search_extract_vocabulary is 'The words of each concept, from its labels, while a build stages the index.';
comment on table catalogue_search_stage_work is 'The acts of the next index, while a build stages them.';
comment on table catalogue_search_stage_amendment is 'The amendments of the next index, while a build stages them.';
comment on table catalogue_search_stage_expression is 'The searchable words and titles of the next index, while a build stages them.';
comment on function catalogue_search_stage_partition is 'Stages the next index for the extracts of one partition of years.';
