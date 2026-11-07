-- SPDX-License-Identifier: MIT
-- Stores how the acts that the versions of an act integrate were published, as part of the act.
-- The domain type is Publication. For each act it stores:
-- - the ELI. It is null if Cellar has no ELI.
-- - the place in the Official Journal. It is null if Cellar does not give it. The place is the
--   series, for example L or C, and the number in the act-by-act Journal, for example 2026/449, or
--   the issue that contains the act, for example 21. It also has the day of publication.
-- - the title in each language that Cellar has it in.
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

-- Make the viewer read the kept acts again on their next visit, to get the publications of their
-- amending acts.
update act set checked_at = timestamptz 'epoch';
