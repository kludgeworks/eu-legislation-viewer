// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import org.jooq.Condition;
import org.jooq.Converter;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

/**
 * Postgres's full-text search and its trigram similarity ({@code pg_trgm}), as jOOQ fields and
 * conditions. jOOQ has none of these built in.
 *
 * <ul>
 *   <li>A filter is the operator itself, {@code @@} or {@code <%}, and never the function behind
 *       it: Postgres uses a GIN index only for the operator.
 *   <li>A text search configuration is written into the SQL, not bound as a value: Postgres uses an
 *       index on an expression such as {@code to_tsvector('english', title)} only if the
 *       configuration is a literal.
 * </ul>
 *
 * <p>The functions that the extensions install, such as {@code word_similarity} and {@code
 * unaccent}, are jOOQ's generated routines ({@code jooq.Routines}). This class has only what jOOQ
 * cannot generate: operators, and functions of {@code pg_catalog}.
 */
final class TextSearch {
  /** The configuration that the index of acts is built with. */
  static final String ENGLISH = "english";

  /** A {@code tsvector}: the words of a text, by position and weight. */
  record TsVector(String text) {}

  /** A {@code tsquery}: the words that a {@code tsvector} must match. */
  record TsQuery(String text) {}

  /**
   * The weights of the four classes of word in a rank, in Postgres's order D, C, B, A. For example,
   * an act's name is A and the rest of its title is C.
   */
  record Weights(float d, float c, float b, float a) {
    /**
     * Returns the weights as a literal of a {@code float4[]}, for example {@code
     * {0.1,0.2,0.4,1.0}}.
     */
    String literal() {
      return "{" + d + "," + c + "," + b + "," + a + "}";
    }
  }

  static final DataType<TsVector> TSVECTOR =
      SQLDataType.OTHER.asConvertedDataType(
          Converter.ofNullable(
              Object.class,
              TsVector.class,
              value -> new TsVector(value.toString()),
              TsVector::text));

  static final DataType<TsQuery> TSQUERY =
      SQLDataType.OTHER.asConvertedDataType(
          Converter.ofNullable(
              Object.class, TsQuery.class, value -> new TsQuery(value.toString()), TsQuery::text));

  private TextSearch() {}

  /**
   * Returns {@code column}, a {@code tsvector} column, as a {@link TsVector} field. jOOQ generates
   * such a column with no Java type. This adds no cast to the SQL.
   */
  static Field<TsVector> tsvector(Field<?> column) {
    return column.coerce(TSVECTOR);
  }

  /**
   * Returns the words of {@code text} as a query, in {@code configuration}: {@code
   * plainto_tsquery}.
   */
  static Field<TsQuery> plainToTsQuery(String configuration, Field<String> text) {
    return DSL.field(
        "plainto_tsquery({0}::regconfig, {1})", TSQUERY, DSL.inline(configuration), text);
  }

  /** Returns a query that matches what {@code one} or {@code other} matches: {@code ||}. */
  static Field<TsQuery> either(Field<TsQuery> one, Field<TsQuery> other) {
    return DSL.field("({0} || {1})", TSQUERY, one, other);
  }

  /** Returns whether {@code vector} matches {@code query}: {@code @@}. */
  static Condition matches(Field<TsVector> vector, Field<TsQuery> query) {
    return DSL.condition("{0} @@ {1}", vector, query);
  }

  /**
   * Returns how well {@code vector} matches {@code query}, by how close its words are: {@code
   * ts_rank_cd}.
   *
   * @param normalization how the rank takes the length of the text into account, as Postgres's bit
   *     mask. For example, 32 scales the rank to between 0 and 1.
   */
  static Field<Float> rankCd(
      Weights weights, Field<TsVector> vector, Field<TsQuery> query, int normalization) {
    return DSL.field(
        "ts_rank_cd({0}::float4[], {1}, {2}, {3})",
        SQLDataType.REAL, DSL.inline(weights.literal()), vector, query, DSL.inline(normalization));
  }

  /**
   * Returns whether {@code text} is like a run of words in {@code in}, above the threshold {@code
   * pg_trgm.word_similarity_threshold}: {@code <%}.
   */
  static Condition wordSimilar(Field<String> text, Field<String> in) {
    return DSL.condition("{0} <% {1}", text, in);
  }
}
