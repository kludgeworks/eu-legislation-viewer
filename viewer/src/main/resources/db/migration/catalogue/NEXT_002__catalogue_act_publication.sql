-- SPDX-License-Identifier: MIT
-- How the acts an act's versions integrate were published (Publication), part of the act: each
-- act's ELI (none where Cellar has none) and its place in the Official Journal (none where Cellar
-- doesn't say): the series (L, C), its number in the act-by-act Journal (2026/449) or the issue it
-- is in (21), and the day it was published; and its title in each language Cellar has it in
create table act_publication (
  act text not null references act,
  subject text not null,
  eli text,
  series text,
  number text,
  issue integer,
  published date,
  primary key (act, subject),
  check ((series is null) = (published is null))
);

create table act_publication_title (
  act text not null,
  subject text not null,
  language text not null,
  title text not null,
  primary key (act, subject, language),
  foreign key (act, subject) references act_publication
);

-- the acts kept are read again on their next visit, for their amending acts' publications
update act set checked_at = timestamptz 'epoch';
