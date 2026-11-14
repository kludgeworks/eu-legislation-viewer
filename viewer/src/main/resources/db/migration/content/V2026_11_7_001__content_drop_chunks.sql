-- SPDX-License-Identifier: MIT
-- Contract: drops the chunks and manifests that v2026.11.6 deprecated. Each text is one row in text
-- since then, and v2026.11.6, which runs while this release deploys, no longer uses them.
drop table manifest, chunk;
