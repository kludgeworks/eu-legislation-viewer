-- SPDX-License-Identifier: MIT
-- Adds the trace id of the attempt that failed. The failure panel shows it, so that a report can
-- find the log lines and spans of the attempt. Texts that failed before this migration have none.
alter table expression
  add column trace text;
