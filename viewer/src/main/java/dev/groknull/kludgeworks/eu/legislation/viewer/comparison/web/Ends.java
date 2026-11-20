// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Inclusions;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Texts;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Requests;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The two ends of a comparison, from its canonical address:
 *
 * <ul>
 *   <li>the act, and its versions in the language
 *   <li>the versions that start on {@code from} and on {@code to}. From is not after To.
 *   <li>the act asked for, to limit the comparison to its changes. It is between the two versions.
 *   <li>the preparation of the act in the language, and the acts that each version includes ({@link
 *       Inclusions}): Cellar's lists, and the acts that the built texts mark first
 * </ul>
 *
 * <p>Anything else is 404. The compare page and the API find the canonical address for any dates.
 * Its parts, and the API's comparison, need not.
 */
final class Ends {
  final Language language;
  final Act act;
  final Timeline timeline;
  final Version from;
  final Version to;
  final @Nullable ActIdentifier only;
  final Preparation preparation;
  final Inclusions included;

  Ends(
      Act act,
      Language language,
      LocalDate from,
      LocalDate to,
      @Nullable String only,
      Texts texts) {
    this.language = language;
    this.act = act;
    timeline = act.timeline().in(language).orElseThrow(Requests::notFound);
    preparation = texts.preparation(act, language);
    included = act.inclusions(language, texts.marks(preparation));
    this.from = timeline.startingOn(from).orElseThrow(Requests::notFound);
    this.to = timeline.startingOn(to).orElseThrow(Requests::notFound);
    if (this.from.date().isAfter(this.to.date())) {
      throw Requests.notFound();
    }
    this.only =
        only == null
            ? null
            : included.between(this.from, this.to).stream()
                .filter(each -> each.celex().equals(only))
                .findFirst()
                .orElseThrow(Requests::notFound);
  }

  /**
   * Returns the first version in the language that includes {@code amending}. The version is after
   * From and not after To.
   */
  Optional<Version> step(ActIdentifier amending) {
    return timeline.versions().stream()
        .filter(v -> v.date().isAfter(from.date()) && !v.date().isAfter(to.date()))
        .filter(v -> included.includes(v, amending))
        .findFirst();
  }

  /**
   * Returns the version in the language before {@code version}. That is the text just before its
   * changes. If there is no version before it, returns {@code version}.
   */
  Version earlier(Version version) {
    return timeline.predecessor(version).orElse(version);
  }
}
