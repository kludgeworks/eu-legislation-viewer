// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import dev.groknull.kludgeworks.jooq.postgres.RankWeights;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How the search of acts ranks and matches ({@link PostgresSearch}). The properties are {@code
 * viewer.catalogue.search.tuning.*}. A change of a value records here why, and which queries of
 * {@code docs/briefs/postgres-search.md} checked it.
 *
 * <p>The text search configuration of the index is not here. The migration that builds the index
 * sets it, and the search must use the same one.
 *
 * @param typoThreshold how like a run of words of a title the typed words must be to match it with
 *     typos: pg_trgm's {@code word_similarity_threshold}, from 0 to 1. Only the typo fallback uses
 *     it, which runs if no act matches by its words. ISS-191 set 0.60. Measured in the brief: at
 *     0.52 "aviaton securty" finds the expected act first, and at 0.60 the schema of spike 7 finds
 *     no act for the two typo queries of suite A. Not measured: how many wrong acts a lower value
 *     finds.
 * @param weights how much a typed word counts in the rank, by the part of the act where it matches
 * @param notInForce what the score of an act not in force is multiplied by, from 0 to 1. An act in
 *     force keeps its score. ISS-191 set 0.5. The spike ("Search architecture and Cellar indexing
 *     spikes" in Notion) measured 0.65, with 1.45 for an act in force. Not recorded: why 0.5.
 */
@ConfigurationProperties("viewer.catalogue.search.tuning")
@Validated
record SearchTuning(
    @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal typoThreshold,
    @NotNull @Valid Weights weights,
    @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal notInForce) {

  /**
   * How much a typed word counts in the rank of an act, by the part of the act where it matches. A
   * match in a part with weight 0.6 counts four times a match in a part with weight 0.15. The parts
   * are the classes A, B and C that the migration gives the words of the index ({@code setweight}).
   * Postgres's default weights for them are 1.0, 0.4 and 0.2. The viewer's give the subject more
   * weight, and the title and topics less. Not recorded: why these numbers. ISS-192 is to measure
   * them against the Cellar baseline.
   *
   * @param numberOrName a match in the number of the act or one of its names: {@code 2016/679},
   *     {@code 679/2016}, {@code 32016R0679} or a short title (class A)
   * @param subject a match in the words of the title that say what the act is about, after "of 27
   *     April 2016": "on the protection of natural persons with regard to the processing of
   *     personal data…" (class B)
   * @param titleAndTopics a match only in the full title, in the number of an act that it amends,
   *     or in its EuroVoc words (class C)
   */
  record Weights(float numberOrName, float subject, float titleAndTopics) {

    /** The index puts no word in class D, so its weight changes no rank. */
    private static final float NO_WORD = 0;

    RankWeights rank() {
      return new RankWeights(NO_WORD, titleAndTopics, subject, numberOrName);
    }
  }
}
