-- SPDX-License-Identifier: MIT
-- How many differences each act made in a step of an act's versions (StepCounts): the comparison
-- of one version's text with the next's in a language, by their roots, attributed as the sidenotes
-- attribute them. Counted once, for the chips; an act's dropped when it is read again (its
-- amendments may have changed). A change to the attribution or the comparison needs a migration
-- dropping them, as the comparisons' do
create table step_count (
  act text not null references act,
  from_root text not null,
  to_root text not null,
  together integer not null,
  unknown integer not null,
  counted_at timestamptz not null,
  primary key (act, from_root, to_root)
);

-- each act of the step's differences: those it made, alone or with others
create table step_act_count (
  act text not null,
  from_root text not null,
  to_root text not null,
  amending text not null,
  differences integer not null,
  primary key (act, from_root, to_root, amending),
  foreign key (act, from_root, to_root) references step_count on delete cascade
);
