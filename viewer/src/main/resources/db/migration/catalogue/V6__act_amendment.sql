-- SPDX-License-Identifier: MIT
-- What other acts did to an act's provisions (Amendment), part of the act, in the order Cellar has
-- them: the amending act, the location in Cellar's codes (AR 89 PA 1 ALN 1), the role, the days it
-- applies from and until (none where Cellar has none), and the only languages it changes (Language
-- codes; none for all of them)
create table act_amendment (
  act text not null references act,
  position integer not null,
  amending text not null,
  location text not null,
  role text not null check (
    role in (
      'REPLACEMENT', 'ADDITION', 'DELETION', 'REPEAL', 'EXTENSION', 'SUSPENSION', 'DEFERRAL', 'OTHER'
    )
  ),
  valid_from date,
  valid_until date,
  languages text[] not null,
  primary key (act, position)
);

-- the acts kept are read again on their next visit, for their amendments
update act set checked_at = timestamptz 'epoch';
