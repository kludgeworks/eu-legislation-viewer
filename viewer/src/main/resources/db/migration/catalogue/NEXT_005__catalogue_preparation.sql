-- SPDX-License-Identifier: MIT
-- An act's preparation in a language (Preparation): from its first opening in the language until
-- every version's text is settled, built or failed; then prepared, or failed if none was built.
create table preparation (
  act text not null references act,
  language text not null,
  state text not null check (state in ('PREPARING', 'PREPARED', 'FAILED')),
  started_at timestamptz not null,
  settled_at timestamptz,
  primary key (act, language),
  check ((state = 'PREPARING') = (settled_at is null))
);

-- Its steps as they happened, for a reader (not the server's log): a version's text started on an
-- attempt, built, queued again after a failed attempt, or failed (failure, why). attempt: the one
-- started or queued again after; none for one built or failed, whose event doesn't say
create table preparation_step (
  act text not null,
  language text not null,
  at timestamptz not null,
  celex text not null references version (celex),
  kind text not null check (kind in ('STARTED', 'BUILT', 'DEFERRED', 'FAILED')),
  attempt integer,
  failure text,
  foreign key (act, language) references preparation
);

create index preparation_step_at on preparation_step (act, language, at);
