// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.jooq.postgres;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

/**
 * Postgres's full-text search and trigram similarity, as jOOQ fields and conditions.
 *
 * <ul>
 *   <li>A filter is the operator itself, {@code @@} or {@code <%}, never the function behind it:
 *       Postgres uses a GIN index only for the operator.
 *   <li>A text search configuration is written into the SQL, not bound as a value: Postgres uses an
 *       index on an expression such as {@code to_tsvector('english', title)} only if the
 *       configuration is a literal.
 * </ul>
 *
 * <p>The functions that the extensions install, such as {@code word_similarity} and {@code
 * unaccent}, are not here: jOOQ's code generation makes them routines of the schema that has them.
 */
public final class TextSearch {
  private TextSearch() {}

  /**
   * Returns the words of {@code text} as a query, in {@code configuration}: {@code
   * plainto_tsquery}.
   */
  public static Field<TsQuery> plainToTsQuery(String configuration, Field<String> text) {
    return DSL.field(
        "plainto_tsquery({0}::regconfig, {1})", TsQuery.DATA_TYPE, DSL.inline(configuration), text);
  }

  /** Returns whether {@code vector} matches {@code query}: {@code @@}. */
  public static Condition matches(Field<TsVector> vector, Field<TsQuery> query) {
    return DSL.condition("{0} @@ {1}", vector, query);
  }

  /**
   * Returns how well {@code vector} matches {@code query}, by how close its words are: {@code
   * ts_rank_cd}.
   *
   * @param normalization how the rank takes the length of the text into account, as Postgres's bit
   *     mask. For example, 32 scales the rank to between 0 and 1.
   */
  public static Field<Float> rankCd(
      RankWeights weights, Field<TsVector> vector, Field<TsQuery> query, int normalization) {
    return DSL.field(
        "ts_rank_cd({0}::float4[], {1}, {2}, {3})",
        SQLDataType.REAL, DSL.inline(weights.literal()), vector, query, DSL.inline(normalization));
  }

  /**
   * Returns whether {@code text} is like a run of words in {@code in}, above the threshold {@code
   * pg_trgm.word_similarity_threshold}: {@code <%}.
   */
  public static Condition wordSimilar(Field<String> text, Field<String> in) {
    return DSL.condition("{0} <% {1}", text, in);
  }
}
