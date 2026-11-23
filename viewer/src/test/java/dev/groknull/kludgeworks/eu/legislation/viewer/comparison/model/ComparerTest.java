// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.MARCH;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.OCTOBER;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.articlesRenumbered;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.articlesWithPoint;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.block;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.cites;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.compared;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.find;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.footnote;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.occurrence;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.only;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.said;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.withFootnotes;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Tag;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.DifferenceBuilder;
import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Modification;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Comparer}: how it makes each occurrence of the two texts a difference, with its
 * place, its list and its tags. Who made a difference is in the rules (who-made-a-change.feature).
 * These tests cover what no example covers: the tags of the earlier text, the act of a deleted
 * point in a renumbered article, and the footnotes of an inserted occurrence.
 */
class ComparerTest {

  @Test
  void keepsTheListAPointIsAnItemOfWithoutItsItems() {
    var c = difference(compared(MARCH, OCTOBER), "anx/pnt_8.1.2.3/pnt_c", false);
    var list = Objects.requireNonNull(c.list());
    assertThat(list.name()).isEqualTo("list");
    assertThat(list.attribute("kind")).isNotBlank();
    assertThat(list.children()).isEmpty();
  }

  @Test
  void namesTheActsThatTagAnOccurrenceInEachText() {
    var c = difference(compared(MARCH, OCTOBER), "anx/pnt_8.1.2.3/pnt_c", false);
    assertThat(c.tags()).containsExactly(new Tag("32026R0449", Modification.REPLACED, null));
    // In March the point has no tag, and it is in no occurrence that an act put in whole. So it is
    // as the act was published.
    assertThat(c.earlierTags()).isEmpty();
  }

  @Test
  void resolvesAmendingActFromRenumberedAncestorForDeletedOccurrence() {
    // Another act renumbered the article than replaced it, so the point differs on its own.
    var deleted =
        difference(
            compared(articlesWithPoint(), articlesRenumbered("ActY")), "art_5/par_1/pnt_a", true);
    assertThat(deleted.kind()).isEqualTo(Kind.DELETED);
    assertThat(deleted.parent()).isEqualTo("art_6/par_1");
    assertThat(deleted.tags()).containsExactly(new Tag("ActX", Modification.REPLACED, "art_6"));
  }

  @Test
  void showsTheFootnotesOfAnInsertedOccurrenceInsertedWithIt() {
    var from = withFootnotes(List.of(occurrence("art_1", block("p", "Plain."))));
    var to =
        withFootnotes(
            List.of(
                occurrence("art_1", block("p", "Plain.")),
                occurrence("art_2", cites("New", "note_1"))),
            footnote("note_1", "OJ L 1, 1.1.2026, p. 1."));
    var inserted = difference(compared(from, to), "art_2", false);
    assertThat(inserted.kind()).isEqualTo(Kind.INSERTED);
    assertThat(said(only(Objects.requireNonNull(inserted.shown()), "footnotes")))
        .isEqualTo("[+OJ L 1, 1.1.2026, p. 1.]");
  }

  private static DifferenceBuilder difference(
      List<ComparedOccurrence> compared, String eId, boolean earlier) {
    return Objects.requireNonNull(find(compared, eId, earlier).difference(), eId);
  }
}
