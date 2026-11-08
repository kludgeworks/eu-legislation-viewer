-- SPDX-License-Identifier: MIT
-- Same-day versions: an act's original text and its same-day consolidation can share a version
-- date when the consolidation exists in only a subset of languages (e.g. 32012R0965).
alter table version drop constraint version_pkey;
alter table version add primary key (act, celex);
