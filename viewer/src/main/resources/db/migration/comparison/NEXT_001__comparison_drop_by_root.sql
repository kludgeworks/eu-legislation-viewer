-- SPDX-License-Identifier: MIT
-- Contract: drops the comparisons kept by the roots of their texts, which v2026.11.6 deprecated.
-- text_comparison and text_comparison_whole keep them by the identities of their texts since then,
-- and v2026.11.6, which runs while this release deploys, no longer uses them.
drop table comparison_whole, comparison;
