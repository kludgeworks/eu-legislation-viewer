-- SPDX-License-Identifier: MIT
-- An act's versions: the original text and its consolidations. Previously constrained to one a day
-- with primary key (act, date). An act's original text and its same-day consolidation can share a
-- version date when the consolidation exists in only a subset of languages (e.g. 32012R0965). One
-- version a day remains an invariant within each language's timeline; the table primary key becomes
-- (act, celex).
alter table version drop constraint version_pkey;
alter table version add primary key (act, celex);
