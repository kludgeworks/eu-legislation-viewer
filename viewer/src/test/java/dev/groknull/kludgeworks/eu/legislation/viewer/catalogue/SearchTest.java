// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Search.Query;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchTest {
  @Test
  void takesTheWordsOfWhatAReaderTypedInLowerCase() {
    assertThat(Query.of("  Aviation-security, ‘air cargo’ 2015/1998 ", Kind.ALL, 0, 20))
        .map(Query::words)
        .contains(List.of("aviation", "security", "air", "cargo", "2015", "1998"));
  }

  @Test
  void takesTheFirstTenWordsOnly() {
    var words = String.join(" ", Collections.nCopies(12, "security"));

    assertThat(Query.of(words, Kind.ALL, 0, 20).orElseThrow().words()).hasSize(Query.MOST_WORDS);
  }

  @Test
  void isNoSearchWithoutWords() {
    assertThat(Query.of(" – / ", Kind.ALL, 0, 20)).isEmpty();
  }

  @Test
  void hasAKindByItsCode() {
    assertThat(Kind.of("amending")).contains(Kind.AMENDING);
    assertThat(Kind.of("treaty")).isEmpty();
  }
}
