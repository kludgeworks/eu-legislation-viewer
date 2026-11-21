// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Divergence;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextMarks;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The marks in the text on the Read page, where the text is not the law on the day asked. The
 * status lines of the Reading card say which acts. The marks show where.
 *
 * <ul>
 *   <li>A change that the text has and that does not apply on the day has an amber rule beside its
 *       occurrence, and a short line with the act: "2022/1190 · does not apply yet". The act is a
 *       link to its page.
 *   <li>A change that the text does not show has one grey marker at its place: "Not in this text:
 *       Article 18a · 2021/1133". If the text has the occurrence, the marker is before it. If not,
 *       the marker is at the end of the occurrence that holds the place, or of the occurrence
 *       before it.
 *   <li>The act itself, where it does not apply at all on the day, has no mark: the status line
 *       says so one time.
 * </ul>
 *
 * <p>The content module renders the text without the act. So the page sends the marks, and
 * js/margin.js puts them on the text when it arrives. It also gives the outline a dot on each part
 * that has a mark.
 *
 * @param marks the marks, in the order of the text
 */
public record MarginView(List<Mark> marks) {

  public MarginView {
    marks = List.copyOf(marks);
  }

  /** Returns the view with no marks. */
  public static MarginView none() {
    return new MarginView(List.of());
  }

  /**
   * Returns the marks of the text of {@code version} of {@code act}, for the day {@code asked}.
   *
   * @param marks the marks of the built texts in {@code language}, by version. Without the text of
   *     {@code version}, there are no marks.
   * @param today the date today, which gives the tense
   */
  public static MarginView of(
      Act act,
      Version version,
      LocalDate asked,
      Language language,
      Map<Version, TextMarks> marks,
      LocalDate today) {
    var text = marks.get(version);
    if (text == null) {
      return none();
    }
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var past = asked.isBefore(today);
    var kept = asked.equals(today) ? null : asked;
    var found = new ArrayList<Mark>();
    for (var divergence : act.divergences(asked, language, marks)) {
      if (divergence.whole(act.id())) {
        continue;
      }
      var cited = act.cited(divergence.act());
      var href = Links.amending(eli, divergence.act(), kept, language);
      var early = divergence.kind() == Divergence.Kind.EARLY;
      for (var occurrence : divergence.in(text.eIds())) {
        found.add(
            new Mark(
                occurrence.eId(),
                !early,
                occurrence.holds(),
                early
                    ? past ? "did not apply yet" : "does not apply yet"
                    : occurrence.places().stream()
                        .map(place -> place.location().name())
                        .distinct()
                        .reduce((one, other) -> one + ", " + other)
                        .orElse(""),
                cited,
                href));
      }
    }
    found.sort(Comparator.comparingInt(mark -> text.eIds().indexOf(mark.occurrence())));
    return new MarginView(found);
  }

  /**
   * One mark on an occurrence of the text.
   *
   * @param occurrence the eId of the occurrence
   * @param marker whether it is a marker for a change that the text does not show. If not, it is a
   *     rule for a change that does not apply on the day.
   * @param holds whether the text does not have the place, and the occurrence holds it or is before
   *     it. A marker is then at the end of the occurrence. A rule is then beside the heading of the
   *     occurrence.
   * @param words for a rule, the state of the change: "does not apply yet". For a marker, the
   *     provisions that the text does not show: "Article 18a, Article 50a".
   * @param act the act, as cited
   * @param href the address of the page of the act
   */
  public record Mark(
      String occurrence, boolean marker, boolean holds, String words, String act, String href) {}
}
