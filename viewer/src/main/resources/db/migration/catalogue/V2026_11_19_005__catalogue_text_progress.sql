-- SPDX-License-Identifier: MIT
-- Expand: the glossary (2026-11-24). The catalogue calls a version in one language a text, and
-- "expression" is Cellar's word. The table expression keeps how far each text is, so it is
-- text_progress (the content module's table is text).
--
-- The release that runs while this one deploys still reads and writes expression. So expression
-- stays, deprecated, and triggers copy each insert, update and delete to the other table. The next
-- release drops expression, the triggers and their functions.
create table text_progress (like expression including all);
alter table text_progress add foreign key (celex) references version (celex);

insert into text_progress (celex, language_code, built_at, attempt, failure, trace, progress)
select
  celex,
  language_code,
  built_at,
  attempt,
  failure,
  trace,
  progress
from expression;

comment on table text_progress is
'How far each text (a version in one language) is: queued, being built, built or failed. The text itself is in the content module.';
comment on column text_progress.progress is
'How far the text is: queued, being built (attempt), built, or failed (failure).';
comment on column text_progress.attempt is 'The ingest job''s attempt that is building the text.';
comment on column text_progress.failure is 'Why the text failed.';
comment on column text_progress.trace is
'The trace id of the attempt that failed, which the failure panel shows. Texts that failed before it was kept have none.';
comment on table expression is
'deprecated: text_progress keeps how far each text is. To be dropped in the next release.';

-- Copies each insert, update and delete of expression to text_progress. pg_trigger_depth() stops
-- the copy of a copy.
create function catalogue_copy_expression() returns trigger language plpgsql as $$
begin
  if pg_trigger_depth() > 1 then
    return null;
  end if;
  if tg_op in ('UPDATE', 'DELETE') then
    delete from text_progress where celex = old.celex and language_code = old.language_code;
  end if;
  if tg_op in ('INSERT', 'UPDATE') then
    insert into text_progress (celex, language_code, built_at, attempt, failure, trace, progress)
    values (new.celex, new.language_code, new.built_at, new.attempt, new.failure, new.trace,
      new.progress);
  end if;
  return null;
end $$;

-- Copies each insert, update and delete of text_progress to expression.
create function catalogue_copy_text_progress() returns trigger language plpgsql as $$
begin
  if pg_trigger_depth() > 1 then
    return null;
  end if;
  if tg_op in ('UPDATE', 'DELETE') then
    delete from expression where celex = old.celex and language_code = old.language_code;
  end if;
  if tg_op in ('INSERT', 'UPDATE') then
    insert into expression (celex, language_code, built_at, attempt, failure, trace, progress)
    values (new.celex, new.language_code, new.built_at, new.attempt, new.failure, new.trace,
      new.progress);
  end if;
  return null;
end $$;

create trigger expression_copy after insert or update or delete on expression
for each row execute function catalogue_copy_expression();
create trigger text_progress_copy after insert or update or delete on text_progress
for each row execute function catalogue_copy_text_progress();
