-- SPDX-License-Identifier: MIT
create temp table labels on commit preserve rows as
select concept,string_agg(distinct word,' ' order by word) words
  from skos_en cross join lateral unnest(array[label,alt]) word
  where word <> '' group by concept;
truncate pg_temp.skos_en;
analyze labels;
analyze citations;
