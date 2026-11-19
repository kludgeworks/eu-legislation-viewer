// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.jooq.postgres;

import org.jooq.Converter;
import org.jooq.DataType;
import org.jooq.impl.SQLDataType;

/**
 * A {@code tsquery}: the words that a {@link TsVector} must match, with their operators, as
 * Postgres writes them. For example, {@code 'aviat' & 'rule'}.
 *
 * @param text the value as Postgres writes it
 */
public record TsQuery(String text) {

  /** The jOOQ data type of a {@code tsquery} field. */
  public static final DataType<TsQuery> DATA_TYPE =
      SQLDataType.OTHER.asConvertedDataType(
          Converter.ofNullable(Object.class, TsQuery.class, TsQuery::of, TsQuery::text));

  /** Returns the value that the Postgres driver gives for a {@code tsquery}. */
  public static TsQuery of(Object value) {
    return new TsQuery(value.toString());
  }
}
