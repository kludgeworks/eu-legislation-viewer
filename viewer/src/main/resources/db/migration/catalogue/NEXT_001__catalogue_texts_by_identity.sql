-- SPDX-License-Identifier: MIT
-- The catalogue no longer keeps a text's root: it asks the content module for the texts of the
-- versions, by their CELEX and language. A step count is kept by the identities of its two texts
-- (TextId), and dropped when one of them is replaced (TextReplaced).
-- Deprecated, and dropped in the next release, because the release that runs while this one
-- deploys still reads and writes them: expression.root, expression.converter, step_count and
-- step_act_count. text_step_count and text_step_act_count replace the step counts. A built
-- expression no longer needs a root, so its check is dropped.
-- The content module stores the texts again in another table (content NEXT_001), so every text is
-- prepared again: the expressions and the preparations are deleted.
delete from preparation_step;
delete from preparation;
delete from expression;

alter table expression drop constraint expression_check;

create table text_step_count (
  act text not null references act,
  from_text bigint not null,
  to_text bigint not null,
  together integer not null,
  unattributed integer not null,
  counted_at timestamptz not null,
  primary key (act, from_text, to_text)
);

-- the counts of a text that is replaced are dropped
create index text_step_count_from_text on text_step_count (from_text);
create index text_step_count_to_text on text_step_count (to_text);

create table text_step_act_count (
  act text not null,
  from_text bigint not null,
  to_text bigint not null,
  amending text not null,
  differences integer not null,
  primary key (act, from_text, to_text, amending),
  foreign key (act, from_text, to_text) references text_step_count on delete cascade
);

comment on column expression.root is
'deprecated: the content module keeps the text by its version and language. To be dropped in the next release.';
comment on column expression.converter is
'deprecated: the content module keeps the converter of each text. To be dropped in the next release.';
comment on table step_count is
'deprecated: text_step_count keeps the counts by the identities of their texts. To be dropped in the next release.';
comment on table step_act_count is
'deprecated: text_step_act_count keeps the counts by the identities of their texts. To be dropped in the next release.';
comment on table text_step_count is
'How many differences each act made in the comparison that an act''s page lists (StepCounts), by the identities of its two texts in the content module, attributed as the sidenotes attribute them. Counted once, for the chips. An act''s are dropped when it is read again, and a text''s when it is replaced. A change to the attribution needs a migration that drops them.';
comment on column text_step_count.from_text is 'The earlier text, by its identity in the content module (TextId).';
comment on column text_step_count.to_text is 'The later text, by its identity in the content module (TextId).';
comment on column text_step_count.together is 'How many differences more than one act made together.';
comment on column text_step_count.unattributed is 'How many differences one of the step''s acts made, where it is not known which act.';
comment on table text_step_act_count is
'Each act''s differences in a step: those it made, alone or with others.';
comment on index text_step_count_from_text is 'The counts of a text that is replaced, which are dropped.';
comment on index text_step_count_to_text is 'The counts of a text that is replaced, which are dropped.';
