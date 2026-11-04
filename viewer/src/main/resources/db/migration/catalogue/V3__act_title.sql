-- SPDX-License-Identifier: MIT
-- Acts' titles, part of the act: one in each official language Cellar has the act's work in, as
-- the Official Journal printed it
create table act_title (
  act text not null references act,
  language text not null,
  title text not null,
  primary key (act, language)
);
