-- SPDX-License-Identifier: MIT
-- Contract: drops the step counts that v2026.11.10 deprecated. Neither v2026.11.10 nor
-- v2026.11.11, which runs while this release deploys, reads or writes them.
drop table text_step_act_count, text_step_count;
