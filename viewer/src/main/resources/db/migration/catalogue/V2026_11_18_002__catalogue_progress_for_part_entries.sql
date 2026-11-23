-- SPDX-License-Identifier: MIT
-- Expand: the catalogue's glossary (2026-11-23). "state" is the legal state only, so the progress
-- of a text and of a preparation is "progress". "in part" is a state on a day, so a date for a part
-- of an act is "for_part". "step" is the comparison's word, so the log of a preparation has
-- entries, and a text that waits again after a failed attempt is POSTPONED (the event
-- ConversionPostponed).
--
-- The release that runs while this one deploys still reads and writes the old names. So the old
-- columns and the old table stay, deprecated, and triggers keep the old and the new names equal in
-- both directions. The next release drops the old names and the triggers.

-- expression.state -> expression.progress
alter table expression
add column progress text check (progress in ('QUEUED', 'BUILDING', 'BUILT', 'FAILED'));
update expression set progress = state;
alter table expression alter column state drop not null;
comment on column expression.progress is
'How far the text is: queued, being built (attempt), built, or failed (failure).';
comment on column expression.state is
'deprecated: expression.progress keeps it. To be dropped in the next release.';

-- preparation.state -> preparation.progress
alter table preparation
add column progress text check (progress in ('PREPARING', 'PREPARED', 'FAILED'));
update preparation set progress = state;
alter table preparation alter column state drop not null;
comment on column preparation.progress is
'How far the preparation is: preparing until every text is settled (settled_at), then prepared, or failed if no text was built.';
comment on column preparation.state is
'deprecated: preparation.progress keeps it. To be dropped in the next release.';

-- act_date.in_part -> act_date.for_part
alter table act_date add column for_part boolean;
update act_date set for_part = in_part;
alter table act_date alter column in_part drop not null;
comment on column act_date.for_part is
'Whether the date is for a part of the act only (Cellar''s MA/PART).';
comment on column act_date.in_part is
'deprecated: act_date.for_part keeps it. To be dropped in the next release.';

-- Keeps a deprecated column and its new column equal, whichever of the two releases writes.
create function catalogue_keep_progress() returns trigger language plpgsql as $$
begin
  if tg_op = 'INSERT' then
    new.progress := coalesce(new.progress, new.state);
    new.state := coalesce(new.state, new.progress);
  elsif new.state is distinct from old.state then
    new.progress := new.state;
  elsif new.progress is distinct from old.progress then
    new.state := new.progress;
  end if;
  return new;
end $$;

create trigger expression_keep_progress before insert or update on expression
for each row execute function catalogue_keep_progress();
create trigger preparation_keep_progress before insert or update on preparation
for each row execute function catalogue_keep_progress();

create function catalogue_keep_for_part() returns trigger language plpgsql as $$
begin
  if tg_op = 'INSERT' then
    new.for_part := coalesce(new.for_part, new.in_part);
    new.in_part := coalesce(new.in_part, new.for_part);
  elsif new.in_part is distinct from old.in_part then
    new.for_part := new.in_part;
  elsif new.for_part is distinct from old.for_part then
    new.in_part := new.for_part;
  end if;
  return new;
end $$;

create trigger act_date_keep_for_part before insert or update on act_date
for each row execute function catalogue_keep_for_part();

-- preparation_step -> preparation_entry, with DEFERRED -> POSTPONED
create table preparation_entry (
  act text not null,
  language_code text not null,
  happened_at timestamptz not null,
  celex text not null references version (celex),
  kind text not null check (kind in ('STARTED', 'BUILT', 'POSTPONED', 'FAILED')),
  attempt integer,
  failure text,
  foreign key (act, language_code) references preparation
);

create index preparation_entry_happened_at on preparation_entry (act, language_code, happened_at);

insert into preparation_entry (act, language_code, happened_at, celex, kind, attempt, failure)
select
  act,
  language_code,
  happened_at,
  celex,
  case kind when 'DEFERRED' then 'POSTPONED' else kind end as kind,
  attempt,
  failure
from preparation_step;

comment on table preparation_entry is
'The log of a preparation, for a reader (not the server''s log): a version''s text started on an attempt, built, postponed (queued again after a failed attempt), or failed (failure, why). attempt: the one started or postponed after; none for one built or failed, whose event does not say.';
comment on table preparation_step is
'deprecated: preparation_entry keeps the log. To be dropped in the next release.';

-- Copies each insert and delete to the other table. pg_trigger_depth() stops the copy of a copy.
create function catalogue_copy_step() returns trigger language plpgsql as $$
begin
  if pg_trigger_depth() > 1 then
    return null;
  end if;
  if tg_op = 'INSERT' then
    insert into preparation_entry (act, language_code, happened_at, celex, kind, attempt, failure)
    values (new.act, new.language_code, new.happened_at, new.celex,
      case new.kind when 'DEFERRED' then 'POSTPONED' else new.kind end, new.attempt, new.failure);
  else
    delete from preparation_entry
    where act = old.act and language_code = old.language_code and happened_at = old.happened_at and celex = old.celex
      and kind = case old.kind when 'DEFERRED' then 'POSTPONED' else old.kind end;
  end if;
  return null;
end $$;

create function catalogue_copy_entry() returns trigger language plpgsql as $$
begin
  if pg_trigger_depth() > 1 then
    return null;
  end if;
  if tg_op = 'INSERT' then
    insert into preparation_step (act, language_code, happened_at, celex, kind, attempt, failure)
    values (new.act, new.language_code, new.happened_at, new.celex,
      case new.kind when 'POSTPONED' then 'DEFERRED' else new.kind end, new.attempt, new.failure);
  else
    delete from preparation_step
    where act = old.act and language_code = old.language_code and happened_at = old.happened_at and celex = old.celex
      and kind = case old.kind when 'POSTPONED' then 'DEFERRED' else old.kind end;
  end if;
  return null;
end $$;

create trigger preparation_step_copy after insert or delete on preparation_step
for each row execute function catalogue_copy_step();
create trigger preparation_entry_copy after insert or delete on preparation_entry
for each row execute function catalogue_copy_entry();

comment on column preparation_entry.attempt is
'The attempt that started, or that the text was postponed after. None for a text built or failed.';
comment on column preparation_entry.failure is 'Why the text failed.';

comment on column version.integrates is
'The CELEX of the acts that Cellar lists as integrated in this version, in our catalogue or not.';
