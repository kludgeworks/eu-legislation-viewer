-- SPDX-License-Identifier: MIT
analyze works;
analyze titles;
analyze concepts;
analyze amends;

insert into tmp_work
select w.celex, min(nullif(w.eli,'')) eli, min(nullif(w.type,'')) resource_type,
       bool_or(w."inForce") in_force, coalesce(max(c.citations),0) citation_count
from works w left join citations c using (celex)
where exists (select from titles t where t.celex=w.celex and t.title <> '')
group by w.celex;

insert into tmp_amendment
select a.celex act, a.amends, min(nullif(a."amendsEli",'')) amends_eli
from amends a join tmp_work w using (celex) group by a.celex, a.amends;

insert into tmp_expression
with names as (
  select celex,array_agg(distinct lower(trim(name)) order by lower(trim(name))) names
  from (
    select celex, regexp_split_to_table(concat_ws(';',short,alt),';') name from short_titles_en
    union all select celex,alias_norm from catalogue_search_alias
  ) n where trim(name) <> '' group by celex
), vocabulary as (
  select celex,string_agg(distinct words,' ' order by words) words
  from concepts join labels on concept=c group by celex
), targets as (
  select act,string_agg(amends||' '||substring(amends,2,4)||'/'||right(amends,4)::integer||' '||right(amends,4)::integer||'/'||substring(amends,2,4),' ' order by amends) numbers
  from tmp_amendment where act in (select celex from titles) group by act
), english as (
  select celex,min(title) title from titles where title <> '' group by celex
), trimmed as (
  select celex,title,regexp_replace(regexp_replace(replace(title,chr(160),' '),
    '^.*?\sof\s+[0-9]{1,2}\s+[A-Za-z]+\s+[0-9]{4}\s+', '', 'i'),
    '\s*\(?Text with EEA relevance\.?\)?\s*$', '', 'i') substantive_title from english
)
select w.celex,'ENG'::text language_code,t.title,t.substantive_title,
       coalesce(n.names,'{}') short_titles_norm,
       setweight(to_tsvector('simple',concat_ws(' ',w.celex,w.year_number,w.number_year)),'A')
       || setweight(to_tsvector('english',unaccent(coalesce(array_to_string(n.names,' '),''))),'A')
       || setweight(to_tsvector('english',unaccent(t.substantive_title)),'B')
       || setweight(to_tsvector('english',unaccent(concat_ws(' ',t.title,v.words,a.numbers,
         (select string_agg(m[1],' ') from regexp_matches(t.title,
           '\m([0-9]{1,4}/[0-9]{1,4})/(?:EC|EU|EEC|Euratom|JHA|CFSP)\M','gi') m)))),'C') search_tsv
from (
  select celex,substring(celex,2,4)||'/'||right(celex,4)::integer year_number,
         right(celex,4)::integer||'/'||substring(celex,2,4) number_year from tmp_work
) w join trimmed t using (celex) left join names n using (celex)
left join vocabulary v using (celex) left join targets a on a.act=w.celex;

truncate pg_temp.works, pg_temp.amends, pg_temp.titles, pg_temp.concepts;
