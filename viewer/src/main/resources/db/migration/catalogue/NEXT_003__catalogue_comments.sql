-- SPDX-License-Identifier: MIT
-- Describes the tables in the database itself, for tools that read the schema (mise run
-- db:diagram). The text is from the comments of the migrations that made them.
comment on table act is
'An act by its CELEX, with its ELI where it has one, as Cellar has it (Acts): read from Cellar on its first read and kept. An act is one aggregate: its versions, dates, titles, amendments and publications are its own.';
comment on column act.checked_at is
'When Cellar was last asked about the act. The epoch makes the viewer read it again on its next visit.';

comment on table version is
'An act''s versions: the original text and its consolidations. An original and a same-day consolidation can share a date (32012R0965).';
comment on column version.celex is 'The document that holds the version''s text.';
comment on column version.integrates is
'The CELEX of the acts that Cellar lists as consolidated at this step, in our catalogue or not.';
comment on column version.languages is 'The languages its text is in as Formex (ENG, FRA …).';

comment on table expression is
'A version''s text in a language as it stands, from when its act is first prepared in the language (Texts.prepare): queued, being built, built or failed, as ingest''s events say.';
comment on column expression.root is 'Once built, the text''s root in the content module.';
comment on column expression.converter is 'Once built, the converter that made the text (ExpressionBuilt).';
comment on column expression.attempt is 'The ingest job''s attempt that is building the text.';
comment on column expression.failure is 'Why the text failed.';
comment on column expression.trace is
'The trace id of the attempt that failed, which the failure panel shows. Texts that failed before it was kept have none.';

comment on table act_date is
'An act''s own dates (ActDate), in the order Cellar has them: when the subject enters into force, applies or takes effect, wholly or in part (in_part), and the clause that sets it.';
comment on column act_date.subject is
'The act itself, or an act that its versions integrate, in our catalogue or not.';
comment on column act_date.falls_on is 'None for Cellar''s placeholder.';
comment on column act_date.anchor is 'The day the date counts from.';
comment on column act_date.days_after is 'The days after the anchor.';
comment on column act_date.provisions is 'As ActDate.Provision writes them (ARTICLE 2).';
comment on column act_date.amended_by is 'The CELEX of the acts that changed the date.';
comment on column act_date.remark is 'Cellar''s comment.';

comment on table act_title is
'An act''s titles: one in each official language that Cellar has the act''s work in, as the Official Journal printed it.';

comment on table act_amendment is
'What other acts did to an act''s provisions (Amendment), in the order Cellar has them.';
comment on column act_amendment.amending is 'The CELEX of the amending act.';
comment on column act_amendment.cellar_location is 'The location in Cellar''s codes (AR 89 PA 1 ALN 1).';
comment on column act_amendment.valid_from is 'The day it applies from. None where Cellar has none.';
comment on column act_amendment.valid_until is 'The day it applies until. None where Cellar has none.';
comment on column act_amendment.languages is
'The only languages it changes (Language codes). Empty for all of them.';

comment on table act_publication is
'How the acts that an act''s versions integrate were published (Publication): the ELI, and the place in the Official Journal with the day of publication.';
comment on column act_publication.eli is 'Null if Cellar has no ELI.';
comment on column act_publication.series is 'The Official Journal''s series, for example L or C. Null if Cellar does not give the place.';
comment on column act_publication.number is 'The number in the act-by-act Journal, for example 2026/449.';
comment on column act_publication.issue is 'The issue that contains the act, for example 21.';
comment on table act_publication_title is
'A published act''s title in each language that Cellar has it in.';

comment on table preparation is
'An act''s preparation in a language (Preparation): from its first opening in the language until every version''s text is settled, built or failed. Then prepared, or failed if none was built.';
comment on table preparation_step is
'A preparation''s steps as they happened, for a reader (not the server''s log): a version''s text started on an attempt, built, queued again after a failed attempt (DEFERRED), or failed.';
comment on column preparation_step.attempt is
'The attempt started, or queued again after. None for a text built or failed, whose event does not say.';
comment on column preparation_step.failure is 'Why the text failed.';

comment on table step_count is
'How many differences each act made in the comparison that an act''s page lists (StepCounts), by the roots of its two texts, attributed as the sidenotes attribute them. Counted once, for the chips. An act''s are dropped when it is read again. A change to the attribution or the comparison needs a migration that drops them.';
comment on column step_count.together is 'How many differences more than one act made together.';
comment on column step_count.unattributed is 'How many differences one of the step''s acts made, where it is not known which act.';
comment on table step_act_count is
'Each act''s differences in a step: those it made, alone or with others.';
