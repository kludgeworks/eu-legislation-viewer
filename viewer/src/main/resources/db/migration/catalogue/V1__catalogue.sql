-- SPDX-License-Identifier: MIT
-- The catalogue module's acts, as Cellar has them (Acts): each act with its versions, read from
-- Cellar on its first read and kept. An act is one aggregate: its versions are its own.

-- An act by its CELEX, with its ELI where it has one; checked_at, when Cellar was last asked
create table act (
  celex text primary key,
  eli text unique,
  resource_type text not null,
  date_of_document date,
  in_force boolean not null,
  end_of_validity date,
  checked_at timestamptz not null
);

-- An act's versions, one a day: the original text and its consolidations (celex, the document
-- holding the text). integrates: the CELEX of the acts Cellar lists as consolidated at this step,
-- in our catalogue or not; languages: those its text is in as Formex (ENG, FRA …)
create table version (
  act text not null references act,
  date date not null,
  kind text not null check (kind in ('ORIGINAL', 'CONSOLIDATED')),
  celex text not null unique,
  interim boolean not null,
  integrates text[] not null,
  languages text[] not null,
  formex_modified timestamptz,
  primary key (act, date)
);
