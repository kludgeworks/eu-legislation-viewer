<!-- SPDX-License-Identifier: MIT -->

# Postgres act search verification

On the 300-query external suite, the viewer put an expected act first for 172 queries. The previous Cellar implementation did so for 74. This suite contains 250 EUR-Lex summary titles and 50 official short titles. An expected act is one linked by EUR-Lex.

| D-300 | Expected act first | Any expected act in first five | Mean recall at five |
| --- | ---: | ---: | ---: |
| Postgres | 172/300 | 199/300 | 64.4% |
| Live Cellar | 74/300 | 102/300 | 32.4% |

The Postgres run used the viewer repository on PostgreSQL 18.6 and the archived November 15 Cellar extract. The baseline used the November 17 live endpoint with the title query at `466ab32b`, the first 21 candidates, and the viewer's ordering. The corpus can change between those dates. A separate live run of all 39 extract queries loaded 171,827 works and 45,782 amendment relationships.

## Regression suites

| Suite | Expected act first | Any expected act in first five | Negative queries returning no acts |
| --- | ---: | ---: | ---: |
| A, prototype | 36/43 | 36/43 | — |
| B, stress cases | 29/32 | 30/32 | 8/8 |
| C, held out | 22/24 | 23/24 | 6/6 |

Replaying the archived Spike 6 and 7 implementations identified the two typo queries behind the change from 41 to 39 first-place hits. At a trigram threshold of 0.52, both schemas ranked an expected act first for `aviaton securty`. For `passanger rights`, Spike 6 returned the expected rail passenger regulation and Spike 7 ranked a different act first even at 0.52. At the specified 0.60 threshold, Spike 7 returned no act for either query. Changing the ranking formula at 0.60 recovered neither.

The viewer also misses three prototype queries that the spike answered with topical aliases: “liquids in hand luggage”, “cookie consent privacy”, and “right to be forgotten”. The production seed contains act identifiers and short names. Catalogue maintainers can update it through reviewed migrations.

The Publications Office documents automated access to Cellar. A recurring bulk-query allowance remains unconfirmed. The viewer can run the job on an enabled instance and issue one request at a time. [Cellar access guidance](https://op.europa.eu/en/web/eu-vocabularies/cellar)

## Rebuild operation

One viewer holds a PostgreSQL session advisory lock while building the index. It records the attempt before contacting Cellar and commits each extract into session-private staging tables. After a failed attempt the hourly check waits until 24 hours after the claim to retry. After a successful build the next nightly boundary makes the index eligible again.

The final transaction checks that the staged inventory is nonempty and contains at least 95% of the previously published act count, applies the index changes, and records completion. Readers keep the last published index if the build fails. The `catalogue.search.extract` observation records duration and errors by extract family, including `citations`.
