-- SPDX-License-Identifier: MIT
-- Describes the tables in the database itself, for tools that read the schema (mise run
-- db:diagram). The text is from the comments of the migrations that made them.
comment on table event_publication is
'Spring Modulith''s event publication registry (spring-modulith-events-jdbc 2.1, schema v2 for Postgres): one row per event and listener until the listener completes, then deleted (spring.modulith.events.completion-mode: delete).';
