// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What the ▼ tags of a text in Read say and where they lead, one entry for each act that amended
 * the act up to the version read. The tags are in the text, which the content module renders
 * without knowing the act; js/tags.js looks each tag's act up here.
 *
 * <p>A tag's hover says what happened, which act did it and when that act applies: "Replaced by
 * 2026/449 · applies from 1 October 2026 · Article 2 of 2026/449". A click opens the history of the
 * provision, {@link ProvisionView}.
 *
 * @param tags one entry for each amending act, in the order the versions include them
 */
public record TagsView(List<Tag> tags) {

  public TagsView {
    tags = List.copyOf(tags);
  }

  /**
   * Returns the tags of the text of {@code reading}, a version of {@code act}, in {@code language}.
   */
  public static TagsView of(Act act, Version reading, Language language) {
    var versions =
        act.timeline()
            .in(language)
            .map(Timeline::versions)
            .filter(those -> those.contains(reading))
            .orElseGet(() -> act.timeline().versions());
    var seen = new LinkedHashSet<ActIdentifier>();
    for (var version : versions) {
      if (!version.date().isAfter(reading.date())) {
        seen.addAll(version.integrates());
      }
    }
    return new TagsView(
        seen.stream().map(each -> new Tag(each.celex(), applies(act, each))).toList());
  }

  /**
   * Returns when {@code amending} applies, with the clause that says so, from {@link Act#applying}.
   * For example, "applies from 1 April 2023 · Article 2 of 2023/566", or "applies in parts from 22
   * March 2026 · Article 2 of 2026/449". It is the act's date, not the occurrence's: an act that
   * applies in parts says when the first part starts. Returns null if Cellar has no day for it and
   * no clause that sets one.
   */
  static @Nullable String applies(Act act, ActIdentifier amending) {
    return act.applying(amending, Set.of())
        .map(
            applying -> {
              var clause = Wording.clause(applying.dates(), amending);
              var cited = clause.isEmpty() ? "" : " · " + clause;
              return switch (applying) {
                case Applying.From from -> "applies from " + Dates.inWords(from.day()) + cited;
                case Applying.InParts parts ->
                    "applies in parts from " + Dates.inWords(parts.first()) + cited;
                case Applying.InForce inForce ->
                    "in force from " + Dates.inWords(inForce.day()) + cited;
                case Applying.SetBy set ->
                    clause.isEmpty() ? null : "applies from a date set in " + clause;
              };
            })
        .orElse(null);
  }

  /**
   * What the tags of one amending act say and where they lead.
   *
   * @param act the CELEX of the amending act, as the tags name it
   * @param applies when the act applies, with the clause that says so. Null if not known.
   */
  public record Tag(String act, @Nullable String applies) {}
}
