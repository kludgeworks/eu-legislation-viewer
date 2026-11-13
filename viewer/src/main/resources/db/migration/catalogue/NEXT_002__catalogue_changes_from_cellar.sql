-- SPDX-License-Identifier: MIT
-- The changes from Cellar. One instance scans Cellar for changes every few minutes (ChangeScan),
-- and the held acts that changed are read again once they are quiet (ActRefresher). An act is no
-- longer read again when a page opens it, so act.checked_at no longer claims that.
-- - version_formex: each version's Formex in each language, whose fixity tells that Cellar
--   republished it. It replaces version.formex_modified, which is no longer written. That column
--   is dropped in a later release.
-- - version.missing_since: a version is withdrawn only when two reads at least 30 minutes apart
--   do not have it.
-- - change_scan: how far the scan has read, and which instance scans.
-- - act_change: the held acts that changed, waiting to be quiet before they are read again.
create table version_formex (
  act text not null,
  celex text not null,
  language_code text not null,
  fixity text,
  modified timestamptz not null,
  primary key (act, celex, language_code),
  foreign key (act, celex) references version on delete cascade
);

alter table version add column missing_since timestamptz;

create table change_scan (
  only_row boolean primary key default true check (only_row),
  watermark timestamptz not null,
  claimed_at timestamptz not null
);

-- the first scan reads from the time of this migration, less the overlap
insert into change_scan (watermark, claimed_at) values (now(), '-infinity');

create table act_change (
  act text primary key references act on delete cascade,
  modified timestamptz,
  first_seen timestamptz,
  due_at timestamptz,
  read_at timestamptz,
  check ((first_seen is null) = (due_at is null))
);

-- the scan publishes the changes that are due
create index act_change_due_at on act_change (due_at) where due_at is not null;

comment on column act.checked_at is 'When the act was last read from Cellar.';
comment on column version.formex_modified is
'deprecated: no longer written, version_formex keeps the Formex of each language. To be dropped in the next release.';
comment on column version.missing_since is
'When a read of the act from Cellar first did not have the version. Null while Cellar has it. A second read at least 30 minutes later withdraws it (VersionWithdrawn).';
comment on table version_formex is
'A version''s Formex in each language (Version.Formex), as Cellar last gave it: a changed fixity is a republication (VersionRepublished).';
comment on column version_formex.fixity is
'The SHA-256 of the sorted SHA-1 fixities of its files (tdm:fixity_message_digest). Null where Cellar has no technical metadata, as for most versions before 2024.';
comment on column version_formex.modified is
'When Cellar last modified the manifestation (cmr:lastModificationDate). It moves on a change of metadata too.';
comment on table change_scan is
'How far the scan of Cellar for changes (ChangeScan) has read. One row.';
comment on column change_scan.watermark is
'The latest cmr:lastModificationDate the scan has read, by Cellar''s clock. The next scan reads from 30 minutes before it, because a change becomes visible up to 18 minutes after its date.';
comment on column change_scan.claimed_at is
'When an instance last claimed the scan. Another instance claims it only when this is older than the scan''s interval, so one instance scans at a time.';
comment on table act_change is
'Each held act that the scan saw change (PendingChange). It is read again (ActChanged) after 10 quiet minutes, at most 60 minutes after the first change.';
comment on column act_change.modified is
'The latest cmr:lastModificationDate seen for the act since first_seen. Only a later one is a new change, which delays due_at.';
comment on column act_change.first_seen is 'When the scan first saw the change. Null when nothing is waiting.';
comment on column act_change.due_at is 'When the act is to be read again. Null when nothing is waiting.';
comment on column act_change.read_at is
'When the act was last sent to be read again. A change dated well before it was in that read.';
comment on index act_change_due_at is 'The acts due to be read again.';
