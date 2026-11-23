// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model;

import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.block;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.cites;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.footnote;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.numbered;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.occurrence;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.only;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.text;
import static dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.BuiltTexts.withFootnotes;
import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.model.Occurrences.Occurrence;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link OccurrenceEdits}: whether the own blocks and footnotes of an occurrence differ from
 * those of the occurrence that it was. The rules are full-stop-after-a-number-is-no-change and
 * footnotes-belong-to-their-occurrence (what-counts-as-a-difference.feature). These tests cover
 * what their examples do not: a num that gained its full stop against one that changed, footnotes
 * at the end of an annex, and the footnote and the reference to it that only one text has.
 */
class OccurrenceEditsTest {

  @Test
  void findsNoDifferenceInANumThatOnlyGainedAFullStop() {
    var earlier = text(numbered("11.3", "CERTIFICATION"));

    assertThat(edits(earlier, text(numbered("11.3.", "CERTIFICATION")), "11.3")).isEmpty();
    assertThat(edits(earlier, text(numbered("11.4", "CERTIFICATION")), "11.3")).isPresent();
  }

  @Test
  void findsTheFootnotesOfAnActAsPublishedAtTheEndOfItsAnnex() {
    // As 2015/1998 as published: the footnote of point 1.0.5 is at the end of the annex. Its
    // consolidations have it at the end of the text, with another eId. Its words are the same.
    var annex =
        new Element(
            "occurrence",
            Map.of("eId", "anx", "kind", "annex"),
            List.of(
                occurrence("anx/pnt_1.0.5", cites("References", "note_anx01000301-2")),
                new Element(
                    "footnotes",
                    Map.of(),
                    List.of(footnote("note_anx01000301-2", "Decision C(2015) 8005 final.")),
                    "")),
            "");
    var from = withFootnotes(List.of(annex));
    var to =
        withFootnotes(
            List.of(
                new Element(
                    "occurrence",
                    Map.of("eId", "anx", "kind", "annex"),
                    List.of(occurrence("anx/pnt_1.0.5", cites("References", "note_2"))),
                    "")),
            footnote("note_2", "Decision C(2015) 8005 final."));

    assertThat(edits(from, to, "anx/pnt_1.0.5")).isEmpty();
  }

  // The marks of a footnote that only one text has are on the occurrence as its difference shows
  // it, so these go through the whole comparison.
  @Test
  void saysThatAFootnoteAndTheReferenceToItAreOnlyInTheLaterText() {
    // As Article 6(2) of 767/2008: 2024/1356 changed the words and added a footnote to them.
    var from = withFootnotes(List.of(occurrence("art_1", block("p", "The authorities."))));
    var to =
        withFootnotes(
            List.of(occurrence("art_1", cites("The screening authorities", "note_1"))),
            footnote("note_1", "OJ L, 2024/1356, 22.5.2024."));
    var shown = Differ.differences(from, to).differences().getFirst().shown();
    assertThat(only(only(shown, "footnotes"), "footnote").attribute("diff")).isEqualTo("ins");
    assertThat(only(only(shown, "p"), "footnoteRef").attribute("diff")).isEqualTo("ins");
  }

  @Test
  void saysThatAFootnoteIsOnlyInTheEarlierTextAndLeavesTheFootnotesOfBothTextsAsTheyAre() {
    var from =
        withFootnotes(
            List.of(occurrence("art_1", cites("Silesia", "note_1"))),
            footnote("note_1", "only in Poland"));
    var to = withFootnotes(List.of(occurrence("art_1", block("p", "Silesia and Pomerania"))));
    var deleted = Differ.differences(from, to).differences().getFirst().shown();
    assertThat(only(only(deleted, "footnotes"), "footnote").attribute("diff")).isEqualTo("del");

    var changed =
        withFootnotes(
            List.of(occurrence("art_1", cites("Silesia", "note_1"))),
            footnote("note_1", "Only in Poland."));
    var kept = Differ.differences(from, changed).differences().getFirst().shown();
    assertThat(only(only(kept, "footnotes"), "footnote").attribute("diff")).isNull();
    assertThat(only(only(kept, "p"), "footnoteRef").attribute("diff")).isNull();
  }

  /** How the occurrence {@code eId} of {@code later} differs from that of {@code earlier}. */
  private static Optional<OccurrenceEdits> edits(Element earlier, Element later, String eId) {
    return Optional.ofNullable(OccurrenceEdits.of(at(earlier, eId), at(later, eId)));
  }

  private static Occurrence at(Element text, String eId) {
    return Objects.requireNonNull(Occurrences.of(text).get(eId), eId);
  }
}
