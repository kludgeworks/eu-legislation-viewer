// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** SQL resources used to build and query the catalogue search index. */
final class SearchSql {
  private SearchSql() {}

  static String read(String name) {
    try (var input = Objects.requireNonNull(SearchSql.class.getResourceAsStream(name), name)) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new UncheckedIOException(name, failure);
    }
  }
}
