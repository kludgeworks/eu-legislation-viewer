-- SPDX-License-Identifier: MIT
delete from catalogue_search_work w where not exists (select from tmp_work t where t.celex=w.celex);
insert into catalogue_search_work as w (celex,eli,resource_type,in_force,citation_count)
select celex,eli,resource_type,in_force,citation_count from tmp_work
on conflict (celex) do update set (eli,resource_type,in_force,citation_count) =
(excluded.eli,excluded.resource_type,excluded.in_force,excluded.citation_count)
where (w.eli,w.resource_type,w.in_force,w.citation_count) is distinct from
(excluded.eli,excluded.resource_type,excluded.in_force,excluded.citation_count);

delete from catalogue_search_amendment a where not exists
(select from tmp_amendment t where (t.act,t.amends)=(a.act,a.amends));
insert into catalogue_search_amendment as a select * from tmp_amendment
on conflict (act,amends) do update set amends_eli=excluded.amends_eli
where a.amends_eli is distinct from excluded.amends_eli;

insert into catalogue_search_expression as e select * from tmp_expression
on conflict (celex,language_code) do update set (title,substantive_title,short_titles_norm,search_tsv)=
(excluded.title,excluded.substantive_title,excluded.short_titles_norm,excluded.search_tsv)
where (e.title,e.substantive_title,e.short_titles_norm,e.search_tsv) is distinct from
(excluded.title,excluded.substantive_title,excluded.short_titles_norm,excluded.search_tsv);
update catalogue_search_rebuild set completed_at=clock_timestamp() where only_row;
