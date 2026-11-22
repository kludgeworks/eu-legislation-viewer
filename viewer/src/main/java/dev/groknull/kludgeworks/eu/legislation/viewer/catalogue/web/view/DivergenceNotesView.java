// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Divergence;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TextTags;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The divergence notes on the Read page: the divergence notes beside the text where it is not the
 * law on the day asked. The status lines of the Reading card say which acts. The divergence notes
 * show where.
 *
 * <ul>
 *   <li>A prospective amendment, a change that the text has and that does not apply on the day, has
 *       an amber bar beside its occurrence, and a short divergence note with the act: "2022/1190 ·
 *       does not apply yet". The act is a link to its page.
 *   <li>A pending amendment, a change that the text does not show, has one grey divergence note at
 *       its place: "Not in this text: Article 18a · 2021/1133". If the text has the occurrence, the
 *       divergence note is before it. If not, the divergence note is at the end of the occurrence
 *       that holds the place, or of the occurrence before it.
 *   <li>The act itself, while its application is deferred, has no divergence note: it is not a
 *       divergence. The status line says so one time.
 * </ul>
 *
 * <p>The content module renders the text without the act. So the page sends the divergence notes,
 * and js/divergence-notes.js puts them on the text when it arrives. It also gives the outline a dot
 * on each part that has a divergence note.
 *
 * @param divergenceNotes the divergence notes, in the order of the text
 */
public record DivergenceNotesView(List<DivergenceNote> divergenceNotes) {

  public DivergenceNotesView {
    divergenceNotes = List.copyOf(divergenceNotes);
  }

  /** Returns the view with no divergence notes. */
  public static DivergenceNotesView none() {
    return new DivergenceNotesView(List.of());
  }

  /**
   * Returns the divergence notes of the text of {@code version} of {@code act}, for the day {@code
   * asked}.
   *
   * @param tags the tags of the built texts in {@code language}, by version. Without the text of
   *     {@code version}, there are no divergence notes.
   * @param today the date today, which gives the tense
   */
  public static DivergenceNotesView of(
      Act act,
      Version version,
      LocalDate asked,
      Language language,
      Map<Version, TextTags> tags,
      LocalDate today) {
    var text = tags.get(version);
    if (text == null) {
      return none();
    }
    var eli = Objects.requireNonNull(act.eli(), () -> act.id() + " has no ELI to link by");
    var past = asked.isBefore(today);
    var kept = asked.equals(today) ? null : asked;
    var found = new ArrayList<DivergenceNote>();
    for (var divergence : act.divergences(asked, language, tags)) {
      var cited = act.cited(divergence.act());
      var href = Links.amending(eli, divergence.act(), kept, language);
      var prospective = divergence.kind() == Divergence.Kind.PROSPECTIVE;
      for (var occurrence : divergence.in(text.eIds())) {
        found.add(
            new DivergenceNote(
                occurrence.eId(),
                !prospective,
                occurrence.holds(),
                prospective
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
    found.sort(
        Comparator.comparingInt(
            divergenceNote -> text.eIds().indexOf(divergenceNote.occurrence())));
    return new DivergenceNotesView(found);
  }

  /**
   * One divergence note on an occurrence of the text.
   *
   * @param occurrence the eId of the occurrence
   * @param pending whether it is the divergence note of a pending amendment, a change that the text
   *     does not show. If not, it is the divergence note of a prospective amendment, a change that
   *     does not apply on the day, with a bar.
   * @param holds whether the text does not have the place, and the occurrence holds it or is before
   *     it. The divergence note of a pending amendment is then at the end of the occurrence. The
   *     bar of a prospective amendment is then beside the heading of the occurrence only.
   * @param words for a prospective amendment, the state of the change: "does not apply yet". For a
   *     pending amendment, the provisions that the text does not show: "Article 18a, Article 50a".
   * @param act the act, as cited
   * @param href the address of the page of the act
   */
  public record DivergenceNote(
      String occurrence, boolean pending, boolean holds, String words, String act, String href) {}
}
