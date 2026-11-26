// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.MARCH;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.OCTOBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Context;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.Objects;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Ordering}: what comes before each difference, and what the later text has after the
 * last one. The comparison is of the consolidations of 2015/1998 of 22 March and 1 October 2026.
 * That each occurrence of the later text is placed once is the rule each-occurrence-once
 * (what-the-page-shows.feature).
 */
class OrderingTest {
  private static final Ordering ORDERED = ordered(MARCH, OCTOBER);

  @Test
  void leadsADifferenceWithTheOpeningOfTheOccurrenceItIsIn() {
    var c = ORDERED.placed().getFirst();
    assertThat(c.parent()).isEqualTo("anx/pnt_8.1.2.3");
    assertThat(Objects.requireNonNull(c.lead()).children())
        .extracting(Wording::of)
        .containsExactly(
            "8.1.2.3",
            "The following means or method of screening, either individually or in combination,"
                + " shall be applied:");
  }

  @Test
  void foldsTheOccurrencesBetweenTwoDifferencesThatAreTheSame() {
    var differences = ORDERED.placed();
    assertThat(differences.get(0).context().getLast().occurrences())
        .extracting(Sibling::num)
        .containsExactly("(a)", "(b)");
    assertThat(differences.get(1).context())
        .singleElement()
        .satisfies(fold -> assertThat(fold.opening()).isNull())
        .extracting(Context::occurrences)
        .asInstanceOf(InstanceOfAssertFactories.list(Sibling.class))
        .extracting(Sibling::eId, Sibling::kind, Sibling::num)
        .containsExactly(tuple("anx/pnt_8.1.2.3/pnt_d", "point", "(d)"));
  }

  @Test
  void foldsTheOccurrencesAfterADifferenceToTheNextAndAfterTheLast() {
    // The folds hold the last point of 8.1.2.3, (g), then 8.1.2.4 up to 9.1.2.3. After the last
    // difference, 9.1.2.4 up to the end is folded.
    var between = ORDERED.placed().get(3).context();
    assertThat(between.getFirst().occurrences())
        .extracting(Sibling::eId)
        .first()
        .isEqualTo("anx/pnt_8.1.2.4");
    assertThat(between)
        .filteredOn(each -> each.opening() != null)
        .extracting(each -> each.occurrences().getFirst().eId())
        .containsExactly("anx/pnt_9.1.2.3");
    assertThat(ORDERED.closing().getFirst().occurrences())
        .extracting(Sibling::eId)
        .first()
        .isEqualTo("anx/pnt_9.1.2.4");
  }

  /** The differences of {@code later} from {@code earlier}, in order. */
  private static Ordering ordered(Element earlier, Element later) {
    var before = Occurrences.of(earlier);
    var after = Occurrences.of(later);
    return Ordering.of(
        new Comparer(before, after, Pairing.of(before, after)).occurrences(null, null));
  }
}
