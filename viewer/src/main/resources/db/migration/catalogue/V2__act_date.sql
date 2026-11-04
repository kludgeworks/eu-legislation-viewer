-- SPDX-License-Identifier: MIT
-- Acts' own dates (ActDate), part of the act they were read for: its own (subject, the act itself)
-- and those of each act its versions integrate (subject, that act, in our catalogue or not).

-- A date, in the order Cellar has them: when the subject enters into force, applies or takes effect,
-- wholly or in part (partial), and the clause that sets it. date: none for Cellar's placeholder;
-- anchor and days: the day it counts from and the days after it; provisions: as ActDate.Provision
-- writes them (ARTICLE 2); amended_by: the CELEX of the acts that changed it; text: Cellar's comment
create table act_date (
  act text not null references act,
  subject text not null,
  position integer not null,
  date date,
  kind text not null check (kind in ('ENTRY_INTO_FORCE', 'APPLICATION', 'TAKING_EFFECT')),
  partial boolean not null,
  anchor text check (anchor in ('PUBLICATION', 'NOTIFICATION', 'DOCUMENT')),
  days integer check (days is null or anchor is not null),
  provisions text[] not null,
  amended_by text[] not null,
  text text not null,
  primary key (act, subject, position)
);
