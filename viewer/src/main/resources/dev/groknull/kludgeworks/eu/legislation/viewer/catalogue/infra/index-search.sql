-- SPDX-License-Identifier: MIT
with input as (select ?::text typed, ?::text kind, ?::boolean allow_typos),
query as (
  select i.*, case when expanded.terms is null then i.original
    else i.original || plainto_tsquery('english',unaccent(expanded.terms)) end words
  from (select input.*,plainto_tsquery('english',unaccent(typed)) original from input) i
  left join lateral (
    -- A longer query can name an act cited in another title, such as "GDPR adequate protection Japan".
    select replace(' '||i.typed||' ',' '||a.alias_norm||' ',
      ' '||w.year||'/'||w.natural_number||' ') terms
    from catalogue_search_alias a join catalogue_search_work w using (celex)
    where a.is_exact_redirect and i.typed <> a.alias_norm
      and strpos(' '||i.typed||' ',' '||a.alias_norm||' ') > 0
    order by length(a.alias_norm) desc,a.alias_norm limit 1
  ) expanded on true
),
filtered as not materialized (
  select w.celex,w.in_force,w.citation_count,e.search_tsv,e.substantive_title,
         coalesce((select true from catalogue_search_amendment a where a.act=w.celex limit 1),false) amending,
         q.words,q.typed,q.allow_typos,
         q.typed in (w.year||'/'||w.natural_number,w.natural_number||'/'||w.year) exact_number
  from catalogue_search_expression e join catalogue_search_work w using (celex) cross join query q
  where e.language_code='ENG' and (q.kind='all' or substring(w.celex,6,1)=q.kind
    or (q.kind='amending' and exists(select from catalogue_search_amendment a where a.act=w.celex)))
), lexical as (
  select celex,in_force,amending,exact_number,false as typo,ts_rank_cd('{0.05,0.15,0.6,1.0}',search_tsv,words,32)
    * (1+ln(1.0+citation_count)) * case when in_force is false then 0.5 else 1 end score
  from filtered where search_tsv @@ words
), candidates as (
  select * from lexical
  union all
  select celex,in_force,amending,exact_number,true as typo,word_similarity(typed,substantive_title) * (1+ln(1.0+citation_count)) score
  from filtered where allow_typos and not exists (select from lexical) and typed <% substantive_title
), page as (
  select * from candidates
  order by exact_number desc,score desc,amending,(in_force is true) desc,celex desc limit ? offset ?
)
select p.typo,p.celex,w.eli,w.resource_type,p.in_force,e.title,a.amends,a.amends_eli
from page p join catalogue_search_work w using (celex)
join catalogue_search_expression e on e.celex=p.celex and e.language_code='ENG'
left join catalogue_search_amendment a on a.act=p.celex
order by p.exact_number desc,p.score desc,p.amending,(p.in_force is true) desc,p.celex desc,a.amends
