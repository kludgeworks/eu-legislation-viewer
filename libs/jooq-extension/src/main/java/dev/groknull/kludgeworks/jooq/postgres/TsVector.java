// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.jooq.postgres;

import org.jooq.Converter;
import org.jooq.DataType;
import org.jooq.impl.SQLDataType;

/**
 * A {@code tsvector}: the words of a text, each with its positions and its weight, as Postgres
 * writes them. For example, {@code 'aviat':1A 'rule':2B}.
 *
 * <p>The viewer reads such values, and never binds one: a {@code tsvector} is made in SQL, with
 * {@code to_tsvector}. Binding a value as text would need a cast to {@code tsvector}.
 *
 * @param text the value as Postgres writes it
 */
public record TsVector(String text) {

  /** The jOOQ data type of a {@code tsvector} field. */
  public static final DataType<TsVector> DATA_TYPE =
      SQLDataType.OTHER.asConvertedDataType(
          Converter.ofNullable(Object.class, TsVector.class, TsVector::of, TsVector::text));

  /** Returns the value that the Postgres driver gives for a {@code tsvector}. */
  public static TsVector of(Object value) {
    return new TsVector(value.toString());
  }
}
