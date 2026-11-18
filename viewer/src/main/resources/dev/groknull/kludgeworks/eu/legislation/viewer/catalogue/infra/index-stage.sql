-- SPDX-License-Identifier: MIT
create temp table works(celex text, eli text, type text, date text, "inForce" boolean) on commit drop;
create temp table titles(celex text, title text) on commit drop;
create temp table concepts(celex text, c text) on commit drop;
create temp table amends(celex text, amends text, "amendsEli" text) on commit drop;
create temp table citations(celex text, citations integer) on commit drop;
create temp table skos_en(concept text, label text, alt text) on commit drop;
create temp table short_titles_en(celex text, short text, alt text) on commit drop;

-- Only the normalized inventory grows for the whole run. Raw rows are reused per partition.
create temp table tmp_work on commit drop as
select celex,eli,resource_type,in_force,citation_count from catalogue_search_work with no data;
create temp table tmp_amendment (like catalogue_search_amendment) on commit drop;
create temp table tmp_expression (like catalogue_search_expression) on commit drop;
